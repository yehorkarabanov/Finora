// Generates a large, realistic Finora backup database for UI testing.
//
//   node scripts/generate-seed-db.mjs [outPath] [years]
//
// Defaults: scripts/out/Finora-backup-large.db, 3 years of history. The file matches Room schema
// version 3 (tables from app/schemas/.../3.json, user_version, room_master_table identity hash), so
// it can be restored through Settings > Data > Import. Deterministic: same output every run.
import { DatabaseSync } from 'node:sqlite';
import { readFileSync, mkdirSync, rmSync, existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const outPath = resolve(process.argv[2] ?? `${root}/scripts/out/Finora-backup-large.db`);
const YEARS = Number(process.argv[3] ?? 3);
const SCHEMA = JSON.parse(readFileSync(`${root}/app/schemas/com.yehor.finora.data.db.FinoraDatabase/3.json`, 'utf8')).database;

// ---- deterministic PRNG -------------------------------------------------------------------
let seed = 20261112;
const rnd = () => { seed = (seed + 0x6D2B79F5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t ^= t + Math.imul(t ^ (t >>> 7), 61 | t); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
const int = (a, b) => a + Math.floor(rnd() * (b - a + 1));
const pick = (arr) => arr[Math.floor(rnd() * arr.length)];
const chance = (p) => rnd() < p;
// log-normal-ish amount in major units, centred on `mid`
const amount = (mid, spread = 0.7) => Math.max(1, mid * Math.exp((rnd() + rnd() + rnd() - 1.5) * spread * 2));

// ---- create file --------------------------------------------------------------------------
mkdirSync(dirname(outPath), { recursive: true });
for (const f of [outPath, `${outPath}-wal`, `${outPath}-shm`]) if (existsSync(f)) rmSync(f);
const db = new DatabaseSync(outPath);
db.exec('PRAGMA journal_mode = DELETE; PRAGMA foreign_keys = ON;');
for (const e of SCHEMA.entities) {
  db.exec(e.createSql.replaceAll('${TABLE_NAME}', e.tableName));
  for (const i of e.indices ?? []) db.exec(i.createSql.replaceAll('${TABLE_NAME}', e.tableName));
}
for (const q of SCHEMA.setupQueries) db.exec(q);
db.exec(`PRAGMA user_version = ${SCHEMA.version}`);

const run = (sql, ...p) => db.prepare(sql).run(...p).lastInsertRowid;
const NOW = Date.now();
db.exec('BEGIN');

// ---- currencies (parsed from CurrencyRegistry.kt) ------------------------------------------
const reg = readFileSync(`${root}/app/src/main/java/com/yehor/finora/domain/money/CurrencyRegistry.kt`, 'utf8');
const currencies = [...reg.matchAll(/Entry\("([A-Z]{3})", (\d), "([^"]*)", "([^"]*)"\)/g)].map((m) => ({ code: m[1], scale: +m[2], symbol: m[3], name: m[4] }));
if (currencies.length < 100) throw new Error('currency parse failed');
for (const c of currencies) run('INSERT INTO currency VALUES (?,?,?,?)', c.code, c.scale, c.symbol, c.name);
const scaleOf = Object.fromEntries(currencies.map((c) => [c.code, c.scale]));

// ---- categories (mirror of DefaultCategories.kt) -------------------------------------------
const C = { RED: 0xFFDC2626, ORANGE: 0xFFEA6A0C, AMBER: 0xFFD97706, GREEN: 0xFF16A34A, TEAL: 0xFF0D9488, BLUE: 0xFF2563EB, INDIGO: 0xFF4F46E5, PURPLE: 0xFF9C3FE0, PINK: 0xFFDB2777, BROWN: 0xFF8D5B3A, GREY: 0xFF71717A };
const defaults = {
  EXPENSE: [['Food', 'restaurant', C.ORANGE, [['Groceries', 'groceries', C.ORANGE], ['Restaurants & Cafes', 'coffee', C.ORANGE]]], ['Housing', 'home', C.BROWN], ['Transport', 'car', C.BLUE], ['Shopping', 'shopping', C.PINK], ['Entertainment', 'entertainment', C.PURPLE], ['Health', 'health', C.RED], ['Subscriptions', 'subscriptions', C.INDIGO], ['Fees & Charges', 'fees', C.GREY], ['Other', 'other', C.GREY]],
  INCOME: [['Salary', 'salary', C.GREEN], ['Freelance', 'freelance', C.TEAL], ['Investment Income', 'dividends', C.GREEN], ['Gifts', 'gift', C.PINK], ['Refunds', 'refund', C.AMBER], ['Other', 'other', C.GREY]],
  TRANSFER: [['Own Account Transfer', 'transfer', C.BLUE], ['Currency Exchange', 'exchange', C.TEAL], ['Savings Transfer', 'savings', C.GREEN], ['Investment Deposit', 'investments', C.INDIGO], ['Other', 'other', C.GREY]],
};
const cat = {}; // "TYPE:Name" -> id
for (const [type, list] of Object.entries(defaults)) {
  for (const [name, icon, color, kids = []] of list) {
    const pid = run('INSERT INTO category (name,icon,color,type,parent_id,created_at) VALUES (?,?,?,?,NULL,?)', name, icon, color, type, NOW);
    cat[`${type}:${name}`] = Number(pid);
    for (const [kn, ki, kc] of kids) cat[`${type}:${kn}`] = Number(run('INSERT INTO category (name,icon,color,type,parent_id,created_at) VALUES (?,?,?,?,?,?)', kn, ki, kc, type, pid, NOW));
  }
}
// a few extra user categories, incl. a 3-level chain (max depth allowed)
const extra = (name, icon, color, type, parent) => (cat[`${type}:${name}`] = Number(run('INSERT INTO category (name,icon,color,type,parent_id,created_at) VALUES (?,?,?,?,?,?)', name, icon, color, type, parent ?? null, NOW)));
extra('Fuel', 'car', C.BLUE, 'EXPENSE', cat['EXPENSE:Transport']);
extra('Public transport', 'car', C.BLUE, 'EXPENSE', cat['EXPENSE:Transport']);
extra('Clothes', 'shopping', C.PINK, 'EXPENSE', cat['EXPENSE:Shopping']);
extra('Electronics', 'shopping', C.PINK, 'EXPENSE', cat['EXPENSE:Shopping']);
extra('Pharmacy', 'health', C.RED, 'EXPENSE', cat['EXPENSE:Health']);
extra('Rent', 'home', C.BROWN, 'EXPENSE', cat['EXPENSE:Housing']);
extra('Utilities', 'home', C.BROWN, 'EXPENSE', cat['EXPENSE:Housing']);
extra('Coffee beans', 'coffee', C.ORANGE, 'EXPENSE', cat['EXPENSE:Restaurants & Cafes']);
extra('Pets', 'other', C.AMBER, 'EXPENSE', null);

// ---- accounts -----------------------------------------------------------------------------
const HISTORY_START = (() => { const d = new Date(NOW); d.setFullYear(d.getFullYear() - YEARS); d.setDate(1); d.setHours(9, 0, 0, 0); return d.getTime(); })();
const acctDefs = [
  // name, ccy, type, icon, color, opening (major units), status
  ['Main card', 'USD', 'BANK', 'bank', C.BLUE, 4200, 'ACTIVE'],
  ['Cash wallet', 'USD', 'CASH', 'cash', C.GREEN, 350, 'ACTIVE'],
  ['Savings', 'USD', 'SAVINGS', 'savings', C.TEAL, 18000, 'ACTIVE'],
  ['Euro account', 'EUR', 'BANK', 'bank', C.INDIGO, 2600, 'ACTIVE'],
  ['Travel GBP', 'GBP', 'CASH', 'cash', C.PINK, 400, 'ACTIVE'],
  ['Broker', 'USD', 'INVESTMENT', 'investments', C.PURPLE, 9500, 'ACTIVE'],
  ['Zloty card', 'PLN', 'BANK', 'bank', C.ORANGE, 3000, 'ACTIVE'],
  ['Yen stash', 'JPY', 'CASH', 'cash', C.RED, 60000, 'ACTIVE'],
  ['Old credit card', 'USD', 'OTHER', 'bank', C.GREY, 150, 'ARCHIVED'],
];
const minor = (ccy, major) => Math.round(major * 10 ** scaleOf[ccy]);
const accounts = acctDefs.map(([name, ccy, type, icon, color, opening, status]) => {
  const id = Number(run('INSERT INTO account (name,currency_code,type,icon,color,description,status,created_at) VALUES (?,?,?,?,?,?,?,?)', name, ccy, type, icon, color, chance(0.3) ? `${name} - test data` : null, status, HISTORY_START));
  const op = run("INSERT INTO operation (type,occurred_at,category_id,description,opening_account_id,created_at,updated_at) VALUES ('OPENING_BALANCE',?,NULL,NULL,?,?,?)", HISTORY_START, id, HISTORY_START, HISTORY_START);
  run('INSERT INTO ledger_entry (operation_id,account_id,currency_code,amount_minor,occurred_at) VALUES (?,?,?,?,?)', op, id, ccy, minor(ccy, opening), HISTORY_START);
  return { id, name, ccy, status };
});
const byName = Object.fromEntries(accounts.map((a) => [a.name, a]));

// ---- tags ---------------------------------------------------------------------------------
const tagNames = ['vacation', 'work', 'family', 'gift', 'tax', 'online', 'impulse', 'health', 'car', 'kids', 'birthday', 'reimbursable'];
const tagColors = [C.RED, C.ORANGE, C.AMBER, C.GREEN, C.TEAL, C.BLUE, C.INDIGO, C.PURPLE, C.PINK, null];
const tags = tagNames.map((n, i) => Number(run('INSERT INTO tag (name,color,created_at) VALUES (?,?,?)', n, tagColors[i % tagColors.length], NOW)));

// ---- operations ---------------------------------------------------------------------------
const insOp = db.prepare('INSERT INTO operation (type,occurred_at,category_id,description,opening_account_id,created_at,updated_at) VALUES (?,?,?,?,NULL,?,?)');
const insEntry = db.prepare('INSERT INTO ledger_entry (operation_id,account_id,currency_code,amount_minor,occurred_at) VALUES (?,?,?,?,?)');
const insOpTag = db.prepare('INSERT OR IGNORE INTO operation_tag VALUES (?,?)');
let opCount = 0;
function addOp(type, at, categoryKey, desc, legs, tagChance = 0.18) {
  const id = Number(insOp.run(type, at, categoryKey ? cat[categoryKey] : null, desc, at, at).lastInsertRowid);
  for (const [acct, amt] of legs) insEntry.run(id, acct.id, acct.ccy, minor(acct.ccy, amt), at);
  if (chance(tagChance)) for (let i = int(1, 3); i > 0; i--) insOpTag.run(id, pick(tags));
  opCount++;
  return id;
}
const stamp = (day, h = int(7, 22), m = int(0, 59)) => { const d = new Date(day); d.setHours(h, m, int(0, 59), 0); return d.getTime(); };

const expenseSpec = [
  // [categoryName, weight, mid amount USD, [descriptions]]
  ['Groceries', 22, 38, ['Supermarket', 'Farmers market', 'Corner shop', 'Bakery', 'Costco run', 'Whole Foods']],
  ['Restaurants & Cafes', 14, 24, ['Lunch', 'Dinner out', 'Pizza', 'Sushi', 'Brunch', 'Burger place', 'Takeaway']],
  ['Coffee beans', 4, 16, ['Beans', 'Roastery']],
  ['Fuel', 6, 55, ['Gas station', 'Fuel']],
  ['Public transport', 8, 6, ['Metro', 'Bus ticket', 'Monthly pass top-up', 'Tram']],
  ['Transport', 4, 22, ['Taxi', 'Ride share', 'Parking']],
  ['Clothes', 4, 70, ['Sneakers', 'Jacket', 'T-shirts', 'Jeans']],
  ['Electronics', 1.5, 160, ['Headphones', 'Cable & charger', 'Keyboard', 'Phone case']],
  ['Shopping', 5, 30, ['Amazon order', 'Home goods', 'Gift shop', 'Bookstore']],
  ['Entertainment', 6, 20, ['Cinema', 'Concert', 'Board games', 'Bowling', 'Museum']],
  ['Pharmacy', 3, 18, ['Pharmacy', 'Vitamins']],
  ['Health', 1.5, 90, ['Dentist', 'Doctor visit', 'Physio']],
  ['Utilities', 0, 0, []], // handled by recurring
  ['Pets', 2, 28, ['Pet food', 'Vet', 'Toys']],
  ['Fees & Charges', 1.5, 5, ['Bank fee', 'ATM fee', 'Card replacement']],
  ['Other', 3, 25, ['Misc', 'Something random', 'Laundry', 'Haircut']],
].filter((s) => s[1] > 0);
const totalW = expenseSpec.reduce((a, s) => a + s[1], 0);
const pickSpec = () => { let r = rnd() * totalW; for (const s of expenseSpec) { if ((r -= s[1]) < 0) return s; } return expenseSpec[0]; };
// expense accounts and their share + currency adjustment to the typical USD amount
const spendAccts = [[byName['Main card'], 55, 1], [byName['Cash wallet'], 20, 1], [byName['Euro account'], 10, 0.92], [byName['Zloty card'], 8, 4], [byName['Travel GBP'], 4, 0.8], [byName['Yen stash'], 3, 150]];
const spendW = spendAccts.reduce((a, s) => a + s[1], 0);
const pickSpend = () => { let r = rnd() * spendW; for (const s of spendAccts) { if ((r -= s[1]) < 0) return s; } return spendAccts[0]; };
const rounded = (ccy, v) => (scaleOf[ccy] === 0 ? Math.round(v) : Math.round(v * 100) / 100);

const dayMs = 86400000;
const firstDay = new Date(HISTORY_START).setHours(0, 0, 0, 0) + dayMs; // day after opening balances
const lastDay = new Date(NOW).setHours(0, 0, 0, 0);
for (let day = firstDay; day <= lastDay; day += dayMs) {
  const d = new Date(day);
  const dom = d.getDate();
  const weekend = d.getDay() === 0 || d.getDay() === 6;
  const todayCap = day === lastDay ? NOW - 60000 : Infinity;
  const clampAt = (t) => Math.min(t, todayCap);

  // daily expenses: 0-4, heavier on weekends
  for (let n = int(1, weekend ? 9 : 6); n > 0; n--) {
    const [name, , mid, descs] = pickSpec();
    const [acct, , factor] = pickSpend();
    const amt = rounded(acct.ccy, amount(mid * factor));
    addOp('EXPENSE', clampAt(stamp(day)), `EXPENSE:${name}`, chance(0.8) ? pick(descs) : null, [[acct, -amt]]);
  }
  // salary on the 1st (USD) and a smaller EUR freelance payment mid month
  if (dom === 15 && chance(0.7)) addOp('INCOME', clampAt(stamp(day, 14)), 'INCOME:Freelance', pick(['Logo project', 'Consulting', 'Website fix', 'Code review']), [[byName['Euro account'], rounded('EUR', amount(650, 0.5))]], 0.4);
  if (chance(0.03)) addOp('INCOME', clampAt(stamp(day)), `INCOME:${pick(['Gifts', 'Refunds', 'Other'])}`, pick(['Birthday money', 'Returned jacket', 'Sold old bike', 'Cashback']), [[pick([byName['Main card'], byName['Cash wallet']]), rounded('USD', amount(60, 0.8))]]);
  if (dom === 28 && chance(0.6)) addOp('INCOME', clampAt(stamp(day, 12)), 'INCOME:Investment Income', 'Dividends', [[byName['Broker'], rounded('USD', amount(90, 0.6))]], 0);

  // monthly bills (rent, salary and streaming come from the recurring schedules below)
  if (dom === 3) {
    addOp('EXPENSE', clampAt(stamp(day, 11)), 'EXPENSE:Utilities', 'Electricity & water', [[byName['Main card'], -rounded('USD', amount(95, 0.4))]], 0);
  }
  if (dom === 7) {
    addOp('EXPENSE', clampAt(stamp(day, 8)), 'EXPENSE:Subscriptions', 'Music', [[byName['Main card'], -10.99]], 0);
    addOp('EXPENSE', clampAt(stamp(day, 9)), 'EXPENSE:Subscriptions', 'Cloud storage', [[byName['Euro account'], -2.99]], 0);
  }
  // transfers
  if (dom === 2) addOp('TRANSFER', clampAt(stamp(day, 10, 30)), 'TRANSFER:Savings Transfer', 'Monthly savings', [[byName['Main card'], -800], [byName['Savings'], 800]], 0);
  if (dom === 5 && chance(0.5)) addOp('TRANSFER', clampAt(stamp(day, 13)), 'TRANSFER:Investment Deposit', null, [[byName['Main card'], -300], [byName['Broker'], 300]], 0);
  if (chance(0.04)) { const amt = pick([100, 150, 200, 300]); addOp('TRANSFER', clampAt(stamp(day)), 'TRANSFER:Own Account Transfer', 'ATM withdrawal', [[byName['Main card'], -amt], [byName['Cash wallet'], amt]], 0); }
  if (chance(0.015)) { // cross-currency exchange, rate ~0.92 EUR/USD
    const usd = pick([200, 300, 500]); addOp('TRANSFER', clampAt(stamp(day)), 'TRANSFER:Currency Exchange', 'USD to EUR', [[byName['Main card'], -usd], [byName['Euro account'], rounded('EUR', usd * 0.92)]], 0);
  }
  if (chance(0.006)) { const usd = pick([150, 250]); addOp('TRANSFER', clampAt(stamp(day)), 'TRANSFER:Currency Exchange', 'Trip money (PLN)', [[byName['Main card'], -usd], [byName['Zloty card'], rounded('PLN', usd * 4.0)]], 0); }
}

// keep the cash wallet from drifting hugely negative: top up in a final fixup if needed
// (balances are not constrained by the app; this just keeps the UI sane)
for (const a of accounts) {
  const bal = Number(db.prepare('SELECT COALESCE(SUM(amount_minor),0) s FROM ledger_entry WHERE account_id=?').get(a.id).s);
  if (bal < 0 && a.status === 'ACTIVE') {
    const topUp = Math.ceil(-bal / 10 ** scaleOf[a.ccy]) + minor(a.ccy, 200) / 10 ** scaleOf[a.ccy];
    addOp('INCOME', lastDay - dayMs * 20, 'INCOME:Other', 'Balance top-up', [[a, topUp]], 0);
  }
}

// ---- recurring schedules + generated occurrences ------------------------------------------
const toDay = (ms) => Math.floor(ms / dayMs);
const today = toDay(lastDay);
const sched = [
  // name, kind, acct, amount(major), category, unit, interval, status, tag
  ['Rent', 'EXPENSE', 'Main card', 1450, 'EXPENSE:Rent', 'MONTH', 1, 'ACTIVE'],
  ['Streaming', 'EXPENSE', 'Main card', 15.99, 'EXPENSE:Subscriptions', 'MONTH', 1, 'ACTIVE'],
  ['Gym', 'EXPENSE', 'Main card', 39, 'EXPENSE:Health', 'MONTH', 1, 'PAUSED'],
  ['Salary', 'INCOME', 'Main card', 5200, 'INCOME:Salary', 'MONTH', 1, 'ACTIVE'],
  ['Insurance', 'EXPENSE', 'Main card', 480, 'EXPENSE:Other', 'YEAR', 1, 'ACTIVE'],
  ['Weekly groceries', 'EXPENSE', 'Cash wallet', 60, 'EXPENSE:Groceries', 'WEEK', 1, 'ACTIVE'],
  ['Cleaner', 'EXPENSE', 'Main card', 70, 'EXPENSE:Other', 'WEEK', 2, 'ACTIVE'],
  ['Domain renewal', 'EXPENSE', 'Euro account', 14, 'EXPENSE:Subscriptions', 'YEAR', 1, 'ACTIVE'],
];
const startMs = HISTORY_START + 40 * dayMs;
for (const [name, kind, an, amt, ck, unit, n, status] of sched) {
  const a = byName[an];
  const start = toDay(startMs);
  const stepDays = { DAY: 1, WEEK: 7, MONTH: 30, YEAR: 365 }[unit] * n;
  // walk occurrences up to today (approximate calendar maths is fine for seed data)
  const dates = [];
  for (let d = start; d <= today; d += stepDays) dates.push(d);
  const next = dates.length ? dates[dates.length - 1] + stepDays : start;
  const sid = Number(run('INSERT INTO recurring_schedule (name,kind,account_id,amount_minor,category_id,unit,interval_count,start_date,end_date,next_date,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,NULL,?,?,?,?)', name, kind, a.id, minor(a.ccy, amt), cat[ck], unit, n, start, next, status, startMs, NOW));
  if (chance(0.5)) run('INSERT INTO recurring_schedule_tag VALUES (?,?)', sid, pick(tags));
  dates.forEach((d, i) => {
    const at = d * dayMs + 9 * 3600000;
    if (at > NOW) return;
    if (i % 9 === 8) { run("INSERT INTO recurring_occurrence (schedule_id,occurrence_date,status,operation_id,handled_at) VALUES (?,?,'SKIPPED',NULL,?)", sid, d, at); return; }
    const opId = Number(insOp.run(kind, at, cat[ck], name, at, at).lastInsertRowid);
    insEntry.run(opId, a.id, a.ccy, minor(a.ccy, kind === 'EXPENSE' ? -amt : amt), at);
    opCount++;
    run("INSERT INTO recurring_occurrence (schedule_id,occurrence_date,status,operation_id,handled_at) VALUES (?,?,'GENERATED',?,?)", sid, d, opId, at);
  });
}

// ---- exchange rates + settings ------------------------------------------------------------
const iso = new Date(NOW).toISOString().slice(0, 10);
const rates = { EUR: '0.92', GBP: '0.79', PLN: '4.00', JPY: '151.5' };
for (const [q, r] of Object.entries(rates)) {
  run('INSERT INTO exchange_rate VALUES (?,?,?,?,?,?)', 'USD', q, r, 'seed', iso, NOW);
  run('INSERT INTO exchange_rate VALUES (?,?,?,?,?,?)', q, 'USD', (1 / Number(r)).toFixed(6).replace(/0+$/, ''), 'seed', iso, NOW);
}
run("INSERT INTO app_setting VALUES ('display_currency','USD',?)", NOW);

db.exec('COMMIT');

// ---- verify -------------------------------------------------------------------------------
const fk = db.prepare('PRAGMA foreign_key_check').all();
const qc = db.prepare('PRAGMA quick_check').get();
const bad = db.prepare(`
  SELECT 'type/category mismatch' w FROM operation o JOIN category c ON c.id=o.category_id WHERE c.type<>o.type
  UNION ALL SELECT 'occurred_at mismatch' FROM ledger_entry e JOIN operation o ON o.id=e.operation_id WHERE e.occurred_at<>o.occurred_at
  UNION ALL SELECT 'future op' FROM operation WHERE occurred_at > ${NOW}
  UNION ALL SELECT 'opening after entry' FROM operation o WHERE o.type='OPENING_BALANCE' AND EXISTS (SELECT 1 FROM ledger_entry e WHERE e.account_id=o.opening_account_id AND e.operation_id<>o.id AND e.occurred_at<o.occurred_at)
  UNION ALL SELECT 'transfer shape' FROM operation o LEFT JOIN ledger_entry e ON e.operation_id=o.id GROUP BY o.id HAVING o.type='TRANSFER' AND (COUNT(e.id)<>2 OR MIN(e.amount_minor)>=0 OR MAX(e.amount_minor)<=0)
  UNION ALL SELECT 'income not positive' FROM operation o JOIN ledger_entry e ON e.operation_id=o.id GROUP BY o.id HAVING o.type='INCOME' AND MIN(e.amount_minor)<=0
  UNION ALL SELECT 'expense not negative' FROM operation o JOIN ledger_entry e ON e.operation_id=o.id GROUP BY o.id HAVING o.type='EXPENSE' AND MAX(e.amount_minor)>=0
`).all();
if (fk.length || qc.quick_check !== 'ok' || bad.length) { console.error({ fk, qc, bad: bad.slice(0, 5) }); process.exit(1); }
db.exec('VACUUM');
const n = (t) => Number(db.prepare(`SELECT COUNT(*) c FROM ${t}`).get().c);
console.log(`Wrote ${outPath}`);
console.log(`  accounts ${n('account')}, categories ${n('category')}, tags ${n('tag')}, operations ${n('operation')}, ledger entries ${n('ledger_entry')}, schedules ${n('recurring_schedule')}, occurrences ${n('recurring_occurrence')}`);
db.close();
