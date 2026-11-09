package com.yehor.finora.ui.accounts

import com.yehor.finora.ui.common.AppIcons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.account.AccountDraftError
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.account.OpeningDatePolicy
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.ui.common.ColorPicker
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.IconKeys
import com.yehor.finora.ui.common.IconPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditScreen(
    onBack: () -> Unit,
    viewModel: AccountEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    val policy = remember { viewModel.policy() }
    val now = remember { System.currentTimeMillis() }
    val errors = AccountFormLogic.errors(state)
    val dateTooLate = AccountFormLogic.dateTooLate(state, policy, now)
    val openingEnabled = AccountFormLogic.openingSectionEnabled(state)

    var showCurrencies by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var typeMenu by rememberSaveable { mutableStateOf(false) }

    Scaffold(
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
                actions = {
                    TextButton(onClick = viewModel::save, enabled = AccountFormLogic.canSave(state, policy, now)) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = { Text(stringResource(R.string.account_form_name)) },
                singleLine = true,
                isError = AccountDraftError.NAME_EMPTY in errors && state.name.isNotEmpty() || AccountDraftError.NAME_TOO_LONG in errors,
                supportingText = {
                    when {
                        AccountDraftError.NAME_TOO_LONG in errors -> Text(stringResource(R.string.account_form_error_name_long))
                        AccountDraftError.NAME_EMPTY in errors && state.name.isNotEmpty() ->
                            Text(stringResource(R.string.account_form_error_name_empty))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // Type
            Column {
                Text(stringResource(R.string.account_form_type), style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    AccountType.entries.forEach { t ->
                        FilterChip(
                            selected = state.type == t,
                            onClick = { viewModel.onType(t) },
                            label = { Text(stringResource(t.labelRes())) },
                        )
                    }
                }
            }

            // Currency
            // A text field consumes taps itself, so an overlay captures the click that opens the dialog.
            Box {
                OutlinedTextField(
                    value = state.currency?.let { "${it.code} (${it.name})" }.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    enabled = !state.isEdit,
                    label = { Text(stringResource(R.string.account_form_currency)) },
                    supportingText = if (state.isEdit) {
                        { Text(stringResource(R.string.account_form_currency_locked)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!state.isEdit) {
                    Box(Modifier.matchParentSize().clickable { showCurrencies = true })
                }
            }

            IconPicker(IconKeys.accountGroups, state.icon, state.color, ColorPalette.ACCOUNT_DEFAULT, viewModel::onIcon)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.picker_color), style = MaterialTheme.typography.labelMedium)
                ColorPicker(state.color, viewModel::onColor)
            }

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescription,
                label = { Text(stringResource(R.string.account_form_description)) },
                isError = AccountDraftError.DESCRIPTION_TOO_LONG in errors,
                supportingText = {
                    if (AccountDraftError.DESCRIPTION_TOO_LONG in errors) {
                        Text(stringResource(R.string.account_form_error_description_long))
                    }
                },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            // Initial balance
            Text(stringResource(R.string.account_form_initial_balance), style = MaterialTheme.typography.titleSmall)
            if (!openingEnabled) {
                Text(
                    stringResource(R.string.account_form_unarchive_to_add),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val amountError = errors.firstOrNull {
                    it == AccountDraftError.AMOUNT_INVALID || it == AccountDraftError.AMOUNT_TOO_PRECISE ||
                        it == AccountDraftError.AMOUNT_OUT_OF_RANGE
                }
                OutlinedTextField(
                    value = state.amountText,
                    onValueChange = viewModel::onAmount,
                    label = { Text(stringResource(R.string.account_form_initial_balance)) },
                    suffix = { Text(state.currency?.symbol.orEmpty()) },
                    singleLine = true,
                    // Decimal keyboards often lack a minus key, and a negative opening balance is allowed.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    isError = amountError != null,
                    supportingText = {
                        amountError?.let {
                            Text(
                                stringResource(
                                    when (it) {
                                        AccountDraftError.AMOUNT_TOO_PRECISE -> R.string.account_form_error_amount_precise
                                        AccountDraftError.AMOUNT_OUT_OF_RANGE -> R.string.account_form_error_amount_range
                                        else -> R.string.account_form_error_amount_invalid
                                    },
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                // A new account's opening balance is always dated today; the date is editable only later.
                if (state.isEdit) {
                    OutlinedTextField(
                        value = formatDate(state.dateMillis),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.account_form_initial_date)) },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(AppIcons.Calendar, contentDescription = stringResource(R.string.account_form_pick_date))
                            }
                        },
                        isError = dateTooLate || state.dateOrderRejected,
                        supportingText = {
                            val earliest = state.earliestOtherEntryAt
                            when {
                                dateTooLate || state.dateOrderRejected ->
                                    Text(stringResource(R.string.account_form_error_date_order))
                                earliest != null ->
                                    Text(stringResource(R.string.account_form_earliest_helper, formatDate(earliest)))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                    )
                }
                AccountFormLogic.balanceDelta(state)?.let { delta ->
                    Text(
                        stringResource(R.string.account_form_delta_warning, delta.format()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            if (state.saveFailed) {
                Text(
                    stringResource(R.string.account_form_error_generic),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (showCurrencies) {
        CurrencyDialog(
            onSelect = { viewModel.onCurrency(it); showCurrencies = false },
            onDismiss = { showCurrencies = false },
        )
    }
    if (showDatePicker) {
        OpeningDateDialog(
            initialMillis = state.dateMillis,
            policy = policy,
            latestAllowed = policy.latestAllowed(now, state.earliestOtherEntryAt),
            onSelect = { viewModel.onDate(it); showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }
}

@Composable
private fun CurrencyDialog(onSelect: (Currency) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) CurrencyRegistry.all
        else CurrencyRegistry.all.filter { it.code.contains(q, true) || it.name.contains(q, true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_form_currency)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.account_form_currency_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.padding(top = 8.dp)) {
                    items(matches, key = { it.code }) { c ->
                        ListItem(
                            headlineContent = { Text("${c.code} (${c.name})") },
                            modifier = Modifier.clickable { onSelect(c) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
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
