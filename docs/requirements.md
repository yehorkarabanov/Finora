Собрал всё в единую спецификацию, объединив первоначальные требования и все твои последующие изменения.

На этом этапе документ будет именно Product Requirements Document (PRD) + Functional and Non-Functional Requirements, а не технический документ реализации.

То есть здесь намеренно не будет:
- Языков программирования и фреймворков.
- Backend, серверов и облачной инфраструктуры.
- Проектирования таблиц базы данных.
- API-контрактов.
- Деталей реализации алгоритмов.
  Но будут подробно описаны поведение приложения, сущности, правила работы с финансами, сценарии использования и критерии готовности.

Я также зафиксирую несколько важных принципов, чтобы при дальнейшей разработке AI coding agent не начал самостоятельно придумывать финансовую логику.

Personal Finance Tracker
Product Specification v1.0
Personal Finance Manager
Product type: Personal financial management application

Platform: Android

Operating model: Fully local, offline-first

Primary purpose: Track personal finances across multiple accounts, currencies, income sources and expense categories.

Target user: Individual user managing personal finances.

Core principles
- Local ownership of financial data.
- Accurate financial accounting.
- Multiple independent currencies.
- Flexible categorization and tagging.
- Fast transaction entry.
- Detailed financial analytics.
- Data portability and backup.

Part I. Product Overview
1. Product Vision
   Personal Finance Tracker is an application designed to provide a comprehensive overview of an individual's financial situation.

The application allows users to manage multiple financial accounts, track income and expenses, categorize transactions, monitor recurring payments, transfer money between accounts, and analyze their financial activity.

Unlike simple expense trackers, the application supports multiple currencies as a fundamental part of its financial model.

Every account maintains its own currency and balance. Currency conversion is not performed automatically when recording ordinary transactions. Instead, conversion is used when transferring funds between accounts with different currencies or when displaying aggregated financial information.

The application is designed to work entirely locally on the user's device. An internet connection is optional and may be used for external services such as retrieving currency exchange rates.
2. Product Goals
   The application should achieve the following goals:
1. Complete financial overview: Provide visibility into all personal financial accounts and transactions.
2. Multi-currency support: Allow simultaneous management of accounts in different currencies without forcing a common storage currency.
3. Accurate accounting: Ensure every transaction affects the appropriate account balance correctly.
4. Flexible categorization: Support hierarchical categories and direct assignment to parent categories.
5. Contextual analysis: Allow transactions to be grouped and analyzed through independent tags.
6. Automation: Support recurring income and expenses.
7. Financial insights: Provide statistics, reports and spending analysis.
8. Privacy: Keep financial information under the user's control.
9. Data portability: Allow complete backup and restoration of financial information.
3. Out of Scope
   The following functionality is explicitly excluded from the initial version:
- User registration and authentication.
- Cloud storage.
- Backend services.
- Multi-device synchronization.
- Automatic bank account integration.
- Automatic transaction imports from banks.
- Automatic payment execution.
- Investment portfolio tracking and securities valuation.
- Shared accounts with multiple users.
- AI-powered financial recommendations.
  These features may be considered in future versions but must not be prerequisites for the initial application.
  Part II. Functional Requirements
4. Account Management
   4.1 Account Overview
   Users must be able to create and manage multiple independent financial accounts.

Each account represents a separate source or location of money.

Examples:
- Bank accounts.
- Cash wallets.
- Savings accounts.
- Brokerage accounts.
- Other financial accounts.
  4.2 Account Properties
  Each account must have:
  Property	Description
  Name	User-defined account name
  Currency	Account's native currency
  Initial balance	Starting account balance
  Account type	Bank, Cash, Savings, Investment, Other
  Icon	User-selected visual icon
  Color	User-selected account color
  Creation date	Account creation date
  Description	Optional additional information
  Status	Active or archived

Icons and colors are visual attributes but must be preserved as part of the account's data and included in backups.
4.3 Account Operations
Users must be able to:
- Create accounts.
- Edit account information.
- Change account names.
- Change account icons and colors.
- View account balances.
- View account transaction history.
- Archive accounts.
- Delete accounts when permitted.
  4.4 Account Balance
  Each account maintains its balance in its own currency.

For example:
Account	Currency	Balance
Santander	PLN	5,000
IBKR	USD	2,000
Cash	EUR	300

The application must never automatically change an account's native balance due to exchange-rate fluctuations.

