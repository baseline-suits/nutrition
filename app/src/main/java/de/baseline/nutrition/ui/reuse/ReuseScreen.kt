package de.baseline.nutrition.ui.reuse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrunchDining
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.baseline.nutrition.R
import de.baseline.nutrition.data.diary.FavoriteDto
import de.baseline.nutrition.data.diary.MealDto
import de.baseline.nutrition.data.diary.MealPayload
import de.baseline.nutrition.data.diary.NutrientDto
import de.baseline.nutrition.domain.diary.MealEditorDraft
import de.baseline.nutrition.domain.diary.toEditorDraft
import de.baseline.nutrition.ui.theme.BaselineCard
import de.baseline.nutrition.ui.theme.BaselineShapes
import de.baseline.nutrition.ui.theme.BaselineSpacing
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

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
            .padding(horizontal = BaselineSpacing.screen, vertical = BaselineSpacing.small)
            .testTag("reuse-screen"),
        verticalArrangement = Arrangement.spacedBy(BaselineSpacing.compact),
    ) {
        ReuseTopBar(onClose)
        Text(
            stringResource(if (mode == ReuseMode.Favorites) R.string.favorites else R.string.recent_meals),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            stringResource(
                if (mode == ReuseMode.Favorites) {
                    R.string.favorites_screen_subtitle
                } else {
                    R.string.recent_screen_subtitle
                },
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.loading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(BaselineSpacing.extraLarge),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.testTag("reuse-loading"))
            }
        } else {
            state.errorCode?.let {
                BaselineCard(Modifier.fillMaxWidth().testTag("reuse-error")) {
                    Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
                        Text(stringResource(R.string.reuse_error), color = MaterialTheme.colorScheme.error)
                        Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
                            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                            OutlinedButton(onClick = onManual) {
                                Text(stringResource(R.string.manual_entry))
                            }
                        }
                    }
                }
            }
            if (mode == ReuseMode.Favorites) {
                FavoritesSection(
                    allFavorites = state.favorites,
                    favorites = favorites,
                    query = query,
                    onQuery = { query = it },
                    activeRequest = state.activeRequest,
                    selectedDay = selectedDay,
                    onReuseFavorite = onReuseFavorite,
                    onEditTemplate = onEditTemplate,
                    onRename = { favorite ->
                        editing = favorite
                        editedName = favorite.displayName
                    },
                    onDelete = onDelete,
                    showTitle = false,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                RecentSection(
                    recent = state.recent,
                    activeRequest = state.activeRequest,
                    selectedDay = selectedDay,
                    onReuseRecent = onReuseRecent,
                    showTitle = true,
                )
            } else {
                RecentSection(
                    recent = state.recent,
                    activeRequest = state.activeRequest,
                    selectedDay = selectedDay,
                    onReuseRecent = onReuseRecent,
                    showTitle = false,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                FavoritesSection(
                    allFavorites = state.favorites,
                    favorites = favorites,
                    query = query,
                    onQuery = { query = it },
                    activeRequest = state.activeRequest,
                    selectedDay = selectedDay,
                    onReuseFavorite = onReuseFavorite,
                    onEditTemplate = onEditTemplate,
                    onRename = { favorite ->
                        editing = favorite
                        editedName = favorite.displayName
                    },
                    onDelete = onDelete,
                    showTitle = true,
                )
            }
            OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.manual_entry))
            }
        }
        Spacer(Modifier.height(BaselineSpacing.small))
    }
    editing?.let { favorite ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.rename_template)) },
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
private fun ReuseTopBar(onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(48.dp)) {
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
            )
        }
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Eco,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.app_name),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = BaselineSpacing.small),
            )
        }
    }
}

@Composable
private fun FavoritesSection(
    allFavorites: List<FavoriteDto>,
    favorites: List<FavoriteDto>,
    query: String,
    onQuery: (String) -> Unit,
    activeRequest: String?,
    selectedDay: String,
    onReuseFavorite: (FavoriteDto, String) -> Unit,
    onEditTemplate: (FavoriteDto) -> Unit,
    onRename: (FavoriteDto) -> Unit,
    onDelete: (FavoriteDto) -> Unit,
    showTitle: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
        if (showTitle) {
            Text(stringResource(R.string.favorites), style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            label = { Text(stringResource(R.string.search_favorites)) },
            singleLine = true,
            shape = BaselineShapes.input,
            modifier = Modifier.fillMaxWidth(),
        )
        if (favorites.isEmpty()) {
            EmptySection(
                text = stringResource(
                    if (allFavorites.isEmpty()) R.string.reuse_no_favorites else R.string.reuse_no_favorite_results,
                ),
                tag = "favorites-empty",
            )
        } else {
            favorites.forEach { favorite ->
                FavoriteCard(
                    favorite = favorite,
                    busy = activeRequest == favorite.id,
                    onReuse = { onReuseFavorite(favorite, selectedDay) },
                    onEditTemplate = { onEditTemplate(favorite) },
                    onRename = { onRename(favorite) },
                    onDelete = { onDelete(favorite) },
                )
            }
        }
    }
}

