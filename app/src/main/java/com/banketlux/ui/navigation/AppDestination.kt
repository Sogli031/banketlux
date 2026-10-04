package com.banketlux.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Bookings : AppDestination(
        route = "bookings",
        label = "Zakazivanja",
        icon = Icons.Filled.Event
    )

    data object Equipment : AppDestination(
        route = "equipment",
        label = "Oprema",
        icon = Icons.Filled.Inventory
    )

    data object Earnings : AppDestination(
        route = "earnings",
        label = "Zarada",
        icon = Icons.Filled.Payments
    )

    data object Settings : AppDestination(
        route = "settings",
        label = "Podešavanja",
        icon = Icons.Filled.Settings
    )

    companion object {
        // Getter, ne polje: polje se puni u statičkoj inicijalizaciji nadklase, pa bi pri
        // prvom pristupu preko AppDestination.Bookings zatekla još neinicijalizovane objekte (null).
        val bottomNavItems: List<AppDestination>
            get() = listOf(Bookings, Equipment, Earnings, Settings)
    }
}
