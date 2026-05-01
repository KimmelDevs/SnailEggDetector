package com.example.snaildetector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.snaildetector.ui.navigation.AppNavGraph
import com.example.snaildetector.ui.theme.SnailDetectorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnailDetectorTheme {
                AppNavGraph()
            }
        }
    }
}
