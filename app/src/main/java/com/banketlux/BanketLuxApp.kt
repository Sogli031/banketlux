package com.banketlux

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
import com.banketlux.ui.navigation.BanketNavigationBar
import com.banketlux.ui.navigation.BanketNavigationRail
import com.banketlux.ui.theme.BanketMotion

private const val BOOKING_EDITOR_ROUTE = "booking_editor"
private const val BOOKING_ID_ARG = "bookingId"

/** Od ove širine ekrana (dp) donja traka postaje bočni rail. */
private const val RAIL_MIN_WIDTH_DP = 600
private val CONTENT_MAX_WIDTH = 840.dp

private val ScreenEnter = fadeIn(tween(210, delayMillis = 90, easing = BanketMotion.EmphasizedDecelerate)) +
    scaleIn(tween(300, easing = BanketMotion.EmphasizedDecelerate), initialScale = 0.94f)
private val ScreenExit = fadeOut(tween(90, easing = BanketMotion.EmphasizedAccelerate))

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

    // Editor je detail ekran sa svojim "Nazad" i dugmetom "Sačuvaj": navigacija se na njemu sakriva.
    val showNavigation = currentRoute != null && !currentRoute.startsWith(BOOKING_EDITOR_ROUTE)
    val useRail = LocalConfiguration.current.screenWidthDp >= RAIL_MIN_WIDTH_DP
    val navigateTo: (AppDestination) -> Unit = { destination ->
        if (currentRoute != destination.route) {
            navController.navigate(destination.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Statusnu traku gore pokriva gornja traka svakog ekrana; ovde bi se dodala dvaput.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = showNavigation && !useRail,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                BanketNavigationBar(currentRoute = currentRoute, onNavigate = navigateTo)
            }
        }
    ) { innerPadding ->
        // Donja traka / rail već pokrivaju sistemske trake — ekrani ih ne dodaju ponovo.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            val showRail = showNavigation && useRail
            if (showRail) {
                BanketNavigationRail(currentRoute = currentRoute, onNavigate = navigateTo)
            }
            // Sadržaj ne ide šire od 840dp i centriran je, da redovi teksta ostanu čitljivi.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.TopCenter
            ) {
                NavHost(
                    navController = navController,
                    startDestination = AppDestination.Bookings.route,
                    enterTransition = { ScreenEnter },
                    exitTransition = { ScreenExit },
                    popEnterTransition = { ScreenEnter },
                    popExitTransition = { ScreenExit },
                    modifier = Modifier
                        .widthIn(max = CONTENT_MAX_WIDTH)
                        .fillMaxSize()
                        .then(
                            if (showRail) {
                                Modifier.consumeWindowInsets(
                                    NavigationRailDefaults.windowInsets.only(WindowInsetsSides.Start)
                                )
                            } else {
                                Modifier
                            }
                        )
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
    }
}
