package com.anasdev.fluxlink.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.anasdev.fluxlink.presentation.ui.screens.HomeScreen
import com.anasdev.fluxlink.presentation.ui.theme.FluxLinkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FluxLinkTheme {
                HomeScreen()
            }
        }
    }
}