The balance must reflect:
- Initial balance.
- Income transactions.
- Expense transactions.
- Incoming transfers.
- Outgoing transfers.
  Archived accounts must retain their historical information and remain available for historical reporting.

5. Transaction Management
   Transactions are the central component of the application.

The application supports three primary transaction types:
1. Income.
2. Expense.
3. Transfer.
   5.1 Income
   Represents money received into an account.

Examples:
- Salary.
- Freelance payments.
- Investment income.
- Gifts.
- Refunds.
- Other incoming money.
  Required properties:
- Amount.
- Account.
- Category.
- Transaction date.
  Optional properties:
- Description.
- Tags.
  Income increases the balance of the associated account.
  5.2 Expense
  Represents money spent from an account.

Examples:
- Groceries.
- Rent.
- Transportation.
- Shopping.
- Subscriptions.
- Entertainment.
  Required properties:
- Amount.
- Account.
- Category.
- Transaction date.
  Optional properties:
- Description.
- Tags.
  Expenses decrease the balance of the associated account.
  5.3 Common Transaction Properties
  All ordinary transactions must contain:
  Property	Description
  ID	Unique transaction identifier
  Type	Income or Expense
  Amount	Transaction amount
  Currency	Currency of the associated account
  Account	Associated financial account
  Category	Assigned category
  Date	Date and time of the transaction
  Description	Optional text
  Tags	Zero or more associated tags
  Creation timestamp	When the record was created
  Modification timestamp	When the record was last modified

The transaction currency must correspond to the associated account's currency.
5.4 Transaction Operations
Users must be able to:
- Create transactions.
- Edit transactions.
- Delete transactions.
- Duplicate existing transactions.
- Browse transaction history.
- Search transactions.
- Filter transactions.
- Sort transactions by date, amount and other relevant properties.
  5.5 Transaction History
  The transaction history must provide a chronological overview of financial activity.

Users should be able to:
- View all transactions.
- Filter by transaction type.
- Filter by account.
- Filter by category.
- Filter by subcategory.
- Filter by one or multiple tags.
- Select a custom date range.
- Search by description.
- Sort by amount or date.
  The list should clearly distinguish income, expenses and transfers visually.

6. Category Management
   6.1 Overview
   Categories provide the primary classification system for financial transactions.

Income, expenses and transfers must have separate category groups.

Users must be able to create and manage their own categories.
6.2 Category Properties
Each category must have:
- Name.
- Icon.
- Color.
- Category type.
- Optional parent category.
- Creation date.
  Category types:
- Income.
- Expense.
- Transfer.
  Icons and colors must be included in backups.
  6.3 Hierarchical Categories
  Categories must support nesting up to 3 levels (category → subcategory → sub-subcategory).

A category can contain multiple subcategories, and subcategories can contain further subcategories.

Example:
Food

Parent

Groceries

Restaurants & Cafes


Coffee shops


Market



6.4 Direct Parent Assignment
Transactions may be assigned directly to any category, including categories that contain subcategories.

For example:
- Buying groceries can be assigned to Food → Groceries.
- Going to a restaurant can be assigned to Food → Restaurants.
- Buying something at a local market can be assigned directly to Food.
  The application must not force users to select a subcategory.
  6.5 Category Aggregation
  Parent categories must aggregate all transactions assigned to:
- The parent category itself.
- Its direct subcategories.
- All nested descendants.
  For example:
  Category	Expenses
  Food	1,250 PLN
  ├ Groceries	700 PLN
  ├ Restaurants	450 PLN
  └ Direct expenses	100 PLN

The total Food amount must be 1,250 PLN.

Users must be able to navigate from aggregated category statistics into more detailed breakdowns.
6.6 Category Management Operations
Users must be able to:
- Create categories.
- Create subcategories.
- Edit category names.
- Change icons and colors.
- Move categories within the hierarchy.
- Delete categories.
- Reassign transactions when deleting a category.
  Deleting a category must not silently delete associated financial transactions.

The user must be offered an appropriate reassignment or uncategorized option.

7. Tag Management
   7.1 Overview
   Tags provide an independent classification mechanism.

Unlike categories, tags are not hierarchical and are not restricted to a single transaction classification.

Categories answer:

What was the money spent on or received for?

Tags answer:

What additional context or purpose is associated with this transaction?
7.2 Tag Properties
Each tag must have:
- Unique identifier.
- Name.
- Optional visual color.
- Creation date.
  Tags do not require icons.
  7.3 Transaction Tagging
  Transactions must support zero or more tags.

