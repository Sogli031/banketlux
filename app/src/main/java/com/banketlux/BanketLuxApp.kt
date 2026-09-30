package com.banketlux

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.banketlux.data.backup.BackupScheduler
import com.banketlux.ui.bookings.BookingEditorScreen
import com.banketlux.ui.bookings.BookingsScreen
import com.banketlux.ui.earnings.EarningsScreen
import com.banketlux.ui.settings.SettingsScreen
import com.banketlux.ui.equipment.EquipmentScreen
import com.banketlux.ui.navigation.AppDestination
import com.banketlux.ui.theme.BanketIconMuted
import com.banketlux.ui.theme.BanketNavBar

private const val BOOKING_EDITOR_ROUTE = "booking_editor"
private const val BOOKING_ID_ARG = "bookingId"

@Composable
fun BanketLuxApp() {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as BanketLuxApplication }
    val equipmentRepository = app.equipmentRepository
    val bookingRepository = app.bookingRepository
    val earningsRepository = app.earningsRepository
    val settingsRepository = app.settingsRepository

    // Kurs se osvežava u pozadini pri pokretanju; bez mreže ostaje poslednji sačuvani.
    LaunchedEffect(settingsRepository) {
        runCatching { settingsRepository.refreshRateIfStale() }
            // Novi kurs je izmena podešavanja — neka stigne i u backup.
            .onSuccess { refreshed -> if (refreshed?.isSuccess == true) BackupScheduler.scheduleAfterChange(context) }
    }

    // Zakazivanja kojima je najam prošao sele se u zaradu i nestaju sa liste.
    LaunchedEffect(earningsRepository) {
        runCatching { earningsRepository.archiveEndedBookings() }
    }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Statusnu traku gore pokriva gornja traka svakog ekrana; ovde bi se dodala dvaput.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                // Aktivna stavka je zlatna, bez Material "pilule" — diskretno.
                NavigationBar(
                    containerColor = BanketNavBar,
                    tonalElevation = 0.dp
                ) {
                    AppDestination.bottomNavItems.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                if (currentRoute != destination.route) {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label
                                )
                            },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = BanketIconMuted,
                                unselectedTextColor = BanketIconMuted
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Bookings.route,
            // Donja navigacija već pokriva sistemsku traku — ekrani je ne dodaju ponovo.
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            composable(AppDestination.Bookings.route) {
                BookingsScreen(
                    bookingRepository = bookingRepository,
                    onCreateBooking = {
                        navController.navigate(BOOKING_EDITOR_ROUTE)
                    },
                    onEditBooking = { bookingId ->
                        navController.navigate("$BOOKING_EDITOR_ROUTE?$BOOKING_ID_ARG=$bookingId")
                    }
                )
            }
            composable(AppDestination.Equipment.route) {
                EquipmentScreen(equipmentRepository = equipmentRepository)
            }
            composable(AppDestination.Earnings.route) {
                EarningsScreen(earningsRepository = earningsRepository)
            }
            composable(AppDestination.Settings.route) {
                SettingsScreen(
                    backupRepository = app.backupRepository,
                    driveBackup = app.googleDriveBackup,
                    calendarSync = app.calendarSync,
                    bookingRepository = bookingRepository
                )
            }
            composable(
                route = "$BOOKING_EDITOR_ROUTE?$BOOKING_ID_ARG={$BOOKING_ID_ARG}",
                arguments = listOf(
                    navArgument(BOOKING_ID_ARG) {
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val bookingId = backStackEntry.arguments?.getString(BOOKING_ID_ARG)?.toLongOrNull()
                BookingEditorScreen(
                    bookingRepository = bookingRepository,
                    equipmentRepository = equipmentRepository,
                    settingsRepository = settingsRepository,
                    bookingId = bookingId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
