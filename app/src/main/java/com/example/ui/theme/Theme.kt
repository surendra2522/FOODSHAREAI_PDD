package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme =
  lightColorScheme(
    primary = EmeraldGreen,
    secondary = DarkGreen,
    tertiary = OrangeFlame,
    background = PureWhite,
    surface = PureWhite,
    surfaceVariant = SurfaceLight,
    onPrimary = PureWhite,
    onSecondary = PureWhite,
    onBackground = PrimaryText,
    onSurface = PrimaryText,
    onSurfaceVariant = SecondaryText,
    outline = Color(0xFFD1D5DB),
    outlineVariant = Color(0xFFE5E7EB),
    error = RubyRed,
    onError = PureWhite
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = LightColorScheme,
    typography = Typography,
    content = content
  )
}

@Composable
fun customOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color(0xFF111827),
    unfocusedTextColor = Color(0xFF111827),
    disabledTextColor = Color(0xFF4B5563),
    errorTextColor = Color(0xFFEF4444),

    focusedContainerColor = PureWhite,
    unfocusedContainerColor = PureWhite,
    disabledContainerColor = Color(0xFFF3F4F6),
    errorContainerColor = PureWhite,

    cursorColor = EmeraldGreen,
    errorCursorColor = RubyRed,

    selectionColors = TextSelectionColors(
        handleColor = EmeraldGreen,
        backgroundColor = EmeraldGreen.copy(alpha = 0.2f)
    ),

    focusedBorderColor = EmeraldGreen,
    unfocusedBorderColor = Color(0xFFD1D5DB),
    disabledBorderColor = Color(0xFFE5E7EB),
    errorBorderColor = RubyRed,

    focusedLabelColor = EmeraldGreen,
    unfocusedLabelColor = Color(0xFF6B7280),
    disabledLabelColor = Color(0xFF9CA3AF),
    errorLabelColor = RubyRed,

    focusedPlaceholderColor = Color(0xFF9CA3AF),
    unfocusedPlaceholderColor = Color(0xFF9CA3AF),
    disabledPlaceholderColor = Color(0xFF9CA3AF),
    errorPlaceholderColor = Color(0xFF9CA3AF),

    focusedLeadingIconColor = EmeraldGreen,
    unfocusedLeadingIconColor = Color(0xFF6B7280),

    focusedTrailingIconColor = EmeraldGreen,
    unfocusedTrailingIconColor = Color(0xFF6B7280)
)

@Composable
fun customTextFieldColors() = TextFieldDefaults.colors(
    focusedTextColor = Color(0xFF111827),
    unfocusedTextColor = Color(0xFF111827),
    disabledTextColor = Color(0xFF4B5563),
    errorTextColor = Color(0xFFEF4444),

    focusedContainerColor = PureWhite,
    unfocusedContainerColor = PureWhite,
    disabledContainerColor = Color(0xFFF3F4F6),
    errorContainerColor = PureWhite,

    cursorColor = EmeraldGreen,
    errorCursorColor = RubyRed,

    selectionColors = TextSelectionColors(
        handleColor = EmeraldGreen,
        backgroundColor = EmeraldGreen.copy(alpha = 0.2f)
    ),

    focusedIndicatorColor = EmeraldGreen,
    unfocusedIndicatorColor = Color(0xFFD1D5DB),
    disabledIndicatorColor = Color(0xFFE5E7EB),
    errorIndicatorColor = RubyRed,

    focusedLabelColor = EmeraldGreen,
    unfocusedLabelColor = Color(0xFF6B7280),
    disabledLabelColor = Color(0xFF9CA3AF),
    errorLabelColor = RubyRed,

    focusedPlaceholderColor = Color(0xFF9CA3AF),
    unfocusedPlaceholderColor = Color(0xFF9CA3AF),
    disabledPlaceholderColor = Color(0xFF9CA3AF),
    errorPlaceholderColor = Color(0xFF9CA3AF),

    focusedLeadingIconColor = EmeraldGreen,
    unfocusedLeadingIconColor = Color(0xFF6B7280),

    focusedTrailingIconColor = EmeraldGreen,
    unfocusedTrailingIconColor = Color(0xFF6B7280)
)