Requirements:
- Users can create tags while editing transactions.
- Users can assign multiple tags to one transaction.
- The same tag can be reused across unlimited transactions.
- Tags can be removed from transactions without deleting the transactions.
- Tags can be renamed.
- Tags can be deleted independently of transaction history.
  I recommend supporting tags on all three transaction types, including transfers, to allow consistent analysis of financial activity.
  7.4 Tag Filtering
  Users must be able to filter transactions using one or multiple tags.

Two filtering modes must be supported:

Match ANY

Returns transactions containing at least one of the selected tags.

Match ALL

Returns only transactions containing every selected tag.
7.5 Tag Analytics
The application must provide a dedicated tag-based analytics view.

Users should be able to select one or more tags and view:
- Total income.
- Total expenses.
- Total transfer amounts, where relevant.
- Transactions associated with selected tags.
- Account distribution.
- Category distribution.
- Date-based distribution.
  The analytics view must support date filters and account filters.
  7.6 Example: Debt Tracking
  A user sometimes spends money belonging to another person from a shared account.

The user assigns the Debt tag to relevant expenses.

At the end of the month, the user selects the Debt tag and obtains:
- Total amount spent.
- Accounts from which money was spent.
- Categories in which the money was spent.
- Individual transactions.
- Spending over a selected period.
  The user can additionally combine tags such as Debt, Shared, and Travel to narrow down the results.

Tags must not change account balances or transaction amounts. They are strictly an analytical classification mechanism.

8. Transfer Management
   8.1 Overview
   Transfers represent money movement between accounts owned or managed by the user.

Transfers must be treated separately from income and expenses.

They must not inflate income or expense statistics.
8.2 Transfer Properties
Each transfer must contain:
- Source account.
- Destination account.
- Amount sent.
- Amount received.
- Source currency.
- Destination currency.
- Transfer date.
- Transfer category.
- Optional description.
- Optional tags.
- Optional transfer fee.
  8.3 Same-Currency Transfers
  When both accounts use the same currency, the user enters the amount being transferred.

Example:
Account	Change
Santander PLN	−500 PLN
Cash PLN	'+500 PLN

The transfer must not affect income or expense totals.
8.4 Cross-Currency Transfers
When accounts use different currencies, users must manually specify both amounts.

Example:
Account	Change
Santander PLN	−1,000 PLN
IBKR USD	'+250 USD

The application must preserve both original values.

The effective exchange rate may be displayed as additional information, calculated from the entered amounts.

The application must not replace user-provided amounts with externally retrieved exchange rates.
8.5 Transfer Fees
Users must be able to record transfer fees.

Fees must be treated as expenses and assigned to an expense category.

The application must distinguish between the amount transferred and the fee charged.
8.6 Transfer Categories
Transfers must have their own independent category hierarchy.

Examples:
- Own Account Transfer.
- Currency Exchange.
- Savings Transfer.
- Investment Deposit.
- Other.
  8.7 Transfer Integrity
  A transfer must be represented as one logical operation connecting two account balance changes.

Editing or deleting a transfer must update both sides of the operation together.

It must not be possible to accidentally leave one side of a transfer recorded while removing the other.

9. Recurring Transactions
   9.1 Overview
   Users must be able to define recurring income and expense operations.

Recurring operations represent predefined schedules that automatically generate regular transactions.
9.2 Recurring Properties
Each recurring schedule must contain:
- Name.
- Transaction type.
- Amount.
- Account.
- Category.
- Optional tags.
- Start date.
- Optional end date.
- Frequency.
- Active/inactive status.
- Next scheduled occurrence.
  9.3 Supported Frequencies
- Daily.
- Weekly.
- Monthly.
- Yearly.
- Custom interval.
  9.4 Recurring Operations
  Users must be able to:
- Create recurring schedules.
- Edit schedules.
- Pause schedules.
- Resume schedules.
- Skip individual occurrences.
- Change future amounts.
- Change future categories.
- Delete schedules.
  9.5 Transaction Generation
  Recurring schedules must generate ordinary transaction records.

Generated transactions must:
- Appear in transaction history.
- Affect the appropriate account balance.
- Contain the configured category.
- Preserve configured tags.
- Be distinguishable as automatically generated if desired.
  9.6 Missed Occurrences
  If the application was not opened during one or more scheduled periods, it must handle missed occurrences when it becomes active again.

