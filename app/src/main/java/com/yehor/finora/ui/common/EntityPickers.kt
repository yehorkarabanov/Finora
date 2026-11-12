package com.yehor.finora.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yehor.finora.R
import com.yehor.finora.data.repo.AccountRepository
import com.yehor.finora.data.repo.CategoryRepository
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.component.list.ListRow
import com.yehor.finora.designsystem.component.list.RoundIcon
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.domain.account.PickableAccount
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.ui.operations.FilterSearchField
import com.yehor.finora.ui.operations.ShelfDivider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// ---- Account picker ----

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountPickerViewModel @Inject constructor(
    private val repository: AccountRepository,
) : ViewModel() {
    private val original = MutableStateFlow<Long?>(null)

    val accounts: StateFlow<List<PickableAccount>> = original
        .flatMapLatest { repository.observePickable(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setOriginal(id: Long?) {
        original.value = id
    }
}

/**
 * Lists active accounts, plus [originalAccountId] when it is archived (design 4.7). The archived
 * original carries an "Archived" badge and stays selectable. No other archived account is listed.
 */
@Composable
fun AccountPicker(
    selectedAccountId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    originalAccountId: Long? = null,
    viewModel: AccountPickerViewModel = hiltViewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(originalAccountId) { viewModel.setOriginal(originalAccountId) }
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    AccountPickerContent(accounts, selectedAccountId, onSelect, modifier)
}

@Composable
fun AccountPickerContent(
    accounts: List<PickableAccount>,
    selectedAccountId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupedCard(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        accounts.forEachIndexed { index, pickable ->
            val a = pickable.account
            val isSelected = a.id == selectedAccountId
            if (index > 0) ShelfDivider()
            ListRow(
                icon = { IconBadge(a.icon, a.color, ColorPalette.ACCOUNT_DEFAULT, size = 38) },
                title = a.name,
                subtitle = a.currency.code,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (pickable.isArchivedOriginal) PickerBadge(stringResource(R.string.picker_archived_badge))
                        if (isSelected) SelectedMark()
                    }
                },
                onClick = { onSelect(a.id) },
                modifier = Modifier.semantics { selected = isSelected },
            )
        }
    }
}

