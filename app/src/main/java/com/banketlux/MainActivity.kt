package com.banketlux

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.banketlux.notifications.BookingReminders
import com.banketlux.ui.theme.BanketLuxTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Zakazivanje za sutra ne čeka sledeću proveru ako je dozvola upravo data.
            if (granted) BookingReminders.checkNow(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Tema je uvek tamna, pa su i sistemske trake uvek tamne (svetle ikonice) i providne.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        setContent {
            BanketLuxTheme {
                BanketLuxApp()
            }
        }
    }

    /** Android 13+ traži dozvolu za obaveštenja; bez nje podsetnik dan ranije ne stiže. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