Each scheduled occurrence must be generated no more than once.

The application must not silently lose scheduled operations.
9.7 Historical Integrity
Changes to recurring schedules must not modify already generated transactions.

Deleting a recurring schedule must not delete its historical transactions.

10. Currency Management
    10.1 Multi-Currency Support
    The application must support multiple currencies simultaneously.

Currencies must follow recognized currency codes, such as:
- PLN.
- EUR.
- USD.
- GBP.
- UAH.
  Each account has exactly one native currency.
  10.2 Currency Independence
  The native currency of an account determines how its balance is stored and displayed.

Ordinary transactions must use the currency of their associated account.

The application must not:
- Automatically convert account balances.
- Automatically change historical transaction amounts.
- Modify transaction values when exchange rates change.
- Require a universal base currency for financial storage.
  10.3 Display Currency
  The application must provide a configurable Display Currency.

This currency is used exclusively for presenting aggregated financial information.

For example, a user can have:
- Santander: 5,000 PLN.
- IBKR: 2,000 USD.
- Cash: 300 EUR.
  With Display Currency set to PLN, the application can present an estimated total value across all accounts.

Changing Display Currency must not modify any original financial data.
10.4 Exchange Rates
Exchange rates may be retrieved from an external service.

Requirements:
- Support current exchange rates.
- Cache previously retrieved rates.
- Reuse cached rates where possible.
- Allow manual rate refresh.
- Handle unavailable network connections.
- Display when a rate was last updated.
- Support manual exchange-rate overrides if necessary.
  Currency conversion must only affect presentation and analytical calculations.
  10.5 Rate Availability
  When an exchange rate is unavailable:
- Use the latest available cached rate if appropriate.
- Clearly indicate that the displayed valuation may be outdated.
- Never silently treat unavailable rates as zero.
- Never change the underlying account balance.
  10.6 Valuation Accuracy
  Aggregated financial amounts must be identified as estimates when exchange rates are used.

The application must distinguish native account balances from converted display values.

Part III. Dashboard and Financial Analytics
11. Dashboard
    11.1 Overview
    The Dashboard is the main screen of the application.

Its purpose is to provide an immediate overview of the user's financial situation without requiring navigation through individual accounts or reports.
11.2 Main Dashboard Elements
The Dashboard should contain:
1. Total balance across all accounts in Display Currency.
2. Individual account balances in their native currencies.
3. Total income for the selected period.
4. Total expenses for the selected period.
5. Net cash flow.
6. Expense distribution by category.
7. Recent transactions.
8. Upcoming recurring transactions, if applicable.
   11.3 Dashboard Filters
   Users must be able to select:
- Today.
- This week.
- This month.
- This year.
- Custom date range.
  The selected period must affect the relevant income, expense and category statistics.
  11.4 Account Overview
  The Dashboard must show a summary of all active accounts.

Users should be able to navigate directly from an account summary to its transaction history.

Archived accounts should not normally appear in the main account overview, but must remain accessible through account management and historical reports.
11.5 Financial Summary
The Dashboard must distinguish between:
- Total income.
- Total expenses.
- Net cash flow.
- Total account valuation.
  Net cash flow is calculated as:

Total Income − Total Expenses

Transfers must not be included in this calculation.

Total account valuation is the combined estimated value of all active account balances converted into the selected Display Currency.

12. Statistics and Analytics
    12.1 Overview
    The application must provide a dedicated statistics section for detailed financial analysis.

Users should be able to analyze their financial activity across different time periods, accounts, categories and tags.
12.2 Expense Analytics
The application must support:
- Total expenses.
- Expenses by category.
- Expenses by subcategory.
- Expenses by account.
- Expenses by tag.
- Monthly expense trends.
- Yearly expense trends.
- Comparison between different periods.
  12.3 Income Analytics
  The application must support:
- Total income.
- Income by category.
- Income by subcategory.
- Income by account.
- Income by tag.
- Monthly income trends.
- Yearly income trends.
- Comparison between different periods.
  12.4 Category Drill-Down
  Users must be able to navigate from high-level categories into their subcategories.

For example:

Food → Groceries → Individual transactions.

The displayed parent category total must include all descendants and direct transactions.
12.5 Tag Analytics
Tag analytics must allow users to inspect all transactions associated with selected tags.

