package com.yehor.finora.ui.operations

import com.yehor.finora.ui.common.AppIcons
import com.yehor.finora.ui.common.IconSize
import com.yehor.finora.ui.theme.finance
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.money.AmountParseResult
import com.yehor.finora.domain.money.AmountParser
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.TransactionDraftError
import com.yehor.finora.domain.operation.TransactionDraftValidator
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.ui.common.AccountPickerContent
import com.yehor.finora.ui.common.CategoryTreePickerContent
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.DateTimePickerFlow
import com.yehor.finora.ui.common.IconBadge
import com.yehor.finora.ui.common.PickerMode
import com.yehor.finora.ui.common.rememberTimeFormatter
import com.yehor.finora.ui.common.TagChip
import com.yehor.finora.ui.common.TagPicker
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val AMOUNT_ERRORS = setOf(
    TransactionDraftError.AMOUNT_INVALID,
    TransactionDraftError.AMOUNT_TOO_PRECISE,
    TransactionDraftError.AMOUNT_NOT_POSITIVE,
    TransactionDraftError.AMOUNT_OUT_OF_RANGE,
)

// Account → tags are one connected group: only its outer corners are rounded.
private val GroupRadius = 20.dp
private val GroupInnerRadius = 4.dp
private val GroupTopShape = RoundedCornerShape(
    topStart = GroupRadius, topEnd = GroupRadius,
    bottomStart = GroupInnerRadius, bottomEnd = GroupInnerRadius,
)
private val GroupMiddleShape = RoundedCornerShape(GroupInnerRadius)
private val GroupBottomShape = RoundedCornerShape(
    topStart = GroupInnerRadius, topEnd = GroupInnerRadius,
    bottomStart = GroupRadius, bottomEnd = GroupRadius,
)
private const val MINUS = "−"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditScreen(
    onBack: () -> Unit,
    onSelectTransfer: (() -> Unit)? = null,
    onDuplicate: ((Long) -> Unit)? = null,
    onNavigateCreateCategory: ((String) -> Unit)? = null,
    onOpenSchedule: ((Long) -> Unit)? = null,
    viewModel: TransactionEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedAnotherMessage = stringResource(R.string.transaction_form_saved_another)
    LaunchedEffect(state.savedAnotherCount) {
        if (state.savedAnotherCount > 0) {
            scrollState.animateScrollTo(0)
            snackbarHostState.showSnackbar(savedAnotherMessage)
        }
    }

    var menuOpen by remember { mutableStateOf(false) }
    var pickAccount by rememberSaveable { mutableStateOf(false) }
    var pickCategory by rememberSaveable { mutableStateOf(false) }
    var pickDateTime by rememberSaveable { mutableStateOf(false) }

    val categoryType = if (state.kind == TransactionKind.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
    val tree = remember(state.categories) { CategoryTree(state.categories) }
    val hasCategoriesOfKind = remember(tree, categoryType) { tree.rootsOf(categoryType).isNotEmpty() }

    val selectedAccount = state.selectedAccount
    val currency = state.currency
    val isIncome = state.kind == TransactionKind.INCOME
    val kindColor = if (isIncome) MaterialTheme.finance.income else MaterialTheme.finance.expense
    val symbols = remember { DecimalFormatSymbols.getInstance() }
    val amountDisplay = AmountKeypad.format(state.amountText, symbols.groupingSeparator, symbols.decimalSeparator) + " " + currency.symbol;

    val parsedMinor = (AmountParser.parse(state.amountText, currency) as? AmountParseResult.Value)?.minor
    val balance = selectedAccount?.let { state.balances[it.id] }
    val balanceAfter = if (!state.isEdit && balance != null && parsedMinor != null && parsedMinor > 0) {
        runCatching {
            val delta = Money(parsedMinor, balance.currency)
            if (isIncome) balance + delta else balance - delta
        }.getOrNull()
    } else {
        null
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEdit) R.string.transaction_form_edit_title else R.string.transaction_form_new_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (state.isEdit) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(AppIcons.More, contentDescription = stringResource(R.string.action_more_options))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (onDuplicate != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.transaction_form_duplicate)) },
                                        onClick = {
                                            menuOpen = false
                                            onDuplicate(state.operationId)
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.onDeleteRequest()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (state.isEdit) 12.dp else 0.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // A disabled button says what is missing instead of just greying out.
                    val blocker = when {
                        state.canSave -> null
                        TransactionDraftError.AMOUNT_EMPTY in state.errors -> R.string.transaction_form_error_amount_empty
                        state.selectedAccountId == null -> R.string.transaction_form_error_account_missing
                        TransactionDraftError.CATEGORY_MISSING in state.errors -> R.string.transaction_form_error_category_missing
                        else -> null
                    }
                    val ctaLabel = when {
                        blocker != null -> stringResource(blocker)
                        state.isEdit -> stringResource(R.string.transaction_form_cta_save)
                        else -> stringResource(
                            if (isIncome) R.string.transaction_form_cta_add_income else R.string.transaction_form_cta_add_expense,
                        ) + " — " + amountDisplay
                    }
                    Button(
                        onClick = { viewModel.save() },
                        enabled = state.canSave,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                    ) {
                        if (blocker == null) {
                            Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(ctaLabel, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!state.isEdit) {
                        TextButton(onClick = { viewModel.save(createAnother = true) }, enabled = state.canSave) {
                            Icon(AppIcons.AddCircle, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.transaction_form_save_and_another))
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val scheduleId = state.recurringScheduleId
            val scheduleName = state.recurringScheduleName
            if (scheduleId != null && scheduleName != null && onOpenSchedule != null) {
                TextButton(onClick = { onOpenSchedule(scheduleId) }) {
                    Icon(AppIcons.Recurring, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.transaction_form_created_by_schedule, scheduleName))
                }
            }

            KindTabs(
                kind = state.kind,
                enabled = !state.isEdit,
                onKindChange = viewModel::onKindChange,
                onSelectTransfer = onSelectTransfer,
            )

            AmountCard(
                amountText = state.amountText,
                amountDisplay = amountDisplay,
                sign = if (isIncome) "+" else MINUS,
                signColor = kindColor,
                currencyScale = currency.scale,
                currencySymbol = currency.symbol,
                accountLabel = selectedAccount?.let { "${it.currency.code} (${it.currency.symbol}) • ${it.name}" },
                errorText = when {
                    state.isOverflow || TransactionDraftError.AMOUNT_OUT_OF_RANGE in state.errors ->
                        stringResource(R.string.transaction_form_error_amount_range)
                    TransactionDraftError.AMOUNT_INVALID in state.errors ->
                        stringResource(R.string.transaction_form_error_amount_invalid)
                    TransactionDraftError.AMOUNT_TOO_PRECISE in state.errors ->
                        stringResource(R.string.transaction_form_error_amount_precise)
                    TransactionDraftError.AMOUNT_NOT_POSITIVE in state.errors ->
                        stringResource(R.string.transaction_form_error_amount_positive)
                    else -> null
                },
                decimalSeparator = symbols.decimalSeparator,
                onAccountClick = { pickAccount = true },
                onAmountChange = viewModel::onAmountChange,
            )

            // Account → tags: one connected group with a thin divider gap between rows.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionCard(shape = GroupTopShape, onClick = { pickAccount = true }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(selectedAccount?.icon, selectedAccount?.color, ColorPalette.ACCOUNT_DEFAULT, size = 44)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                selectedAccount?.name ?: stringResource(R.string.transaction_form_account),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            selectedAccount?.let {
                                Spacer(Modifier.width(8.dp))
                                Pill(it.currency.code)
                            }
                        }
                        val subtitle = when {
                            selectedAccount?.status == AccountStatus.ARCHIVED ->
                                stringResource(R.string.account_detail_archived_banner)
                            balance != null && balanceAfter != null -> stringResource(
                                R.string.transaction_form_balance_after,
                                formatMoney(balance, symbols),
                                formatMoney(balanceAfter, symbols),
                            )
                            balance != null -> stringResource(R.string.transaction_form_balance, formatMoney(balance, symbols))
                            else -> null
                        }
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (balanceAfter != null && balanceAfter.amountMinor < 0) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Icon(AppIcons.Next, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Category
            val category = state.selectedCategoryId?.let { tree.category(it) }
            // Resolve composable values unconditionally: a composable call nested in `?: if (...)` is skipped once a
            // category is picked, which shifted the slot table and crashed the next SectionCard on recomposition.
            val uncategorizedLabel = stringResource(R.string.picker_uncategorized)
            val categoryName = category?.name
                ?: if (state.isEdit && !state.hadCategoryBefore) uncategorizedLabel else null
            val showCategoryError = TransactionDraftError.CATEGORY_MISSING in state.errors && state.amountText.isNotBlank()
            val parentPath = remember(category, tree) {
                generateSequence(category?.parentId?.let { tree.category(it) }) { c -> c.parentId?.let { tree.category(it) } }
                    .toList()
                    .asReversed()
                    .joinToString(" › ") { it.name }
                    .ifEmpty { null }
            }
            val recents = state.recentCategoryIds.mapNotNull { tree.category(it) }.take(6)
            SectionCard(shape = GroupMiddleShape, onClick = { pickCategory = true }, isError = showCategoryError) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(category?.icon, category?.color, ColorPalette.defaultFor(categoryType), size = 44)
                    Column(Modifier.weight(1f)) {
                        Text(
                            categoryName ?: stringResource(R.string.transaction_form_category),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (categoryName == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        when {
                            showCategoryError -> Text(
                                stringResource(R.string.transaction_form_error_category_missing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            parentPath != null -> Text(
                                stringResource(R.string.transaction_form_in_parent, parentPath),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Icon(AppIcons.Next, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (recents.isNotEmpty() || (!hasCategoriesOfKind && onNavigateCreateCategory != null)) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (recents.isNotEmpty()) {
                            Text(
                                stringResource(R.string.transaction_form_recent),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        recents.forEach { recent ->
                            FilterChip(
                                selected = recent.id == state.selectedCategoryId,
                                onClick = { viewModel.onCategoryChange(recent.id) },
                                label = { Text(recent.name) },
                            )
                        }
                        if (!hasCategoriesOfKind && onNavigateCreateCategory != null) {
                            FilterChip(
                                selected = false,
                                onClick = { onNavigateCreateCategory(categoryType.name) },
                                label = { Text(stringResource(R.string.transaction_form_create_category)) },
                                leadingIcon = { Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(IconSize.Small)) },
                            )
                        }
                    }
                }
            }

            // Date & time
            val today = LocalDate.now()
            val futureDate = TransactionDraftError.DATE_IN_FUTURE in state.errors
            SectionCard(shape = GroupMiddleShape, onClick = { pickDateTime = true }, isError = futureDate) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LeadingIcon(AppIcons.Calendar)
                    Column(Modifier.weight(1f)) {
                        Text(dateLabel(state.date, today), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (futureDate) {
                                stringResource(R.string.transaction_form_error_future_date)
                            } else {
                                stringResource(
                                    R.string.transaction_form_time_hint,
                                    state.time.format(rememberTimeFormatter()),
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (futureDate) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(AppIcons.Next, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        today to R.string.transactions_day_today,
                        today.minusDays(1) to R.string.transactions_day_yesterday,
                    ).forEach { (day, label) ->
                        FilterChip(
                            selected = state.date == day,
                            onClick = {
                                viewModel.onDateChange(day)
                                // Picking "today" after a later time was chosen for yesterday must not land in the future.
                                if (day == today && state.time.isAfter(LocalTime.now())) {
                                    viewModel.onTimeChange(LocalTime.now().withSecond(0).withNano(0))
                                }
                            },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }

            // Merchant & description
            val descriptionTooLong = TransactionDraftError.DESCRIPTION_TOO_LONG in state.errors
            SectionCard(shape = GroupMiddleShape, isError = descriptionTooLong) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LeadingIcon(AppIcons.Merchant)
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.transaction_form_merchant_description),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                        BasicTextField(
                            value = state.description,
                            onValueChange = viewModel::onDescriptionChange,
                            textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done,
                            ),
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                Box {
                                    if (state.description.isEmpty()) {
                                        Text(
                                            stringResource(R.string.transaction_form_description_hint),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                        // The counter only appears when the limit is getting close.
                        if (descriptionTooLong) {
                            Text(
                                stringResource(R.string.transaction_form_error_description_long),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else if (state.description.length > TransactionDraftValidator.DESCRIPTION_MAX - 100) {
                            Text(
                                "${state.description.length}/${TransactionDraftValidator.DESCRIPTION_MAX}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            TagsCard(
                state = state,
                onToggleTag = viewModel::onToggleTag,
                onAddPendingTag = viewModel::onAddPendingTag,
                onRemovePendingTag = viewModel::onRemovePendingTag,
            )
            }

            Spacer(Modifier.height(4.dp))
        }

        DateTimePickerFlow(
            open = pickDateTime,
            date = state.date,
            time = state.time,
            maxDate = LocalDate.now(),
            onDismiss = { pickDateTime = false },
            onPicked = { date, time ->
                viewModel.onDateChange(date)
                viewModel.onTimeChange(time)
                pickDateTime = false
            },
        )

        if (pickAccount) {
            ModalBottomSheet(onDismissRequest = { pickAccount = false }) {
                AccountPickerContent(
                    accounts = state.accounts,
                    selectedAccountId = state.selectedAccountId,
                    onSelect = {
                        viewModel.onAccountChange(it)
                        pickAccount = false
                    },
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
        }

        if (pickCategory) {
            ModalBottomSheet(onDismissRequest = { pickCategory = false }) {
                CategoryTreePickerContent(
                    tree = tree,
                    type = categoryType,
                    mode = PickerMode.Assign(),
                    selectedId = state.selectedCategoryId,
                    onSelect = {
                        viewModel.onCategoryChange(it)
                        pickCategory = false
                    },
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                )
            }
        }

        state.reassignmentInfo?.let { info ->
            AlertDialog(
                onDismissRequest = viewModel::onDismissReassignment,
                title = { Text(stringResource(R.string.currency_change_dialog_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.currency_change_dialog_body,
                            info.oldFormatted,
                            info.newFormatted,
                            info.oldAccountName,
                            if (info.isExpense) "+${info.oldFormatted}" else "−${info.oldFormatted}",
                            info.newAccountName,
                            if (info.isExpense) "−${info.newFormatted}" else "+${info.newFormatted}",
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::onConfirmCurrencyChange) {
                        Text(stringResource(R.string.currency_change_dialog_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::onDismissReassignment) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        if (state.showOpeningOrderDialog) {
            AlertDialog(
                onDismissRequest = viewModel::onDismissOpeningOrder,
                title = { Text(stringResource(R.string.account_form_initial_date)) },
                text = { Text(stringResource(R.string.transaction_form_opening_order_body)) },
                confirmButton = {
                    TextButton(onClick = viewModel::onConfirmOpeningOrder) {
                        Text(stringResource(R.string.action_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::onDismissOpeningOrder) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        if (state.showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = viewModel::onDismissDelete,
                title = { Text(stringResource(R.string.transaction_form_delete_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.transaction_form_delete_body,
                            state.deleteBalanceChange?.format().orEmpty(),
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::onConfirmDelete) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::onDismissDelete) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Kind tabs
// ---------------------------------------------------------------------------

@Composable
private fun KindTabs(
    kind: TransactionKind,
    enabled: Boolean,
    onKindChange: (TransactionKind) -> Unit,
    onSelectTransfer: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KindTab(
                label = stringResource(R.string.transaction_kind_expense),
                icon = AppIcons.Income,
                color = MaterialTheme.finance.expense,
                selected = kind == TransactionKind.EXPENSE,
                enabled = enabled,
                onClick = { onKindChange(TransactionKind.EXPENSE) },
            )
            KindTab(
                label = stringResource(R.string.transaction_kind_income),
                icon = AppIcons.Expense,
                color = MaterialTheme.finance.income,
                selected = kind == TransactionKind.INCOME,
                enabled = enabled,
                onClick = { onKindChange(TransactionKind.INCOME) },
            )
            if (onSelectTransfer != null) {
                KindTab(
                    label = stringResource(R.string.transaction_kind_transfer),
                    icon = AppIcons.Transfer,
                    color = MaterialTheme.colorScheme.primary,
                    selected = false,
                    enabled = enabled,
                    onClick = onSelectTransfer,
                )
            }
        }
    }
}

@Composable
private fun RowScope.KindTab(
    label: String,
    icon: ImageVector,
    color: Color,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val content = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        selected = selected,
        onClick = onClick,
        // In edit mode the kind is fixed: the selected tab stays readable, the others fade.
        enabled = enabled && !selected,
        shape = RoundedCornerShape(50),
        color = if (selected) color.copy(alpha = 0.16f) else Color.Transparent,
        contentColor = content,
        modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .alpha(if (!enabled && !selected) 0.38f else 1f),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize.Small))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        }
    }
}

// ---------------------------------------------------------------------------
// Amount hero + keypad
// ---------------------------------------------------------------------------

@Composable
private fun AmountCard(
    amountText: String,
    amountDisplay: String,
    sign: String,
    signColor: Color,
    currencyScale: Int,
    currencySymbol: String,
    accountLabel: String?,
    errorText: String?,
    decimalSeparator: Char,
    onAccountClick: () -> Unit,
    onAmountChange: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val changeAccount = stringResource(R.string.transaction_form_change_account)
            Surface(
                onClick = onAccountClick,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.semantics { contentDescription = "${accountLabel.orEmpty()}. $changeAccount" },
            ) {
                Row(
                    Modifier.padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        accountLabel ?: stringResource(R.string.transaction_form_account),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        AppIcons.Dropdown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            AmountDisplay(
                sign = sign,
                signColor = signColor,
                amountDisplay = amountDisplay,
                isError = errorText != null,
                isEmpty = amountText.isEmpty(),
            )
            if (errorText != null) {
                Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(10, 25, 50).forEach { inc ->
                    SmallPill(text = "+$currencySymbol$inc", onClick = { onAmountChange(AmountKeypad.add(amountText, inc)) })
                }
                if (amountText.isNotEmpty()) {
                    val clear = stringResource(R.string.transaction_form_clear_amount)
                    SmallPill(onClick = { onAmountChange("") }, modifier = Modifier.semantics { contentDescription = clear }) {
                        Icon(AppIcons.Close, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                    }
                }
            }

            NumberPad(
                decimalLabel = decimalSeparator.toString(),
                decimalEnabled = currencyScale > 0,
                onKey = { onAmountChange(AmountKeypad.applyKey(amountText, it, currencyScale)) },
                onClear = { onAmountChange("") },
            )
        }
    }
}

@Composable
private fun AmountDisplay(sign: String, signColor: Color, amountDisplay: String, isError: Boolean, isEmpty: Boolean) {
    val amountColor = when {
        isError -> MaterialTheme.colorScheme.error
        isEmpty -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val caretAlpha by rememberInfiniteTransition(label = "caret").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse),
        label = "caretAlpha",
    )
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = signColor)) { append(sign) }
        append(" ")
        withStyle(SpanStyle(color = amountColor)) { append(amountDisplay) }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        BasicText(
            text = text,
            maxLines = 1,
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
                letterSpacing = (-1).sp,
            ),
            autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 48.sp),
            modifier = Modifier.weight(1f, fill = false),
        )
        Box(
            Modifier
                .padding(start = 4.dp)
                .width(2.dp)
                .height(40.dp)
                .alpha(caretAlpha)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberPad(
    decimalLabel: String,
    decimalEnabled: Boolean,
    onKey: (String) -> Unit,
    onClear: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val backspaceLabel = stringResource(R.string.transaction_form_backspace)
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", AmountKeypad.DECIMAL, "0", AmountKeypad.BACKSPACE)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    val isDecimal = key == AmountKeypad.DECIMAL
                    val isBackspace = key == AmountKeypad.BACKSPACE
                    val keyEnabled = !isDecimal || decimalEnabled
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (keyEnabled) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                            )
                            .combinedClickable(
                                enabled = keyEnabled,
                                role = Role.Button,
                                onClickLabel = if (isBackspace) backspaceLabel else null,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                    onKey(key)
                                },
                                // Long-press on backspace clears the whole amount.
                                onLongClick = if (isBackspace) {
                                    {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onClear()
                                    }
                                } else {
                                    null
                                },
                            )
                            .semantics { if (isBackspace) contentDescription = backspaceLabel },
                    ) {
                        when {
                            isBackspace -> Icon(AppIcons.Backspace, contentDescription = null)
                            isDecimal && !decimalEnabled -> Unit
                            else -> Text(
                                if (isDecimal) decimalLabel else key,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tags
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TagsCard(
    state: TransactionEditUiState,
    onToggleTag: (Long) -> Unit,
    onAddPendingTag: (String) -> Unit,
    onRemovePendingTag: (String) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    SectionCard(shape = GroupBottomShape) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                AppIcons.Tag,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.transaction_form_labels_tags),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (editing) {
                TextButton(onClick = { editing = false }) { Text(stringResource(R.string.transaction_form_tags_done)) }
            } else {
                Text(
                    stringResource(R.string.transaction_form_tags_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (editing) {
            TagPicker(
                allTags = state.allTags,
                selectedTagIds = state.selectedTagIds,
                pendingTagNames = state.pendingTagNames,
                onToggleTag = onToggleTag,
                onAddPendingTag = onAddPendingTag,
                onRemovePendingTag = onRemovePendingTag,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.allTags.filter { it.id in state.selectedTagIds }.forEach { tag ->
                    TagChip(tag = tag, onRemove = { onToggleTag(tag.id) })
                }
                state.pendingTagNames.forEach { name ->
                    InputChip(
                        selected = true,
                        onClick = {},
                        label = { Text(stringResource(R.string.tag_picker_new_suffix, name)) },
                        trailingIcon = {
                            IconButton(onClick = { onRemovePendingTag(name) }, modifier = Modifier.size(16.dp)) {
                                Icon(AppIcons.Close, contentDescription = stringResource(R.string.action_remove))
                            }
                        },
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { editing = true },
                    label = { Text(stringResource(R.string.transaction_form_add_tag)) },
                    leadingIcon = { Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(IconSize.Small)) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Reusable pieces
// ---------------------------------------------------------------------------

@Composable
private fun SectionCard(
    shape: Shape,
    onClick: (() -> Unit)? = null,
    isError: Boolean = false,
    content: @Composable () -> Unit,
) {
    // One Surface with a stable structure: the click and error outline are plain modifiers/params,
    // so toggling them never changes the composition shape.
    val errorColor = MaterialTheme.colorScheme.error
    val clickModifier = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (isError) BorderStroke(1.dp, errorColor) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(clickModifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun LeadingIcon(icon: ImageVector) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(IconSize.Default))
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun SmallPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.height(32.dp),
    ) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            if (text != null) Text(text, style = MaterialTheme.typography.labelLarge)
            icon?.invoke()
        }
    }
}

@Composable
private fun dateLabel(date: LocalDate, today: LocalDate): String {
    val formatted = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    return when (date) {
        today -> "${stringResource(R.string.transactions_day_today)}, $formatted"
        today.minusDays(1) -> "${stringResource(R.string.transactions_day_yesterday)}, $formatted"
        else -> formatted
    }
}

/** Money with the symbol in front and locale digit grouping, matching the amount hero. */
private fun formatMoney(money: Money, symbols: DecimalFormatSymbols): String {
    val plain = money.toPlainString()
    val negative = plain.startsWith("-")
    val body = AmountKeypad.format(plain.removePrefix("-"), symbols.groupingSeparator, symbols.decimalSeparator)
    return (if (negative) MINUS else "") + money.currency.symbol + body
}
