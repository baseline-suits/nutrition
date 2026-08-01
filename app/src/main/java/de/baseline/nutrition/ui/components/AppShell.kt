package de.baseline.nutrition.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import de.baseline.nutrition.R

enum class MainDestination(val label: Int, val icon: ImageVector) {
    Overview(R.string.navigation_overview, Icons.Rounded.Home),
    Diary(R.string.navigation_diary, Icons.AutoMirrored.Rounded.MenuBook),
    Statistics(R.string.navigation_statistics, Icons.Rounded.BarChart),
    Profile(R.string.navigation_profile, Icons.Rounded.Person),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaselineAppShell(
    selected: MainDestination,
    onSelected: (MainDestination) -> Unit,
    showTopBar: Boolean = true,
    content: @Composable () -> Unit,
) {
    BackHandler(enabled = selected != MainDestination.Overview) {
        onSelected(MainDestination.Overview)
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (showTopBar) CenterAlignedTopAppBar(
                title = {
                    androidx.compose.foundation.layout.Row {
                        Icon(
                            imageVector = Icons.Rounded.Eco,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = de.baseline.nutrition.ui.theme.BaselineSpacing.small),
                        )
                    }
                },
                actions = {
                    if (selected != MainDestination.Profile) {
                        IconButton(onClick = { onSelected(MainDestination.Profile) }) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.settings_title),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                MainDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = selected == destination,
                        onClick = { if (selected != destination) onSelected(destination) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            content()
        }
    }
}