The user must be able to combine:
- Tags.
- Accounts.
- Categories.
- Transaction types.
- Date ranges.
  For example, a user can filter all expenses with the Debt tag across all accounts during November.
  12.6 Account Analytics
  Each account must have its own statistics view.

It should include:
- Current balance.
- Initial balance.
- Total income.
- Total expenses.
- Incoming transfers.
- Outgoing transfers.
- Transaction history.
- Balance changes over time.
  12.7 Currency Presentation in Reports
  All aggregated financial statistics must use the selected Display Currency where conversion is required.

Individual account and transaction details must retain their native currencies.

Important distinction: converted historical reports may change when exchange rates are updated if the application uses current rates for their presentation. This must be consistent and transparent to the user.

13. Budgets (Optional Feature)
    Budgets are a planned extension and are not mandatory for the initial release.

Users may define spending limits for individual categories.

Examples:
- Food: 1,000 PLN per month.
- Entertainment: 300 PLN per month.
- Shopping: 500 PLN per month.
  Requirements:
- Define a budget amount.
- Assign a category.
- Select a period.
- Track actual spending against the budget.
- Display remaining budget.
- Support monthly and custom periods.
  Parent category budgets must include spending in their descendant categories.

Budgets should be treated as informational limits and must not prevent users from recording transactions.

Part IV. Additional Functionality
14. Transaction Templates
    Users should be able to create reusable transaction templates.

Templates may contain:
- Transaction type.
- Default amount.
- Account.
- Category.
- Tags.
- Description.
  Templates allow users to create frequently used transactions with fewer actions.

Templates do not automatically generate transactions. That functionality belongs to recurring schedules.
15. Search and Filtering
    The application must provide a unified search and filtering experience.

Supported filtering dimensions:
Filter	Description
Transaction type	Income, Expense, Transfer
Account	One or multiple accounts
Category	Parent or subcategory
Tags	One or multiple tags
Date	Custom date range
Amount	Minimum and maximum
Description	Text search

Users must be able to combine multiple filters.

For example:

Find all expenses from Santander, assigned to Food, containing the Debt tag, during November.
16. Data Export and Backup
    16.1 Backup
    Users must be able to create complete backups of their financial data.

A backup must include:
- Accounts.
- Account balances and initial balances.
- Transactions.
- Transfers.
- Categories.
- Category hierarchies.
- Category icons and colors.
- Account icons and colors.
- Tags.
- Recurring schedules.
- Application settings.
- Display Currency.
- Other user-defined configuration.
  The backup must preserve relationships between entities.
  16.2 Restore
  Users must be able to restore a previously created backup.

The application must:
- Validate backup integrity.
- Detect incompatible backup formats.
- Avoid silently corrupting existing data.
- Clearly communicate whether restoration replaces or merges existing data.
  16.3 CSV Export
  Users must be able to export transaction history into CSV format.

Export should support:
- All transactions.
- Selected accounts.
- Selected transaction types.
- Selected date ranges.
- Selected categories.
  The export must preserve original transaction amounts and currencies.

Transfers should retain both their source and destination information.

17. Settings and Personalization
    The application must provide a central settings section.
    17.1 General Settings
- Display Currency.
- Application language.
- Date format.
- Number format.
- First day of the week.
- Light, dark or system theme.
  17.2 Financial Settings
- Default account for new transactions.
- Default transaction category, if configured.
- Exchange-rate refresh preferences.
- Display currency preferences.
  17.3 Data Management
- Create backup.
- Restore backup.
- Export CSV.
- Manage application data.
- View application data information.
  17.4 Personalization
  Users should be able to customize:
- Account icons and colors.
- Category icons and colors.
- Tag colors.
- Visual preferences.

Part V. User Experience Requirements
18. Navigation
    The application should have a simple and predictable navigation structure.

Suggested primary navigation:
Dashboard


Transactions


Accounts


Statistics




Additional navigation

Categories
Tags
Recurring
Transfers
Settings


This is a proposed navigation structure, not a mandatory visual design.
19. Transaction Entry Experience
    Adding a transaction should be one of the fastest and most accessible operations in the application.

Requirements:
- Minimal number of required fields.
- Clear amount entry.
- Easy account selection.
- Easy category selection.
- Optional tags.
- Current date selected by default.
- Easy access to recent categories and accounts.
- Clear indication of the transaction currency.
  The user should be able to add a simple expense without navigating through multiple screens.
20. Visual Design
    The application should have a clean, modern and minimal interface.

