package com.canlitvradyo.tr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.canlitvradyo.tr.ui.AppScreen
import com.canlitvradyo.tr.ui.MainViewModel

private val AppDarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    secondary = Color(0xFFE11D48),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC)
)

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = AppDarkColors) {
                AppScreen(viewModel = viewModel)
            }
        }
    }
}