/** Small muted pill label (e.g. "Archived"). */
@Composable
private fun PickerBadge(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, PillShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Mint check shown on the chosen row. */
@Composable
private fun SelectedMark() {
    Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
}

// ---- Category tree picker ----

@HiltViewModel
class CategoryTreeViewModel @Inject constructor(
    repository: CategoryRepository,
) : ViewModel() {
    val tree: StateFlow<CategoryTree> = repository.observeAll()
        .map { CategoryTree(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryTree(emptyList()))
}

/** Searchable, expandable tree of the categories of [type]. [onSelect] gets null for "no parent". */
@Composable
fun CategoryTreePicker(
    type: CategoryType,
    mode: PickerMode,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    selectedId: Long? = null,
    viewModel: CategoryTreeViewModel = hiltViewModel(),
) {
    val tree by viewModel.tree.collectAsStateWithLifecycle()
    CategoryTreePickerContent(tree, type, mode, onSelect, modifier, selectedId)
}

@Composable
fun CategoryTreePickerContent(
    tree: CategoryTree,
    type: CategoryType,
    mode: PickerMode,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    selectedId: Long? = null,
    onCreate: (() -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    val rows = CategoryPickerModel.rows(tree, type, mode, expanded.toSet(), query)
    val showNoParent = mode is PickerMode.Parent && query.isBlank()

    Column(modifier) {
        FilterSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.action_search),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        GroupedCard(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            LazyColumn {
                if (showNoParent) {
                    item(key = "no-parent") {
                        ListRow(
                            icon = { RoundIcon(AppIcons.Category, MaterialTheme.colorScheme.onSurfaceVariant, size = 36.dp) },
                            title = stringResource(R.string.picker_no_parent),
                            subtitle = null,
                            trailing = { if (selectedId == null) SelectedMark() },
                            onClick = { onSelect(null) },
                            modifier = Modifier.semantics { selected = selectedId == null },
                        )
                    }
                }
                if (rows.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.picker_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                itemsIndexed(rows, key = { _, row -> row.category.id }) { index, row ->
                    val c = row.category
                    if (index > 0 || showNoParent) ShelfDivider()
                    ListRow(
                        icon = { IconBadge(c.icon, c.color, ColorPalette.defaultFor(c.type), size = PICKER_ICON_SIZE) },
                        title = if (query.isBlank()) c.name else row.path,
                        subtitle = null,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (row.hasChildren && query.isBlank()) {
                                    IconButton(onClick = {
                                        expanded = if (row.expanded) expanded - c.id else expanded + c.id
                                    }) {
                                        Icon(
                                            if (row.expanded) AppIcons.Collapse else AppIcons.Expand,
                                            contentDescription = stringResource(
                                                if (row.expanded) R.string.action_collapse else R.string.action_expand,
                                            ),
                                        )
                                    }
                                }
                                if (row.selectable && c.id == selectedId) SelectedMark()
                            }
                        },
                        modifier = Modifier
                            .padding(start = (row.level * 16).dp)
                            .semantics { selected = c.id == selectedId },
                        onClick = if (row.selectable) ({ onSelect(c.id) }) else null,
                    )
                }
                if (onCreate != null) {
                    item(key = "create-category") {
                        if (rows.isNotEmpty() || showNoParent) ShelfDivider()
                        ListRow(
                            icon = { RoundIcon(AppIcons.Add, MaterialTheme.colorScheme.primary, size = PICKER_ICON_SIZE.dp) },
                            title = stringResource(R.string.transaction_form_create_category),
                            subtitle = null,
                            trailing = {},
                            onClick = onCreate,
                        )
                    }
                }
            }
        }
    }
}

private const val PICKER_ICON_SIZE = 36

// ---- Previews ----

private fun previewAccounts(): List<PickableAccount> {
    val usd = com.yehor.finora.domain.money.CurrencyRegistry.get("USD")
    fun account(id: Long, name: String, icon: String, status: com.yehor.finora.domain.model.AccountStatus) =
        com.yehor.finora.domain.account.Account(
            id, name, usd, com.yehor.finora.domain.account.AccountType.BANK, icon, null, null, status, 0L,
        )
    return listOf(
        PickableAccount(account(1, "Main card", "account_balance", com.yehor.finora.domain.model.AccountStatus.ACTIVE), false),
        PickableAccount(account(2, "Cash", "wallet", com.yehor.finora.domain.model.AccountStatus.ACTIVE), false),
        PickableAccount(account(3, "Old savings", "savings", com.yehor.finora.domain.model.AccountStatus.ARCHIVED), true),
    )
}

private fun previewTree() = CategoryTree(
    listOf(
        com.yehor.finora.domain.category.Category(1, "Food", "restaurant", null, CategoryType.EXPENSE, null, 0),
        com.yehor.finora.domain.category.Category(2, "Groceries", "shopping_cart", null, CategoryType.EXPENSE, 1, 0),
        com.yehor.finora.domain.category.Category(3, "Transport", "directions_bus", null, CategoryType.EXPENSE, null, 0),
    ),
)

@Composable
private fun PickerSheetPreview(dark: Boolean) {
    com.yehor.finora.designsystem.theme.PreviewTheme(dark) {
        androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                AccountPickerContent(previewAccounts(), selectedAccountId = 1L, onSelect = {})
                CategoryTreePickerContent(
                    tree = previewTree(),
                    type = CategoryType.EXPENSE,
                    mode = PickerMode.Parent(),
                    onSelect = {},
                    selectedId = 1L,
                    onCreate = {},
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, heightDp = 900, name = "Account and category pickers light")
@Composable
private fun PickerSheetPreviewLight() = PickerSheetPreview(dark = false)

@androidx.compose.ui.tooling.preview.Preview(
    showBackground = true, backgroundColor = 0xFF0F1412, heightDp = 900, name = "Account and category pickers dark",
)
@Composable
private fun PickerSheetPreviewDark() = PickerSheetPreview(dark = true)