Design principles:
- Clear information hierarchy.
- Consistent visual language.
- Distinctive but restrained colors.
- Readable financial figures.
- Consistent account and category icons.
- Clear differentiation between income and expenses.
- Minimal unnecessary animations.
- Responsive layouts for different Android screen sizes.
  Support both light and dark themes.

Part VI. Non-Functional Requirements
21. Data Storage and Privacy
    NFR-01 — Local Data Ownership

All financial information must be stored locally on the user's device.

NFR-02 — No Mandatory Internet Connection

The application must not require an internet connection to access or manage financial data.

NFR-03 — Privacy

Financial information must not be transmitted to external services except for explicitly required requests, such as retrieving exchange rates.

NFR-04 — Data Protection

The application should protect locally stored financial information against unauthorized access, with additional protection for sensitive backups.

NFR-05 — No Third-Party Financial Tracking

The initial application must not automatically connect to banks, brokers or payment providers.
22. Reliability and Data Integrity
    NFR-06 — Transaction Integrity

Every completed financial operation must be reflected consistently in all affected accounts.

NFR-07 — Transfer Integrity

Both sides of a transfer must remain consistent.

NFR-08 — No Duplicate Recurring Transactions

A scheduled occurrence must never be generated more than once.

NFR-09 — Historical Preservation

Changes to account settings, categories, tags or recurring schedules must not silently modify historical transaction amounts.

NFR-10 — Data Persistence

Financial information must remain available after application restarts and device reboots.

NFR-11 — Error Recovery

An interrupted operation must not leave account balances in an inconsistent state.
23. Financial Accuracy
    NFR-12 — Decimal Precision

Financial calculations must preserve appropriate decimal precision.

NFR-13 — Native Currency Preservation

Original transaction amounts must always be retained in their original currencies.

NFR-14 — Exchange Rate Transparency

Converted amounts must be distinguishable from actual account balances.

NFR-15 — No Implicit Currency Conversion

The application must never silently convert account balances or transaction amounts.

NFR-16 — Consistent Rounding

Currency-specific display precision must be respected without unnecessarily discarding stored precision.
24. Performance
    NFR-17 — Responsive Interface

Common operations must complete without noticeable delays.

NFR-18 — Transaction History

The application must remain responsive as transaction history grows.

NFR-19 — Analytics

Statistics and reports must remain usable with large transaction histories.

NFR-20 — Background Processing

Recurring transaction generation and external exchange-rate retrieval must not block normal application usage.
25. Maintainability and Extensibility
    NFR-21 — Extensible Data Model

The application must support future additions without requiring fundamental changes to its existing financial model.

NFR-22 — Backup Compatibility

Backup formats must support versioning and future migration.

NFR-23 — Future Backend Compatibility

The application may eventually support external synchronization, but its initial functionality must remain fully independent of any backend.

NFR-24 — Currency Extensibility

New currencies and exchange-rate providers must be supportable without changing the basic account and transaction concepts.

