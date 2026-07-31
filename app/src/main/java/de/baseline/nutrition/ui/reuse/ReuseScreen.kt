package de.baseline.nutrition.ui.reuse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import de.baseline.nutrition.R
import de.baseline.nutrition.data.diary.FavoriteDto
import de.baseline.nutrition.data.diary.MealDto
import de.baseline.nutrition.domain.diary.MealEditorDraft
import de.baseline.nutrition.domain.diary.toEditorDraft
import de.baseline.nutrition.ui.theme.BaselineSpacing

@Composable
fun ReuseScreen(
    viewModel: ReuseViewModel,
    mode: ReuseMode,
    selectedDay: String,
    onDraftReady: (MealEditorDraft) -> Unit,
    onEditTemplate: (FavoriteDto) -> Unit,
    onManual: () -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(mode) { viewModel.refresh() }
    LaunchedEffect(state.draft?.clientId) {
        state.draft?.let { draft ->
            onDraftReady(draft.toEditorDraft())
            viewModel.consumeDraft()
        }
    }
    BackHandler(onBack = onClose)
    ReuseContent(
        state = state,
        mode = mode,
        selectedDay = selectedDay,
        onReuseFavorite = viewModel::reuseFavorite,
        onReuseRecent = viewModel::reuseRecent,
        onEditTemplate = onEditTemplate,
        onRename = viewModel::renameFavorite,
        onDelete = viewModel::deleteFavorite,
        onRetry = viewModel::refresh,
        onManual = onManual,
        onClose = onClose,
    )
}

@Composable
fun ReuseContent(
    state: ReuseUiState,
    mode: ReuseMode,
    selectedDay: String,
    onReuseFavorite: (FavoriteDto, String) -> Unit,
    onReuseRecent: (MealDto, String) -> Unit,
    onEditTemplate: (FavoriteDto) -> Unit,
    onRename: (FavoriteDto, String) -> Unit,
    onDelete: (FavoriteDto) -> Unit,
    onRetry: () -> Unit,
    onManual: () -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<FavoriteDto?>(null) }
    var editedName by rememberSaveable { mutableStateOf("") }
    val favorites = state.favorites.filter {
        query.isBlank() || it.displayName.contains(query, ignoreCase = true) ||
            it.meal.name.contains(query, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(BaselineSpacing.large),
        verticalArrangement = Arrangement.spacedBy(BaselineSpacing.medium),
    ) {
        TextButton(onClick = onClose) { Text(stringResource(R.string.back)) }
        Text(
            stringResource(
                if (mode == ReuseMode.Favorites) R.string.favorites else R.string.recent_meals,
            ),
            fontWeight = FontWeight.Bold,
        )
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.testTag("reuse-loading"))
            Text(stringResource(R.string.loading_reuse))
        } else {
            state.errorCode?.let {
                Card(Modifier.fillMaxWidth().testTag("reuse-error")) {
                    Column(
                        Modifier.padding(BaselineSpacing.medium),
                        verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
                    ) {
                        Text(stringResource(R.string.reuse_error))
                        Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
                            OutlinedButton(onClick = onRetry) {
                                Text(stringResource(R.string.retry))
                            }
                            OutlinedButton(onClick = onManual) {
                                Text(stringResource(R.string.manual_entry))
                            }
                        }
                    }
                }
            }
            if (mode == ReuseMode.Favorites) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.search_favorites)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (favorites.isEmpty()) {
                    EmptyReuse(onManual, "favorites-empty")
                } else {
                    favorites.forEach { favorite ->
                        FavoriteCard(
                            favorite = favorite,
                            busy = state.activeRequest == favorite.id,
                            onReuse = { onReuseFavorite(favorite, selectedDay) },
                            onEditTemplate = { onEditTemplate(favorite) },
                            onRename = {
                                editing = favorite
                                editedName = favorite.displayName
                            },
                            onDelete = { onDelete(favorite) },
                        )
                    }
                }
            } else if (state.recent.isEmpty()) {
                EmptyReuse(onManual, "recent-empty")
            } else {
                state.recent.forEach { meal ->
                    RecentCard(
                        meal = meal,
                        busy = state.activeRequest == meal.id,
                        onReuse = { onReuseRecent(meal, selectedDay) },
                    )
                }
            }
        }
    }
    editing?.let { favorite ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.edit_template)) },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text(stringResource(R.string.favorite_display_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = editedName.isNotBlank(),
                    onClick = {
                        onRename(favorite, editedName)
                        editing = null
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun FavoriteCard(
    favorite: FavoriteDto,
    busy: Boolean,
    onReuse: () -> Unit,
    onEditTemplate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("favorite-${favorite.id}"),
    ) {
        Column(
            Modifier.padding(BaselineSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
        ) {
            Text(favorite.displayName, fontWeight = FontWeight.Bold)
            Text(favorite.meal.name)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
            ) {
                Button(
                    enabled = !busy,
                    onClick = onReuse,
                    modifier = Modifier.testTag("favorite-reuse-${favorite.id}"),
                ) {
                    Text(stringResource(R.string.add_again))
                }
                TextButton(
                    onClick = onEditTemplate,
                    modifier = Modifier.testTag("favorite-edit-template-${favorite.id}"),
                ) {
                    Text(stringResource(R.string.edit_template))
                }
                TextButton(onClick = onRename) {
                    Text(stringResource(R.string.rename_template))
                }
                TextButton(enabled = !busy, onClick = onDelete) {
                    Text(stringResource(R.string.remove_favorite))
                }
            }
        }
    }
}

@Composable
private fun RecentCard(meal: MealDto, busy: Boolean, onReuse: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("recent-${meal.id}"),
    ) {
        Column(
            Modifier.padding(BaselineSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
        ) {
            Text(meal.name, fontWeight = FontWeight.Bold)
            Text("${meal.localDay} · ${meal.mealType}")
            Button(
                enabled = !busy,
                onClick = onReuse,
                modifier = Modifier.testTag("recent-reuse-${meal.id}"),
            ) {
                Text(stringResource(R.string.add_again))
            }
        }
    }
}

@Composable
private fun EmptyReuse(onManual: () -> Unit, tag: String) {
    Card(Modifier.fillMaxWidth().testTag(tag)) {
        Column(
            Modifier.padding(BaselineSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
        ) {
            Text(stringResource(R.string.reuse_empty))
            OutlinedButton(onClick = onManual) { Text(stringResource(R.string.manual_entry)) }
        }
    }
}
