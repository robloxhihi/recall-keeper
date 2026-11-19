package com.robloxhihi.recallkeeper.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF1F3A5F)
val ConfirmBg = Color(0xFFFDE7E7)
val ConfirmFg = Color(0xFFB3261E)
val SuspectBg = Color(0xFFFFF3DC)
val SuspectFg = Color(0xFF8A5A00)
val NormalBg = Color(0xFFEDF1F6)
val NormalFg = Color(0xFF4A5568)

private val Light = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    secondary = Color(0xFF4A6785),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFEDF1F6),
    onSurfaceVariant = Color(0xFF4A5568),
    outline = Color(0xFFCBD5E0)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9FC0E8),
    onPrimary = Color(0xFF0B2038),
    secondary = Color(0xFF9FC0E8),
    background = Color(0xFF14161A),
    onBackground = Color(0xFFE6E8EB),
    surface = Color(0xFF1C1F24),
    onSurface = Color(0xFFE6E8EB),
    surfaceVariant = Color(0xFF2A2F36),
    onSurfaceVariant = Color(0xFFB7C0CB),
    outline = Color(0xFF3C434C)
)

@Composable
fun RecallTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content
    )
}