Part VII. Business Rules and Important Constraints
Эта часть особенно важна. Здесь фиксируются правила, которые приложение должно соблюдать независимо от интерфейса и способа реализации.
26. Account Rules
1. Каждый счёт имеет ровно одну собственную валюту.
2. Баланс каждого счёта рассчитывается в его собственной валюте.
3. Изменение Display Currency не меняет баланс счёта.
4. Изменение валютных курсов не меняет баланс счёта.
5. Архивирование счёта не удаляет его историю.
6. Удаление счёта с историческими операциями не должно приводить к потере финансовых данных.
7. Изменение валюты счёта с существующими транзакциями не должно автоматически пересчитывать историю. Предпочтительно запрещать такое изменение и предлагать создать новый счёт.
8. Начальный баланс должен учитываться отдельно от обычных доходов и расходов.
9. Отрицательный баланс должен допускаться, поскольку фактический баланс счёта может стать отрицательным.
27. Transaction Rules
1. Каждая Income увеличивает баланс соответствующего счёта.
2. Каждая Expense уменьшает баланс соответствующего счёта.
3. Каждая операция относится к одной категории соответствующего типа.
4. Категория может быть родительской или вложенной.
5. Транзакция может содержать любое количество тегов.
6. Изменение категории не должно менять сумму транзакции.
7. Изменение тегов не должно менять баланс.
8. Удаление транзакции должно корректно пересчитывать баланс.
9. Изменение даты транзакции не должно менять её сумму или валюту.
10. Все операции должны сохранять первоначальную валюту и сумму.
28. Transfer Rules
1. Перевод всегда связывает два разных счёта.
2. Переводы не считаются доходами или расходами.
3. Переводы между одинаковыми валютами используют одинаковые суммы списания и зачисления, если пользователь не указывает иное.
4. Переводы между разными валютами требуют ручного ввода суммы списания и зачисления.
5. Фактические суммы не должны изменяться после обновления курсов.
6. Комиссия за перевод учитывается как отдельный расход.
7. Перевод должен иметь собственную категорию.
8. Удаление или редактирование перевода должно затрагивать обе стороны операции.
9. Переводы между своими счетами не должны влиять на чистый денежный поток.
29. Category Rules
1. Категории Income, Expense и Transfer независимы.
2. Подкатегория наследует тип родительской категории.
3. Категория может содержать прямые транзакции и подкатегории одновременно.
4. Родительская категория агрегирует все вложенные транзакции.
5. Удаление категории не должно автоматически удалять транзакции.
6. Перемещение категории в другую ветку не должно менять исторические суммы.
7. Циклическая вложенность категорий не допускается.
30. Tag Rules
1. Теги не имеют обязательной иерархии.
2. Транзакция может иметь ноль или несколько тегов.
3. Один тег может использоваться в неограниченном количестве транзакций.
4. Удаление тега не удаляет связанные транзакции.
5. Переименование тега должно отражаться во всех связанных операциях.
6. Фильтрация должна поддерживать ANY и ALL.
7. Теги не должны влиять на финансовые балансы.
31. Recurring Transaction Rules
1. Расписание создаёт обычные Income или Expense транзакции.
2. Каждое запланированное событие создаётся не более одного раза.
3. Уже созданные транзакции не изменяются при редактировании расписания.
4. Пауза расписания прекращает создание новых операций.
5. Удаление расписания сохраняет созданные ранее операции.
6. Пропуск одного события не должен отменять всё расписание.
7. Пропущенные события должны обрабатываться после возвращения приложения к активному состоянию.
8. Регулярные операции должны сохранять исходную валюту счёта.
9. Изменение суммы расписания влияет только на будущие операции.
32. Currency and Display Rules
1. Счета и транзакции всегда сохраняют собственные валюты.
2. Display Currency используется исключительно для отображения агрегированных значений.
3. Все курсы используются только для конвертации отображаемых значений.
4. Фактические суммы переводов задаются пользователем.
5. Отсутствующий курс не должен приводить к нулевой оценке.
6. Устаревшие курсы должны быть визуально обозначены.
7. Переключение Display Currency не должно изменять историю.
8. Все конвертированные суммы должны быть обозначены как расчётные значения, а не как реальные остатки.

Part VIII. Acceptance Criteria
33. MVP Acceptance Criteria
    Приложение считается функционально готовым к первой полноценной эксплуатации, если выполняются следующие условия.

ACCOUNTS
- [ ] Пользователь может создать несколько счетов.
- [ ] Каждый счёт имеет собственную валюту.
- [ ] Каждый счёт имеет настраиваемые иконку и цвет.
- [ ] Балансы отображаются в родных валютах.
- [ ] Архивирование не уничтожает историю.
  TRANSACTIONS
- [ ] Пользователь может добавлять доходы и расходы.
- [ ] Транзакции автоматически учитываются в балансе соответствующего счёта.
- [ ] Пользователь может редактировать и удалять транзакции.
- [ ] Поддерживается история операций.
- [ ] Поддерживаются поиск и фильтрация.
  CATEGORIES
- [ ] Пользователь может создавать категории.
- [ ] Категории имеют иконки и цвета.
- [ ] Поддерживается вложенность категорий.
- [ ] Можно добавлять транзакции непосредственно в родительскую категорию.
- [ ] Родительские категории корректно агрегируют суммы подкатегорий.
  TAGS
- [ ] Пользователь может создавать собственные теги.
- [ ] Можно присваивать несколько тегов одной транзакции.
- [ ] Поддерживается фильтрация ANY и ALL.
- [ ] Можно просматривать статистику по тегам.
- [ ] Можно анализировать теги по счетам и категориям.
  TRANSFERS
