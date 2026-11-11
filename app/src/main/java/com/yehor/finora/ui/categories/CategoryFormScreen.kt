package com.yehor.finora.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.component.list.SectionHeader
import com.yehor.finora.designsystem.component.sheet.FinoraSheet
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.icon.IconKeys
import com.yehor.finora.designsystem.theme.PreviewTheme
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.category.CategoryRules.ParentCheck
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.ui.common.CategoryTreePicker
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.ColorPicker
import com.yehor.finora.ui.common.FieldNote
import com.yehor.finora.ui.common.FormCtaBar
import com.yehor.finora.ui.common.FormScreen
import com.yehor.finora.ui.common.FormTextRow
import com.yehor.finora.ui.common.IconBadge
import com.yehor.finora.ui.common.IconPicker
import com.yehor.finora.ui.common.PickerMode
import com.yehor.finora.ui.common.errorOutline
import com.yehor.finora.ui.operations.ShelfDivider
import com.yehor.finora.ui.operations.ShelfIcon
import com.yehor.finora.ui.operations.ShelfRow
import com.yehor.finora.ui.operations.ShelfValue

@Composable
fun CategoryEditScreen(
    onBack: () -> Unit,
    onCreated: ((Long) -> Unit)? = null,
    viewModel: CategoryEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) {
            val id = state.createdCategoryId
            if (id != null && onCreated != null) onCreated(id) else onBack()
        }
    }
    var pickParent by rememberSaveable { mutableStateOf(false) }

    CategoryFormContent(
        state = state,
        onBack = onBack,
        onName = viewModel::onName,
        onIcon = viewModel::onIcon,
        onColor = viewModel::onColor,
        onPickParent = { pickParent = true },
        onSave = viewModel::save,
    )

    if (pickParent) {
        FinoraSheet(onDismiss = { pickParent = false }) {
            CategoryTreePicker(
                type = state.type,
                mode = PickerMode.Parent(movingId = state.categoryId),
                selectedId = state.parentId,
                onSelect = { viewModel.onParent(it); pickParent = false },
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
internal fun CategoryFormContent(
    state: CategoryFormState,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onIcon: (String) -> Unit,
    onColor: (Long) -> Unit,
    onPickParent: () -> Unit,
    onSave: () -> Unit,
) {
    val rejection = state.parentRejected ?: state.parentCheck.takeIf { it != ParentCheck.OK }
    val parentName = state.parentId?.let { state.tree.category(it)?.name }
    val cta = CategoryFormLogic.ctaState(state.isEdit, state.nameError, state.parentCheck)
    val ctaLabel = stringResource(
        when (cta) {
            CategoryFormLogic.CtaState.CREATE -> R.string.category_cta_create
            CategoryFormLogic.CtaState.SAVE -> R.string.transaction_form_cta_save
            CategoryFormLogic.CtaState.NAME_MISSING -> R.string.category_cta_name_missing
            CategoryFormLogic.CtaState.NAME_TOO_LONG -> R.string.category_cta_name_long
            CategoryFormLogic.CtaState.PARENT_INVALID -> R.string.category_cta_parent_invalid
        },
    )

    FormScreen(
        title = stringResource(if (state.isEdit) R.string.category_form_edit_title else R.string.category_form_new_title),
        onBack = onBack,
        bottomBar = {
            FormCtaBar(
                label = ctaLabel,
                enabled = state.canSave,
                onClick = onSave,
                error = if (state.saveFailed) stringResource(R.string.category_form_error_generic) else null,
            )
        },
    ) {
        NameGroup(state, onName)

        SectionHeader(stringResource(R.string.category_form_parent), Modifier.padding(top = 8.dp))
        GroupedCard(Modifier.fillMaxWidth().errorOutline(rejection != null)) {
            val parent = state.parentId?.let { state.tree.category(it) }
            ShelfRow(
                icon = {
                    if (parent != null) {
                        IconBadge(parent.icon, parent.color, ColorPalette.defaultFor(parent.type), size = 34)
                    } else {
                        ShelfIcon(AppIcons.Category)
                    }
                },
                label = stringResource(R.string.category_form_parent_title),
                onClick = onPickParent,
                supporting = rejection?.let {
                    stringResource(
                        when (it) {
                            ParentCheck.SELF -> R.string.category_form_error_self
                            ParentCheck.CYCLE -> R.string.category_form_error_cycle
                            ParentCheck.TYPE_MISMATCH -> R.string.category_form_error_type
                            ParentCheck.TOO_DEEP -> R.string.category_form_error_deep
                            ParentCheck.OK -> R.string.category_form_error_generic
                        },
                    )
                },
                supportingIsError = true,
                trailing = {
                    ShelfValue(parentName ?: stringResource(R.string.picker_no_parent), muted = parentName == null)
                    Icon(AppIcons.Next, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
            )
        }

        SectionHeader(stringResource(R.string.picker_icon) + " · " + stringResource(R.string.picker_color), Modifier.padding(top = 8.dp))
        GroupedCard(Modifier.fillMaxWidth()) {
            IconPicker(IconKeys.categoryGroups, state.icon, state.color, ColorPalette.defaultFor(state.type), onIcon)
            ShelfDivider()
            Column(Modifier.padding(14.dp)) {
                Text(
                    stringResource(R.string.picker_color),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp).semantics { heading() },
                )
                ColorPicker(state.color, onColor)
            }
        }
    }
}

/** Name with the live icon badge and its counter, then the fixed type row. */
@Composable
private fun NameGroup(state: CategoryFormState, onName: (String) -> Unit) {
    val error = state.nameError
    val showError = error == CategoryFormLogic.NameError.TOO_LONG ||
        (error == CategoryFormLogic.NameError.EMPTY && state.name.isNotEmpty())
    GroupedCard(Modifier.fillMaxWidth().errorOutline(showError)) {
        FormTextRow(
            icon = { IconBadge(state.icon, state.color, ColorPalette.defaultFor(state.type), size = 40) },
            label = stringResource(R.string.category_form_name),
            value = state.name,
            onValueChange = onName,
            hint = stringResource(R.string.category_form_name),
            textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            notes = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    FieldNote(
                        when {
                            error == CategoryFormLogic.NameError.TOO_LONG -> stringResource(R.string.category_form_error_name_long)
                            showError -> stringResource(R.string.category_form_error_name_empty)
                            else -> ""
                        },
                        isError = true,
                        modifier = Modifier.weight(1f),
                    )
                    FieldNote(
                        stringResource(R.string.category_form_name_counter, state.name.trim().length, CategoryFormLogic.NAME_MAX),
                        isError = error == CategoryFormLogic.NameError.TOO_LONG,
                    )
                }
            },
        )
        ShelfDivider()
        ShelfRow(
            icon = { ShelfIcon(AppIcons.Tag) },
            label = stringResource(R.string.category_form_type, stringResource(state.type.labelRes())),
            onClick = null,
            trailing = { CategoryPill(stringResource(state.type.labelRes()), state.type.kindColor()) },
        )
    }
}

// ---- Previews ----

private fun previewFormState(name: String, parentId: Long? = 1L, saveFailed: Boolean = false) = CategoryFormState(
    categoryId = null,
    loaded = true,
    name = name,
    type = CategoryType.EXPENSE,
    icon = "coffee",
    color = ColorPalette.colors[4],
    parentId = parentId,
    tree = CategoryTree(listOf(Category(1, "Food", "restaurant", null, CategoryType.EXPENSE, null, 0))),
    saveFailed = saveFailed,
)

@Composable
private fun FormPreview(dark: Boolean, state: CategoryFormState) {
    PreviewTheme(dark) {
        CategoryFormContent(state, {}, {}, {}, {}, {}, {})
    }
}

@Preview(showBackground = true, heightDp = 800, name = "Category form light")
@Composable
private fun FormPreviewLight() = FormPreview(dark = false, state = previewFormState("Cafes"))

@Preview(showBackground = true, backgroundColor = 0xFF0F1412, heightDp = 800, name = "Category form dark")
@Composable
private fun FormPreviewDark() = FormPreview(dark = true, state = previewFormState("Cafes", saveFailed = true))

@Preview(showBackground = true, heightDp = 800, name = "Category form empty light")
@Composable
private fun FormEmptyPreviewLight() = FormPreview(dark = false, state = previewFormState("", parentId = null))

@Preview(showBackground = true, backgroundColor = 0xFF0F1412, heightDp = 800, name = "Category form too long dark")
@Composable
private fun FormLongPreviewDark() = FormPreview(dark = true, state = previewFormState("x".repeat(55)))