@Composable
private fun RecentSection(
    recent: List<MealDto>,
    activeRequest: String?,
    selectedDay: String,
    onReuseRecent: (MealDto, String) -> Unit,
    showTitle: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
        if (showTitle) {
            Text(stringResource(R.string.recent_section_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.recent_section_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (recent.isEmpty()) {
            EmptySection(stringResource(R.string.reuse_no_recent), "recent-empty")
        } else {
            recent.forEach { meal ->
                RecentCard(
                    meal = meal,
                    busy = activeRequest == meal.id,
                    onReuse = { onReuseRecent(meal, selectedDay) },
                )
            }
        }
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
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("favorite-${favorite.id}"),
        shape = BaselineShapes.compactCard,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(BaselineSpacing.compact),
            verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MealTypeIcon(favorite.meal.mealType)
                Column(Modifier.weight(1f).padding(horizontal = BaselineSpacing.small)) {
                    Text(
                        favorite.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(mealTypeLabel(favorite.meal.mealType)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                EnergyLabel(favorite.meal.energy())
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = stringResource(
                                R.string.favorite_more_actions,
                                favorite.displayName,
                            ),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename_template)) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.remove_favorite)) },
                            enabled = !busy,
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
                Button(
                    enabled = !busy,
                    onClick = onReuse,
                    modifier = Modifier.testTag("favorite-reuse-${favorite.id}"),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.add_again))
                    }
                }
                OutlinedButton(
                    onClick = onEditTemplate,
                    modifier = Modifier.testTag("favorite-edit-template-${favorite.id}"),
                ) {
                    Text(stringResource(R.string.edit))
                }
            }
        }
    }
}

@Composable
private fun RecentCard(meal: MealDto, busy: Boolean, onReuse: () -> Unit) {
    val description = stringResource(R.string.reuse_recent_action, meal.name)
    Card(
        modifier = Modifier.fillMaxWidth().testTag("recent-${meal.id}"),
        shape = BaselineShapes.compactCard,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !busy, onClick = onReuse)
                .semantics {
                    contentDescription = description
                    role = Role.Button
                }
                .testTag("recent-reuse-${meal.id}")
                .padding(BaselineSpacing.compact),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MealTypeIcon(meal.mealType)
            Column(Modifier.weight(1f).padding(horizontal = BaselineSpacing.small)) {
                Text(
                    meal.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    recentMoment(meal),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                EnergyLabel(meal.energy())
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = BaselineSpacing.tiny),
                )
            }
        }
    }
}

@Composable
private fun MealTypeIcon(type: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        Icon(
            mealTypeIcon(type),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun EnergyLabel(value: BigDecimal?) {
    Text(
        if (value == null) "–" else "${value.displayNumber()} kcal",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun EmptySection(text: String, tag: String) {
    BaselineCard(Modifier.fillMaxWidth().testTag(tag)) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun recentMoment(meal: MealDto): String {
    val locale = currentLocale()
    val day = runCatching { LocalDate.parse(meal.localDay) }.getOrNull()
    val time = runCatching {
        OffsetDateTime.parse(meal.eatenAt)
            .atZoneSameInstant(ZoneId.of(meal.timezone))
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    }.getOrNull()
    val date = when (day) {
        LocalDate.now() -> stringResource(R.string.today)
        LocalDate.now().minusDays(1) -> stringResource(R.string.yesterday)
        null -> meal.localDay
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }
    return listOfNotNull(date, time).joinToString(" · ")
}

private fun MealPayload.energy(): BigDecimal? = energy(nutrients, ingredients.flatMap { it.nutrients })

private fun MealDto.energy(): BigDecimal? = energy(nutrients, ingredients.flatMap { it.nutrients })

private fun energy(vararg groups: List<NutrientDto>): BigDecimal? {
    val values = groups.flatMap { it }
        .filter { it.key == "energy" && it.basis == "portion" }
        .mapNotNull { it.value.toBigDecimalOrNull() }
    return values.takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO, BigDecimal::add)
}

@Composable
private fun BigDecimal.displayNumber(): String = NumberFormat.getNumberInstance(currentLocale()).apply {
    maximumFractionDigits = 1
    minimumFractionDigits = 0
}.format(setScale(1, RoundingMode.HALF_UP).stripTrailingZeros())

@Composable
private fun currentLocale(): Locale = Locale.forLanguageTag(
    LocalConfiguration.current.locales[0].toLanguageTag(),
)

private fun mealTypeLabel(type: String): Int = when (type) {
    "breakfast" -> R.string.breakfast
    "lunch" -> R.string.lunch
    "dinner" -> R.string.dinner
    else -> R.string.snacks
}

private fun mealTypeIcon(type: String): ImageVector = when (type) {
    "breakfast" -> Icons.Rounded.BrunchDining
    "lunch" -> Icons.Rounded.LunchDining
    "dinner" -> Icons.Rounded.DinnerDining
    else -> Icons.Rounded.Restaurant
}