- [ ] Можно переводить деньги между счетами.
- [ ] Поддерживаются одинаковые и разные валюты.
- [ ] Для разных валют пользователь задаёт обе суммы.
- [ ] Переводы не учитываются как доходы или расходы.
- [ ] Поддерживаются категории переводов.
- [ ] Обе стороны перевода всегда синхронизированы.
- [ ] Поддерживаются комиссии.
  RECURRING
- [ ] Можно создавать ежедневные, еженедельные, ежемесячные и ежегодные расписания.
- [ ] Автоматически создаются обычные транзакции.
- [ ] Не возникают дубликаты.
- [ ] Поддерживаются пауза, возобновление и пропуск.
- [ ] Изменение расписания не изменяет историю.
  CURRENCY
- [ ] Поддерживаются разные валюты.
- [ ] Можно выбрать Display Currency.
- [ ] Курсы используются для отображения общей суммы.
- [ ] Обновление курсов не изменяет реальные балансы.
- [ ] Поддерживается работа с кэшированными курсами.
- [ ] Отсутствие интернета не блокирует финансовые операции.
  DASHBOARD & ANALYTICS
- [ ] Отображается общая оценка финансов.
- [ ] Отображаются доходы и расходы.
- [ ] Есть распределение по категориям.
- [ ] Есть статистика по счетам.
- [ ] Есть статистика по тегам.
- [ ] Поддерживаются различные временные периоды.
  DATA MANAGEMENT
- [ ] Все данные сохраняются локально.
- [ ] Приложение работает без интернета.
- [ ] Можно создать полный backup.
- [ ] Можно восстановить backup.
- [ ] Можно экспортировать транзакции в CSV.

Part IX. Feature Prioritization
Чтобы не пытаться реализовать всё сразу, предлагаю разделить требования по приоритетам.
Приоритет	Функциональность
P0	Accounts
P0	Income / Expense
P0	Categories / Subcategories
P0	Transfers
P0	Multi-currency accounts
P0	Tags and filtering
P0	Dashboard
P1	Recurring transactions
P1	Currency rate retrieval
P1	Advanced analytics
P1	Backup / Restore
P1	CSV export
P2	Budgets
P2	Transaction templates
P2	Advanced reports
P2	Attachments and receipts

P0 — основа приложения.

P1 — полноценный пользовательский опыт.

P2 — дополнительные возможности, которые можно реализовать после стабилизации основной функциональности.
Part X. Final Product Decisions
Чтобы документ был однозначным, фиксирую следующие решения, исходя из нашего обсуждения.
Вопрос	Принятое решение
Хранение валют	Каждый счёт хранит собственную валюту
Базовая валюта	Отсутствует
Display Currency	Есть, только для отображения
Курсы валют	Используются для конвертации отображаемых значений
Переводы между валютами	Обе суммы задаются пользователем
Иерархия категорий	Ограничена: максимум 3 уровня
Прямые транзакции в родительских категориях	Разрешены
Теги	Множественные, независимые от категорий
Теги у переводов	Разрешены
Фильтрация тегов	ANY и ALL
Категории переводов	Отдельная группа
Регулярные операции	Автоматически создают обычные транзакции
Backend	Отсутствует в первой версии
Хранение данных	Только локально
Интернет	Необязателен, используется для дополнительных функций

Важные уточнения к приоритетам
Я бы изменил предыдущую таблицу приоритетов в двух местах:
- Recurring Transactions следует отнести к P0, поскольку автоматические платежи были частью первоначальной основной функциональности.
- Multi-currency Display и получение курсов также относятся к P0, поскольку без них невозможно полноценно видеть общую финансовую картину.
  Backup & Restore можно оставить P1, но структуру данных и пользовательские настройки необходимо изначально проектировать с учётом будущего резервного копирования.

Итог
Получается приложение с шестью основными функциональными направлениями:
Accounts
Мультивалютные счета и балансы

Transactions
Доходы, расходы и переводы

Categories
Иерархические категории

Tags
Гибкая классификация и аналитика

Recurring
Автоматические регулярные операции

Analytics
Общая статистика и финансовый обзор


Моя оценка: это уже достаточно полноценная спецификация для начала разработки. При этом она не перегружена преждевременными техническими решениями и оставляет свободу выбора реализации.

Я бы сохранил именно эту версию как исходный PRD и перед началом разработки использовал её как единый источник требований. Особенно важно не потерять три ключевые особенности продукта: независимые валюты счетов, иерархические категории с прямым назначением транзакций и независимую систему тегов. Именно они отличают твой проект от обычного трекера расходов.