package com.yehor.finora.ui.accounts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.input.FilterPill
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.component.list.ListRow
import com.yehor.finora.designsystem.component.list.SectionHeader
import com.yehor.finora.designsystem.component.sheet.FinoraSheet
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.icon.IconKeys
import com.yehor.finora.designsystem.theme.PreviewTheme
import com.yehor.finora.domain.account.AccountDraftError
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.account.OpeningDatePolicy
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.ColorPicker
import com.yehor.finora.ui.common.FieldNote
import com.yehor.finora.ui.common.FormCtaBar
import com.yehor.finora.ui.common.FormScreen
import com.yehor.finora.ui.common.FormTextRow
import com.yehor.finora.ui.common.IconBadge
import com.yehor.finora.ui.common.IconPicker
import com.yehor.finora.ui.common.SingleDateDialog
import com.yehor.finora.ui.common.errorOutline
import com.yehor.finora.ui.operations.FilterSearchField
import com.yehor.finora.ui.operations.ShelfDivider
import com.yehor.finora.ui.operations.ShelfIcon
import com.yehor.finora.ui.operations.ShelfRow
import com.yehor.finora.ui.operations.ShelfValue
import com.yehor.finora.ui.operations.SmallPill
import java.time.ZoneOffset

@Composable
fun AccountEditScreen(
    onBack: () -> Unit,
    viewModel: AccountEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    val policy = remember { viewModel.policy() }
    val now = remember { System.currentTimeMillis() }

    AccountEditContent(
        state = state,
        policy = policy,
        now = now,
        onBack = onBack,
        onName = viewModel::onName,
        onType = viewModel::onType,
        onCurrency = viewModel::onCurrency,
        onIcon = viewModel::onIcon,
        onColor = viewModel::onColor,
        onDescription = viewModel::onDescription,
        onAmount = viewModel::onAmount,
        onDate = viewModel::onDate,
        onSave = viewModel::save,
    )
}

