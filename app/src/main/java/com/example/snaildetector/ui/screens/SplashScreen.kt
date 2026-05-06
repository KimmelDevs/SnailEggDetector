package com.example.snaildetector.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.snaildetector.supabase
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.SessionStatus
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onAuthenticated    : () -> Unit,
    onNotAuthenticated : () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label         = "splash-fade"
    )

    // Fade in then check session
    LaunchedEffect(Unit) {
        visible = true
        delay(800) // let the animation play

        // Wait for Supabase to resolve session from storage
        supabase.auth.sessionStatus.collect { status ->
            when (status) {
                is SessionStatus.Authenticated    -> onAuthenticated()
                is SessionStatus.NotAuthenticated -> onNotAuthenticated()
                else                              -> Unit // LoadingFromStorage — keep waiting
            }
        }
    }

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier            = Modifier.alpha(alpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text     = "🐌",
                fontSize = 72.sp,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text       = "SnailEggs",
                fontSize   = 24.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text     = "AI Snail Egg Detection",
                fontSize = 13.sp,
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            )
        }
    }
}