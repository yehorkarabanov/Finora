package com.yehor.finora.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.account.AccountDraftError
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.account.OpeningDatePolicy
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.ColorPicker
import com.yehor.finora.ui.common.IconBadge
import com.yehor.finora.designsystem.icon.IconKeys
import com.yehor.finora.ui.common.IconPicker
import com.yehor.finora.designsystem.icon.IconSize
import com.yehor.finora.designsystem.theme.PreviewTheme
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

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isEdit) R.string.account_form_edit_title else R.string.account_form_new_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        bottomBar = {
            Column {
                if (state.saveFailed) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(AppIcons.Warning, contentDescription = null, modifier = Modifier.size(IconSize.Default))
                            Text(stringResource(R.string.account_form_error_generic), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                AccountBottomCta(
                    label = ctaLabel(state.isEdit, blocker),
                    enabled = blocker == null,
                    onClick = onSave,
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeroCard(
                state = state,
                nameError = when {
                    AccountDraftError.NAME_TOO_LONG in errors -> R.string.account_form_error_name_long
                    nameTouched && AccountDraftError.NAME_EMPTY in errors -> R.string.account_form_error_name_empty
                    else -> null
                },
                onName = { nameTouched = true; onName(it) },
                onCurrencyClick = { showCurrencies = true },
            )

            TypeTrack(selected = state.type, onSelect = onType)

            OpeningGroup(
                state = state,
                enabled = openingEnabled,
                errors = errors,
                dateTooLate = dateTooLate,
                onAmount = onAmount,
                onDateClick = { showDatePicker = true },
            )

            IconPicker(IconKeys.accountGroups, state.icon, state.color, ColorPalette.ACCOUNT_DEFAULT, onIcon)
            AccountSectionCard(shape = groupShape(0, 1)) {
                Text(
                    stringResource(R.string.picker_color),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                ColorPicker(state.color, onColor)
            }

            DescriptionCard(
                value = state.description,
                isError = AccountDraftError.DESCRIPTION_TOO_LONG in errors,
                onValueChange = onDescription,
            )
        }
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

@Composable
private fun borderlessFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
)

@Composable
private fun HeroCard(
    state: AccountFormState,
    nameError: Int?,
    onName: (String) -> Unit,
    onCurrencyClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconBadge(state.icon, state.color, ColorPalette.ACCOUNT_DEFAULT, size = 64)
            val nameStyle = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            TextField(
                value = state.name,
                onValueChange = onName,
                singleLine = true,
                textStyle = nameStyle,
                placeholder = {
                    Text(
                        stringResource(R.string.account_form_name_placeholder),
                        style = nameStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                isError = nameError != null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                colors = borderlessFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (nameError != null) {
                Text(
                    stringResource(nameError),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            val currency = state.currency
            val pillText = currency?.let { "${it.code} · ${it.name}" } ?: stringResource(R.string.account_form_currency_choose)
            Surface(
                onClick = onCurrencyClick,
                enabled = !state.isEdit,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp).heightIn(min = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.isEdit) {
                        Icon(AppIcons.Lock, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        pillText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!state.isEdit) {
                        Icon(AppIcons.Dropdown, contentDescription = null, modifier = Modifier.size(IconSize.Medium))
                    }
                }
            }
            if (state.isEdit) {
                Text(
                    stringResource(R.string.account_form_currency_locked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Account type as a scrollable segmented track, styled like the transaction kind tabs. */
@Composable
private fun TypeTrack(selected: AccountType, onSelect: (AccountType) -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(4.dp)) {
            AccountType.entries.forEach { type ->
                val isSelected = type == selected
                Box(
                    Modifier
                        .heightIn(min = 44.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(type) })
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(type.labelRes()),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
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
    // A new account's opening balance is always dated today; the date is editable only later.
    val showDate = enabled && state.isEdit
    val count = if (showDate) 2 else 1
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (!enabled) {
            AccountSectionCard(shape = groupShape(0, 1)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccountLeadingIcon(AppIcons.Income)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.account_form_initial_balance), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.account_form_unarchive_to_add),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            return@Column
        }
        val amountError = errors.firstOrNull { it in AMOUNT_ERROR_SET }
        AccountSectionCard(shape = groupShape(0, count), isError = amountError != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccountLeadingIcon(AppIcons.Income)
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.account_form_initial_balance),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextField(
                        value = state.amountText,
                        onValueChange = onAmount,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum",
                        ),
                        placeholder = {
                            Text(
                                "0",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        suffix = { Text(state.currency?.symbol.orEmpty(), style = MaterialTheme.typography.titleMedium) },
                        isError = amountError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        colors = borderlessFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val negative = state.amountText.trim().startsWith("-")
                val signToggleLabel = stringResource(R.string.account_form_sign_toggle)
                AccountSmallPill(
                    onClick = { onAmount(AccountFormLogic.toggleSign(state.amountText)) },
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .semantics { contentDescription = signToggleLabel },
                    text = if (negative) "−" else "+",
                )
            }
            amountError?.let {
                Text(
                    stringResource(
                        when (it) {
                            AccountDraftError.AMOUNT_TOO_PRECISE -> R.string.account_form_error_amount_precise
                            AccountDraftError.AMOUNT_OUT_OF_RANGE -> R.string.account_form_error_amount_range
                            else -> R.string.account_form_error_amount_invalid
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            AccountFormLogic.balanceDelta(state)?.let { delta ->
                Text(
                    stringResource(R.string.account_form_delta_warning, delta.format()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        if (showDate) {
            val dateError = dateTooLate || state.dateOrderRejected
            val earliest = state.earliestOtherEntryAt
            AccountSectionCard(shape = groupShape(1, count), onClick = onDateClick, isError = dateError) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccountLeadingIcon(AppIcons.Calendar)
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.account_form_initial_date),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            formatDate(state.dateMillis),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Icon(AppIcons.Next, contentDescription = stringResource(R.string.account_form_pick_date), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    dateError -> Text(
                        stringResource(R.string.account_form_error_date_order),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    earliest != null -> Text(
                        stringResource(R.string.account_form_earliest_helper, formatDate(earliest)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
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
    AccountSectionCard(shape = groupShape(0, 1), isError = isError) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountLeadingIcon(AppIcons.Edit)
            Column(Modifier.weight(1f)) {
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = { Text(stringResource(R.string.account_form_description)) },
                    minLines = 2,
                    isError = isError,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = borderlessFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isError) {
                    Text(
                        stringResource(R.string.account_form_error_description_long),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencySheet(selected: Currency?, onSelect: (Currency) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) CurrencyRegistry.all
        else CurrencyRegistry.all.filter { it.code.contains(q, true) || it.name.contains(q, true) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxHeight(0.9f)) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.account_form_currency_search)) },
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(AppIcons.Close, contentDescription = stringResource(R.string.action_remove))
                        }
                    }
                } else null,
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
            )
            LazyColumn(Modifier.weight(1f)) {
                items(matches, key = { it.code }) { c ->
                    val isSelected = c.code == selected?.code
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(c) })
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(c.code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            c.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

/** The Material picker works in UTC midnights; [OpeningDatePolicy] maps to and from local day starts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpeningDateDialog(
    initialMillis: Long,
    policy: OpeningDatePolicy,
    latestAllowed: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val latestUtc = policy.toPickerUtcMidnight(latestAllowed)
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = policy.toPickerUtcMidnight(initialMillis).coerceAtMost(latestUtc),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= latestUtc
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { pickerState.selectedDateMillis?.let { onSelect(policy.fromPickerUtcMidnight(it)) } },
                enabled = pickerState.selectedDateMillis != null,
            ) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = pickerState)
    }
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