@Composable
internal fun AccountEditContent(
    state: AccountFormState,
    policy: OpeningDatePolicy,
    now: Long,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onType: (AccountType) -> Unit,
    onCurrency: (Currency) -> Unit,
    onIcon: (String) -> Unit,
    onColor: (Long) -> Unit,
    onDescription: (String) -> Unit,
    onAmount: (String) -> Unit,
    onDate: (Long) -> Unit,
    onSave: () -> Unit,
) {
    val errors = AccountFormLogic.errors(state)
    val blocker = AccountFormLogic.blocker(state, policy, now)
    val dateTooLate = AccountFormLogic.dateTooLate(state, policy, now)
    val openingEnabled = AccountFormLogic.openingSectionEnabled(state)

    var showCurrencies by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    // The empty-name error appears only once the user has typed in the field; the CTA explains it before that.
    var nameTouched by rememberSaveable { mutableStateOf(false) }

    FormScreen(
        title = stringResource(if (state.isEdit) R.string.account_form_edit_title else R.string.account_form_new_title),
        onBack = onBack,
        bottomBar = {
            FormCtaBar(
                label = ctaLabel(state.isEdit, blocker),
                enabled = blocker == null,
                onClick = onSave,
                error = if (state.saveFailed) stringResource(R.string.account_form_error_generic) else null,
            )
        },
    ) {
        NameGroup(
            state = state,
            nameError = when {
                AccountDraftError.NAME_TOO_LONG in errors -> R.string.account_form_error_name_long
                nameTouched && AccountDraftError.NAME_EMPTY in errors -> R.string.account_form_error_name_empty
                else -> null
            },
            onName = { nameTouched = true; onName(it) },
            onCurrencyClick = { showCurrencies = true },
        )

        SectionHeader(stringResource(R.string.account_form_type), Modifier.padding(top = 8.dp))
        TypeTrack(selected = state.type, onSelect = onType)

        SectionHeader(stringResource(R.string.account_form_initial_balance), Modifier.padding(top = 8.dp))
        OpeningGroup(
            state = state,
            enabled = openingEnabled,
            errors = errors,
            dateTooLate = dateTooLate,
            onAmount = onAmount,
            onDateClick = { showDatePicker = true },
        )

        SectionHeader(stringResource(R.string.picker_icon) + " · " + stringResource(R.string.picker_color), Modifier.padding(top = 8.dp))
        GroupedCard(Modifier.fillMaxWidth()) {
            IconPicker(IconKeys.accountGroups, state.icon, state.color, ColorPalette.ACCOUNT_DEFAULT, onIcon)
            ShelfDivider()
            Column(Modifier.padding(14.dp)) {
                Text(
                    stringResource(R.string.picker_color),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                ColorPicker(state.color, onColor)
            }
        }

        SectionHeader(stringResource(R.string.account_form_description), Modifier.padding(top = 8.dp))
        DescriptionCard(
            value = state.description,
            isError = AccountDraftError.DESCRIPTION_TOO_LONG in errors,
            onValueChange = onDescription,
        )
    }

    if (showCurrencies) {
        CurrencySheet(
            selected = state.currency,
            onSelect = { onCurrency(it); showCurrencies = false },
            onDismiss = { showCurrencies = false },
        )
    }
    if (showDatePicker) {
        OpeningDateDialog(
            initialMillis = state.dateMillis,
            policy = policy,
            latestAllowed = policy.latestAllowed(now, state.earliestOtherEntryAt),
            onSelect = { onDate(it); showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }
}

@Composable
private fun ctaLabel(isEdit: Boolean, blocker: AccountFormBlocker?): String = stringResource(
    when (blocker) {
        null -> if (isEdit) R.string.account_form_cta_save else R.string.account_form_cta_create
        AccountFormBlocker.LOADING -> R.string.account_form_cta_loading
        AccountFormBlocker.SAVING -> R.string.account_form_cta_saving
        AccountFormBlocker.NAME_EMPTY -> R.string.account_form_blocker_name
        AccountFormBlocker.NAME_TOO_LONG -> R.string.account_form_blocker_name_long
        AccountFormBlocker.CURRENCY_MISSING -> R.string.account_form_blocker_currency
        AccountFormBlocker.AMOUNT_INVALID -> R.string.account_form_blocker_amount
        AccountFormBlocker.DESCRIPTION_TOO_LONG -> R.string.account_form_blocker_description
        AccountFormBlocker.DATE_TOO_LATE -> R.string.account_form_blocker_date
    },
)

/** Name with the live icon badge, then the currency row (locked once the account exists). */
@Composable
private fun NameGroup(
    state: AccountFormState,
    nameError: Int?,
    onName: (String) -> Unit,
    onCurrencyClick: () -> Unit,
) {
    val currency = state.currency
    val currencyText = currency?.let { "${it.code} · ${it.name}" } ?: stringResource(R.string.account_form_currency_choose)
    GroupedCard(Modifier.fillMaxWidth().errorOutline(nameError != null)) {
        FormTextRow(
            icon = { IconBadge(state.icon, state.color, ColorPalette.ACCOUNT_DEFAULT, size = 40) },
            label = stringResource(R.string.account_form_name),
            value = state.name,
            onValueChange = onName,
            hint = stringResource(R.string.account_form_name_placeholder),
            textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            notes = { if (nameError != null) FieldNote(stringResource(nameError), isError = true) },
        )
        ShelfDivider()
        ShelfRow(
            icon = { ShelfIcon(if (state.isEdit) AppIcons.Lock else AppIcons.Currency) },
            label = stringResource(R.string.account_form_currency),
            onClick = if (state.isEdit) null else onCurrencyClick,
            supporting = if (state.isEdit) stringResource(R.string.account_form_currency_locked) else null,
            trailing = {
                ShelfValue(currencyText, muted = currency == null || state.isEdit)
                if (!state.isEdit) {
                    Icon(AppIcons.Dropdown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
}

/** Account type as a horizontally scrollable row of pills. */
@Composable
private fun TypeTrack(selected: AccountType, onSelect: (AccountType) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AccountType.entries.forEach { type ->
            FilterPill(
                label = stringResource(type.labelRes()),
                active = type == selected,
                onClick = { onSelect(type) },
            )
        }
    }
}

@Composable
private fun OpeningGroup(
    state: AccountFormState,
    enabled: Boolean,
    errors: Set<AccountDraftError>,
    dateTooLate: Boolean,
    onAmount: (String) -> Unit,
    onDateClick: () -> Unit,
) {
    if (!enabled) {
        GroupedCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ShelfIcon(AppIcons.Income)
                Text(
                    stringResource(R.string.account_form_unarchive_to_add),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    // A new account's opening balance is always dated today; the date is editable only later.
    val showDate = state.isEdit
    val amountError = errors.firstOrNull { it in AMOUNT_ERROR_SET }
    val dateError = dateTooLate || state.dateOrderRejected
    val earliest = state.earliestOtherEntryAt
    GroupedCard(Modifier.fillMaxWidth().errorOutline(amountError != null || (showDate && dateError))) {
        val negative = state.amountText.trim().startsWith("-")
        val signToggleLabel = stringResource(R.string.account_form_sign_toggle)
        FormTextRow(
            icon = { ShelfIcon(AppIcons.Income) },
            label = null,
            value = state.amountText,
            onValueChange = onAmount,
            hint = "0",
            textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            trailing = {
                Text(state.currency?.symbol.orEmpty(), style = MaterialTheme.typography.titleMedium)
                SmallPill(
                    onClick = { onAmount(AccountFormLogic.toggleSign(state.amountText)) },
                    modifier = Modifier.semantics { contentDescription = signToggleLabel },
                    text = if (negative) "−" else "+",
                )
            },
            notes = {
                amountError?.let {
                    FieldNote(
                        stringResource(
                            when (it) {
                                AccountDraftError.AMOUNT_TOO_PRECISE -> R.string.account_form_error_amount_precise
                                AccountDraftError.AMOUNT_OUT_OF_RANGE -> R.string.account_form_error_amount_range
                                else -> R.string.account_form_error_amount_invalid
                            },
                        ),
                        isError = true,
                    )
                }
                AccountFormLogic.balanceDelta(state)?.let { delta ->
                    Text(
                        stringResource(R.string.account_form_delta_warning, delta.format()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            },
        )
        if (showDate) {
            ShelfDivider()
            ShelfRow(
                icon = { ShelfIcon(AppIcons.Calendar) },
                label = stringResource(R.string.account_form_initial_date),
                onClick = onDateClick,
                supporting = when {
                    dateError -> stringResource(R.string.account_form_error_date_order)
                    earliest != null -> stringResource(R.string.account_form_earliest_helper, formatDate(earliest))
                    else -> null
                },
                supportingIsError = dateError,
                trailing = {
                    ShelfValue(formatDate(state.dateMillis), color = if (dateError) MaterialTheme.colorScheme.error else null)
                    Icon(
                        AppIcons.Next,
                        contentDescription = stringResource(R.string.account_form_pick_date),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
    }
}

private val AMOUNT_ERROR_SET = setOf(
    AccountDraftError.AMOUNT_INVALID,
    AccountDraftError.AMOUNT_TOO_PRECISE,
    AccountDraftError.AMOUNT_OUT_OF_RANGE,
)

@Composable
private fun DescriptionCard(value: String, isError: Boolean, onValueChange: (String) -> Unit) {
    GroupedCard(Modifier.fillMaxWidth().errorOutline(isError)) {
        FormTextRow(
            icon = { ShelfIcon(AppIcons.Edit) },
            label = null,
            value = value,
            onValueChange = onValueChange,
            hint = stringResource(R.string.account_form_description),
            singleLine = false,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            notes = { if (isError) FieldNote(stringResource(R.string.account_form_error_description_long), isError = true) },
        )
    }
}

@Composable
private fun CurrencySheet(selected: Currency?, onSelect: (Currency) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) CurrencyRegistry.all
        else CurrencyRegistry.all.filter { it.code.contains(q, true) || it.name.contains(q, true) }
    }
    FinoraSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxHeight(0.9f)) {
            FilterSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.account_form_currency_search),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            GroupedCard(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                LazyColumn {
                    items(matches, key = { it.code }) { c ->
                        val isSelected = c.code == selected?.code
                        ListRow(
                            icon = {
                                Box(Modifier.size(width = 48.dp, height = 24.dp), contentAlignment = Alignment.CenterStart) {
                                    Text(c.code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                }
                            },
                            title = c.name,
                            subtitle = null,
                            trailing = {
                                if (isSelected) Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            onClick = { onSelect(c) },
                            modifier = Modifier.semantics { this.selected = isSelected },
                        )
                    }
                }
            }
        }
    }
}

/** The Material picker works in UTC midnights; [OpeningDatePolicy] maps to and from local day starts. */
@Composable
private fun OpeningDateDialog(
    initialMillis: Long,
    policy: OpeningDatePolicy,
    latestAllowed: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val latestUtc = policy.toPickerUtcMidnight(latestAllowed)
    SingleDateDialog(
        initialUtcMillis = policy.toPickerUtcMidnight(initialMillis).coerceAtMost(latestUtc),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= latestUtc
        },
        onDismiss = onDismiss,
        onConfirm = { onSelect(policy.fromPickerUtcMidnight(it)) },
    )
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private const val PREVIEW_DAY = 24L * 60 * 60 * 1000

@Preview(showBackground = true, heightDp = 900, name = "New account light")
@Composable
private fun AccountEditNewLightPreview() = AccountEditPreview(dark = false, edit = false)

@Preview(showBackground = true, backgroundColor = 0xFF0F1412, heightDp = 900, name = "New account dark")
@Composable
private fun AccountEditNewDarkPreview() = AccountEditPreview(dark = true, edit = false)

@Preview(showBackground = true, heightDp = 1000, name = "Edit account light")
@Composable
private fun AccountEditEditLightPreview() = AccountEditPreview(dark = false, edit = true)

@Preview(showBackground = true, backgroundColor = 0xFF0F1412, heightDp = 1000, name = "Edit account dark")
@Composable
private fun AccountEditEditDarkPreview() = AccountEditPreview(dark = true, edit = true)

@Composable
private fun AccountEditPreview(dark: Boolean, edit: Boolean) {
    val usd = remember { CurrencyRegistry.get("USD") }
    val state = if (edit) {
        AccountFormState(
            isEdit = true, loaded = true, name = "Main card", type = AccountType.BANK, currency = usd,
            icon = "account_balance", color = ColorPalette.ACCOUNT_DEFAULT, description = "Salary goes here",
            amountText = "-1200.50", dateMillis = 100 * PREVIEW_DAY, originalOpeningMinor = 100_000,
            earliestOtherEntryAt = 120 * PREVIEW_DAY, saveFailed = true,
        )
    } else {
        AccountFormState(loaded = true, name = "", amountText = "")
    }
    PreviewTheme(dark) {
        AccountEditContent(
            state = state,
            policy = OpeningDatePolicy(ZoneOffset.UTC),
            now = 130 * PREVIEW_DAY,
            onBack = {}, onName = {}, onType = {}, onCurrency = {}, onIcon = {}, onColor = {},
            onDescription = {}, onAmount = {}, onDate = {}, onSave = {},
        )
    }
}
