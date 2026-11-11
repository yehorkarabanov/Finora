package com.yehor.finora.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.category.CategoryRules.ParentCheck
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.ui.common.CategoryTreePicker
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.ColorPicker
import com.yehor.finora.ui.common.IconBadge
import com.yehor.finora.designsystem.icon.IconKeys
import com.yehor.finora.ui.common.IconPicker
import com.yehor.finora.designsystem.icon.IconSize
import com.yehor.finora.ui.common.PickerMode
import com.yehor.finora.designsystem.theme.PreviewTheme

@OptIn(ExperimentalMaterial3Api::class)
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
        ModalBottomSheet(onDismissRequest = { pickParent = false }) {
            CategoryTreePicker(
                type = state.type,
                mode = PickerMode.Parent(movingId = state.categoryId),
                selectedId = state.parentId,
                onSelect = { viewModel.onParent(it); pickParent = false },
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val blocked = cta != CategoryFormLogic.CtaState.CREATE && cta != CategoryFormLogic.CtaState.SAVE

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isEdit) R.string.category_form_edit_title else R.string.category_form_new_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.saveFailed) {
                        Text(
                            stringResource(R.string.category_form_error_generic),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Button(
                        onClick = onSave,
                        enabled = state.canSave,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                    ) {
                        if (!blocked) {
                            Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(ctaLabel, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
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
            HeroCard(state, onName)

            CategorySectionCard(
                shape = RoundedCornerShape(20.dp),
                onClick = onPickParent,
                isError = rejection != null,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val parent = state.parentId?.let { state.tree.category(it) }
                    if (parent != null) {
                        IconBadge(parent.icon, parent.color, ColorPalette.defaultFor(parent.type), size = 44)
                    } else {
                        CategoryLeadingIcon(AppIcons.Category)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.category_form_parent_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            parentName ?: stringResource(R.string.picker_no_parent),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(AppIcons.Next, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                rejection?.let {
                    Text(
                        stringResource(
                            when (it) {
                                ParentCheck.SELF -> R.string.category_form_error_self
                                ParentCheck.CYCLE -> R.string.category_form_error_cycle
                                ParentCheck.TYPE_MISMATCH -> R.string.category_form_error_type
                                ParentCheck.TOO_DEEP -> R.string.category_form_error_deep
                                ParentCheck.OK -> R.string.category_form_error_generic
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            IconPicker(IconKeys.categoryGroups, state.icon, state.color, ColorPalette.defaultFor(state.type), onIcon)

            CategorySectionCard(shape = RoundedCornerShape(20.dp)) {
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

@Composable
private fun HeroCard(state: CategoryFormState, onName: (String) -> Unit) {
    val error = state.nameError
    val showError = error == CategoryFormLogic.NameError.TOO_LONG ||
        (error == CategoryFormLogic.NameError.EMPTY && state.name.isNotEmpty())
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(state.icon, state.color, ColorPalette.defaultFor(state.type), size = 72)
            CategoryPill(stringResource(R.string.category_form_type, stringResource(state.type.labelRes())), state.type.kindColor())
            TextField(
                value = state.name,
                onValueChange = onName,
                placeholder = {
                    Text(
                        stringResource(R.string.category_form_name),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                singleLine = true,
                isError = showError,
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    errorIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    when {
                        error == CategoryFormLogic.NameError.TOO_LONG -> stringResource(R.string.category_form_error_name_long)
                        showError -> stringResource(R.string.category_form_error_name_empty)
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.category_form_name_counter, state.name.trim().length, CategoryFormLogic.NAME_MAX),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error == CategoryFormLogic.NameError.TOO_LONG) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
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
