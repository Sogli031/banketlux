package com.banketlux.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Donja traka za uske ekrane. Aktivna stavka ima Material indikator (pilulu),
 * pa se stanje ne razlikuje samo bojom. Ikonica je ukrasna jer labela već postoji.
 */
@Composable
fun BanketNavigationBar(
    currentRoute: String?,
    onNavigate: (AppDestination) -> Unit
) {
    NavigationBar {
        AppDestination.bottomNavItems.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onNavigate(destination) },
                icon = { Icon(imageVector = destination.icon, contentDescription = null) },
                label = { Text(destination.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedTextColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

/** Bočna navigacija za ekrane široke 600dp i više (tablet, landscape, sklopivi). */
@Composable
fun BanketNavigationRail(
    currentRoute: String?,
    onNavigate: (AppDestination) -> Unit
) {
    NavigationRail {
        AppDestination.bottomNavItems.forEach { destination ->
            NavigationRailItem(
                selected = currentRoute == destination.route,
                onClick = { onNavigate(destination) },
                icon = { Icon(imageVector = destination.icon, contentDescription = null) },
                label = { Text(destination.label) },
                alwaysShowLabel = true,
                colors = NavigationRailItemDefaults.colors(
                    selectedTextColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
