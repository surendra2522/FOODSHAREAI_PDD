package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// High fidelity branding colors specifically for FoodShareAI
private val BrandGreen = Color(0xFF16A34A)
private val BrandOrange = Color(0xFFF97316)
private val DarkSlate = Color(0xFF0F172A)
private val TextMuted = Color(0xFF64748B)

private data class FloatingSpec(
    val icon: ImageVector,
    val xOffset: androidx.compose.ui.unit.Dp,
    val yOffset: androidx.compose.ui.unit.Dp,
    val scale: Float,
    val isOrange: Boolean
)

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val logoScale = remember { Animatable(0.6f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textOffsetY = remember { Animatable(30f) } // animated slide up
    val progressAlpha = remember { Animatable(0f) }

    val currentOnTimeout by rememberUpdatedState(onTimeout)

    // Staggered premium animations choreo
    LaunchedEffect(key1 = true) {
        // Stage 1: Logo appearance
        launch {
            logoAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(1000, easing = FastOutSlowInEasing)
            )
        }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.65f, // organic soft bounce
                    stiffness = 180f
                )
            )
        }
        
        // Stage 2: Brand text slide-up & fade-in
        delay(350)
        launch {
            textAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(800, easing = LinearOutSlowInEasing)
            )
        }
        launch {
            textOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(800, easing = LinearOutSlowInEasing)
            )
        }

        // Stage 3: Dynamic loading bar fades in
        delay(350)
        launch {
            progressAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(600)
            )
        }

        // Stage 4: Hold splash for premium 2.8s total and then route
        delay(2100)
        currentOnTimeout()
    }

    // Set up dynamic floating background oscillations using an infinite translation clock
    val infiniteTransition = rememberInfiniteTransition(label = "background_clock")
    val waveState by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveState"
    )

    // Selection of standard Material icons representing food sustainability and AI matchings
    val floatingIcons = remember {
        listOf(
            FloatingSpec(Icons.Default.Restaurant, (-130).dp, (-250).dp, 0.85f, false),
            FloatingSpec(Icons.Default.Eco, 135.dp, (-210).dp, 1.1f, true),
            FloatingSpec(Icons.Default.AutoAwesome, (-140).dp, 180.dp, 1.0f, false),
            FloatingSpec(Icons.Default.Cake, 140.dp, 230.dp, 0.9f, true),
            FloatingSpec(Icons.Default.Psychology, (-145).dp, (-20).dp, 1.05f, false),
            FloatingSpec(Icons.Default.Share, 150.dp, (-10).dp, 0.95f, false),
            FloatingSpec(Icons.Default.Favorite, (-60).dp, (-340).dp, 0.8f, true),
            FloatingSpec(Icons.Default.LocationOn, 70.dp, 330.dp, 0.95f, false)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFF2FBF4), // 5% green hue
                        Color(0xFFFFF9F4), // 5% orange hue
                        Color(0xFFFAFBFC),
                        Color(0xFFFFFFFF)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // --- 1. Background Subtle Floating Icons Layer ---
        floatingIcons.forEachIndexed { index, spec ->
            // Use trigonometry to calculate dynamic independent bobbing translation & slow rotation
            val oscillationY = (kotlin.math.sin(waveState + (index * 0.9f)) * 12).dp
            val rotationAngle = kotlin.math.cos(waveState + (index * 1.3f)) * 14f

            val iconColor = if (spec.isOrange) {
                BrandOrange.copy(alpha = 0.05f)
            } else {
                BrandGreen.copy(alpha = 0.04f)
            }

            Icon(
                imageVector = spec.icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier
                    .offset(x = spec.xOffset, y = spec.yOffset + oscillationY)
                    .graphicsLayer {
                        rotationZ = rotationAngle
                    }
                    .scale(spec.scale)
                    .size(42.dp)
            )
        }

        // --- 2. Central Glassmorphic Brand Container ---
        Card(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 360.dp)
                .graphicsLayer {
                    alpha = logoAlpha.value
                    scaleX = logoScale.value
                    scaleY = logoScale.value
                }
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(32.dp),
                    clip = false,
                    ambientColor = BrandGreen.copy(alpha = 0.12f),
                    spotColor = BrandOrange.copy(alpha = 0.2f)
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.9f),
                            Color.White.copy(alpha = 0.35f),
                            BrandGreen.copy(alpha = 0.15f),
                            BrandOrange.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.85f)
                        )
                    ),
                    shape = RoundedCornerShape(32.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.72f) // glassmorphism opacity backer
            ),
            shape = RoundedCornerShape(32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 38.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Centered interlocking brand logo representation
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(110.dp)
                ) {
                    // Decorative halo lighting
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        BrandGreen.copy(alpha = 0.18f),
                                        BrandOrange.copy(alpha = 0.08f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Eco Green Shield - Rotated asymmetrically for depth
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .offset(x = (-12).dp, y = (-6).dp)
                            .graphicsLayer { rotationZ = -10f }
                            .shadow(5.dp, RoundedCornerShape(20.dp), clip = false)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(BrandGreen, Color(0xFF15803D))
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restaurant,
                            contentDescription = "Food logo segment",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Intellect Orange Shield - Overlapping to represent social share matchmaking
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .offset(x = 12.dp, y = 8.dp)
                            .graphicsLayer { rotationZ = 14f }
                            .shadow(6.dp, RoundedCornerShape(20.dp), clip = false)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFFFB923C), BrandOrange)
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI intelligence logo segment",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Center Link connection jewel
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .shadow(3.dp, CircleShape, clip = false)
                            .background(Color.White, shape = CircleShape)
                            .border(1.dp, Color(0xFFF1F5F9), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Eco,
                            contentDescription = "Community impact anchor jewel",
                            tint = BrandGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Staggered text segment
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer {
                        alpha = textAlpha.value
                        translationY = textOffsetY.value * density
                    }
                ) {
                    Spacer(modifier = Modifier.height(26.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FoodShare",
                            style = TextStyle(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp,
                                color = DarkSlate
                            )
                        )
                        Text(
                            text = "AI",
                            style = TextStyle(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp,
                                brush = Brush.linearGradient(
                                    colors = listOf(BrandGreen, BrandOrange)
                                )
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "AI-Powered Smart Food Redistribution",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.2.sp
                    )
                }
            }
        }

        // --- 3. Synchronized Progress & Launch Indicator Block ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
                .graphicsLayer {
                    alpha = progressAlpha.value
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Linear Progress bar corresponding to the real runtime timer
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFE2E8F0))
                        .border(0.5.dp, Color(0xFFF1F5F9), RoundedCornerShape(3.dp))
                ) {
                    val progress = remember { Animatable(0f) }
                    LaunchedEffect(key1 = true) {
                        delay(300)
                        progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(2100, easing = FastOutSlowInEasing)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.value)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        BrandGreen,
                                        Color(0xFFFB923C),
                                        BrandOrange
                                    )
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Initializing Eco-Redistribution System...",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
