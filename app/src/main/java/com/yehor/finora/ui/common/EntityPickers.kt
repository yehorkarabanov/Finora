package com.yehor.finora.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yehor.finora.R
import com.yehor.finora.data.repo.AccountRepository
import com.yehor.finora.data.repo.CategoryRepository
import com.yehor.finora.domain.account.PickableAccount
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.CategoryType
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
    Column(modifier) {
        accounts.forEach { pickable ->
            val a = pickable.account
            ListItem(
                leadingContent = { IconBadge(a.icon, a.color, ColorPalette.ACCOUNT_DEFAULT) },
                headlineContent = { Text(a.name) },
                supportingContent = { Text(a.currency.code) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (pickable.isArchivedOriginal) {
                            SuggestionChip(onClick = {}, label = { Text(stringResource(R.string.picker_archived_badge)) })
                        }
                        RadioButton(selected = a.id == selectedAccountId, onClick = null)
                    }
                },
                modifier = Modifier.clickable { onSelect(a.id) },
            )
        }
    }
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

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.action_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (mode is PickerMode.Parent && query.isBlank()) {
                item(key = "no-parent") {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.picker_no_parent)) },
                        trailingContent = { RadioButton(selected = selectedId == null, onClick = null) },
                        modifier = Modifier.clickable { onSelect(null) },
                    )
                }
            }
            if (rows.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.picker_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(rows, key = { it.category.id }) { row ->
                val c = row.category
                CategoryPickerRow(
                    leading = {
                        IconBadge(c.icon, c.color, ColorPalette.defaultFor(c.type), size = PICKER_ICON_SIZE)
                    },
                    headline = if (query.isBlank()) c.name else row.path,
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
                            if (row.selectable) RadioButton(selected = c.id == selectedId, onClick = null)
                        }
                    },
                    modifier = Modifier
                        .padding(start = (row.level * 16).dp)
                        .clickable(enabled = row.selectable) { onSelect(c.id) },
                )
            }
            if (onCreate != null) {
                item(key = "create-category") {
                    val primary = MaterialTheme.colorScheme.primary
                    CategoryPickerRow(
                        leading = {
                            Box(
                                modifier = Modifier.size(PICKER_ICON_SIZE.dp).clip(CircleShape)
                                    .background(primary.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    AppIcons.Add,
                                    contentDescription = null,
                                    tint = primary,
                                    modifier = Modifier.size((PICKER_ICON_SIZE * 0.55f).dp),
                                )
                            }
                        },
                        headline = stringResource(R.string.transaction_form_create_category),
                        headlineColor = primary,
                        modifier = Modifier.clickable { onCreate() },
                    )
                }
            }
        }
    }
}

private const val PICKER_ICON_SIZE = 32

@Composable
private fun CategoryPickerRow(
    leading: @Composable () -> Unit,
    headline: String,
    modifier: Modifier = Modifier,
    headlineColor: Color = Color.Unspecified,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        leadingContent = { Row(verticalAlignment = Alignment.CenterVertically) { leading() } },
        headlineContent = { Text(headline, color = headlineColor) },
        trailingContent = trailing,
        modifier = modifier,
    )
}
