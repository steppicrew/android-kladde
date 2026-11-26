package de.steppicrew.kladde.adx

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * Dark status and navigation bar icons on the app's light screens, also when the phone is in
 * dark mode (which would make them white on white). Call before setContent.
 */
fun ComponentActivity.showSystemBars() {
    val light = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    enableEdgeToEdge(statusBarStyle = light, navigationBarStyle = light)
}
