package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.FoodShareViewModel
import com.example.ui.theme.*

@Composable
fun PremiumBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "bg_anim")
    
    val xOffset1 by infiniteTransition.animateFloat(
        initialValue = -50f, targetValue = 50f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Reverse),
        label = "x1"
    )
    val yOffset1 by infiniteTransition.animateFloat(
        initialValue = -30f, targetValue = 30f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse),
        label = "y1"
    )

    Box(modifier = Modifier.fillMaxSize().background(PureWhite)) {
        // Top-left abstract mint-green blurred circle (opacity 8%)
        Box(
            modifier = Modifier
                .size(260.dp)
                .offset(x = (-40 + xOffset1).dp, y = (-40 + yOffset1).dp)
                .background(EmeraldGreen.copy(alpha = 0.08f), CircleShape)
                .blur(60.dp)
        )
        // Bottom-right abstract mint-green blurred circle (opacity 9%)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(240.dp)
                .offset(x = (40 - xOffset1).dp, y = (40 - yOffset1).dp)
                .background(DarkGreen.copy(alpha = 0.09f), CircleShape)
                .blur(65.dp)
        )
    }
}

@Composable
fun LoginScreen(
    viewModel: FoodShareViewModel,
    onNavigateToRegister: () -> Unit,
    onNavigateToAdminLogin: () -> Unit,
    onNavigateToResetPassword: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("donor") }
    var showPassword by remember { mutableStateOf(false) }

    var emailDirty by remember { mutableStateOf(false) }
    var passwordDirty by remember { mutableStateOf(false) }

    val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
    val isEmailValid = email.isEmpty() || emailRegex.matches(email.trim())
    val isPasswordValid = password.isEmpty() || password.length >= 6

    val errorMessage by viewModel.errorMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Screen launch fade-in animation
    val screenAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        viewModel.clearMessages()
        screenAlpha.animateTo(1f, tween(600))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PremiumBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .alpha(screenAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Logo Section (Background circle reduced by ~20% to 80.dp)
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(EmeraldGreen.copy(alpha = 0.12f), CircleShape)
                        .blur(16.dp)
                )
                Icon(
                    imageVector = Icons.Default.Restaurant,
                    contentDescription = "FoodShare Logo",
                    tint = EmeraldGreen,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "FoodShareAI",
                style = MaterialTheme.typography.displayMedium,
                color = PrimaryText,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "AI-Powered Smart Food Redistribution",
                style = MaterialTheme.typography.bodyMedium,
                color = EmeraldGreen,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Welcome Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Welcome Back",
                    style = MaterialTheme.typography.headlineMedium,
                    color = PrimaryText,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Continue your food redistribution mission",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryText
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Role Selection Cards with smooth 250ms transition and ripple
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                listOf(
                    Triple("donor", "Donor", Icons.Default.VolunteerActivism),
                    Triple("ngo", "NGO", Icons.Default.Handshake)
                ).forEach { (roleKey, label, icon) ->
                    val isSelected = selectedRole == roleKey
                    
                    val cardScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.02f else 1f,
                        animationSpec = tween(250),
                        label = "roleScale"
                    )
                    val cardBgColor by animateColorAsState(
                        targetValue = if (isSelected) LightGreenBg else PureWhite,
                        animationSpec = tween(250),
                        label = "roleBg"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (isSelected) EmeraldGreen else GrayBorder,
                        animationSpec = tween(250),
                        label = "roleBorder"
                    )
                    val elevation by animateDpAsState(
                        targetValue = if (isSelected) 4.dp else 0.dp,
                        animationSpec = tween(250),
                        label = "roleElevation"
                    )

                    Surface(
                        onClick = { selectedRole = roleKey },
                        modifier = Modifier
                            .weight(1f)
                            .scale(cardScale)
                            .testTag("role_${roleKey}_tab"),
                        shape = RoundedCornerShape(16.dp),
                        color = cardBgColor,
                        border = BorderStroke(width = if (isSelected) 2.dp else 1.dp, color = borderColor),
                        shadowElevation = elevation
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 18.dp, horizontal = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) EmeraldGreen else Color(0xFF374151),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = PrimaryText,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            val isUserFacingAuthError = !errorMessage.isNullOrBlank() &&
                !errorMessage.orEmpty().contains("Gemini", ignoreCase = true) &&
                !errorMessage.orEmpty().contains("API key", ignoreCase = true) &&
                !errorMessage.orEmpty().contains("AI Studio", ignoreCase = true) &&
                !errorMessage.orEmpty().contains("AI services", ignoreCase = true)

            if (isUserFacingAuthError) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    color = RubyRed.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RubyRed.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = errorMessage.orEmpty(),
                        color = RubyRed,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Input Fields with focus animation and rounded corners (16dp, height 58dp)
            val emailInteractionSource = remember { MutableInteractionSource() }
            val isEmailFocused by emailInteractionSource.collectIsFocusedAsState()

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; emailDirty = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("username_input"),
                label = { Text("Email Address") },
                placeholder = { Text("restaurant@hotel.com", color = MutedText) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Email Icon",
                        tint = if (isEmailFocused) EmeraldGreen else SecondaryText
                    )
                },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                isError = emailDirty && !isEmailValid,
                interactionSource = emailInteractionSource,
                colors = customOutlinedTextFieldColors()
            )

            Spacer(modifier = Modifier.height(16.dp))

            val passwordInteractionSource = remember { MutableInteractionSource() }
            val isPasswordFocused by passwordInteractionSource.collectIsFocusedAsState()

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; passwordDirty = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("password_input"),
                label = { Text("Password") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Password Icon",
                        tint = if (isPasswordFocused) EmeraldGreen else SecondaryText
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        AnimatedContent(
                            targetState = showPassword,
                            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                            label = "passwordVisibility"
                        ) { isVisible ->
                            Icon(
                                imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (isVisible) "Hide Password" else "Show Password",
                                tint = SecondaryText
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                isError = passwordDirty && !isPasswordValid,
                interactionSource = passwordInteractionSource,
                colors = customOutlinedTextFieldColors()
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Sign In Button with scale on press & green gradient
            val isFormComplete = email.isNotBlank() && password.length >= 6
            val buttonInteractionSource = remember { MutableInteractionSource() }
            val isButtonPressed by buttonInteractionSource.collectIsPressedAsState()
            val buttonScale by animateFloatAsState(
                targetValue = if (isButtonPressed && isFormComplete) 0.97f else 1f,
                animationSpec = tween(150),
                label = "buttonScale"
            )

            Button(
                onClick = {
                    if (isFormComplete) {
                        viewModel.login(email.trim(), password, selectedRole) { onLoginSuccess() }
                    }
                },
                enabled = isFormComplete && !isLoading,
                interactionSource = buttonInteractionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .scale(buttonScale)
                    .testTag("submit_button")
                    .shadow(
                        elevation = if (isFormComplete) 8.dp else 0.dp,
                        shape = RoundedCornerShape(18.dp),
                        spotColor = EmeraldGreen.copy(alpha = 0.4f)
                    ),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = Color(0xFFE5E7EB)
                ),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isFormComplete) Brush.horizontalGradient(listOf(EmeraldGreen, DarkGreen))
                            else Brush.horizontalGradient(listOf(Color(0xFFE5E7EB), Color(0xFFE5E7EB)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = PureWhite, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            text = "Sign In",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isFormComplete) PureWhite else Color(0xFF9CA3AF)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Create Account Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Don't have an account? ",
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Create Account",
                    color = EmeraldGreen,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clickable { onNavigateToRegister() }
                        .testTag("create_account_btn")
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Admin Access link with lock icon
            TextButton(
                onClick = { onNavigateToAdminLogin() },
                modifier = Modifier.testTag("admin_access_link"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = SecondaryText,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Admin Access",
                        color = SecondaryText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}


@Composable
fun RegisterScreen(
    viewModel: FoodShareViewModel,
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("donor") }

    val errorMessage by viewModel.errorMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        PremiumBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Join our Save-Food Mission",
                style = MaterialTheme.typography.displaySmall,
                color = PrimaryText,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = PureWhite,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Create Account",
                        style = MaterialTheme.typography.titleLarge,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val isUserFacingAuthError = !errorMessage.isNullOrBlank() &&
                        !errorMessage.orEmpty().contains("Gemini", ignoreCase = true) &&
                        !errorMessage.orEmpty().contains("API key", ignoreCase = true) &&
                        !errorMessage.orEmpty().contains("AI Studio", ignoreCase = true) &&
                        !errorMessage.orEmpty().contains("AI services", ignoreCase = true)

                    if (isUserFacingAuthError) {
                        Text(
                            text = errorMessage.orEmpty(),
                            color = RubyRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Role selector simplified
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(4.dp)
                    ) {
                        listOf("donor" to "Donor", "ngo" to "NGO").forEach { (key, label) ->
                            val isSelected = selectedRole == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) EmeraldGreen else Color.Transparent)
                                    .clickable { selectedRole = key }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) PureWhite else SecondaryText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Organization / Name") },
                        shape = RoundedCornerShape(18.dp),
                        colors = customOutlinedTextFieldColors()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email Address") },
                        shape = RoundedCornerShape(18.dp),
                        colors = customOutlinedTextFieldColors()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Password") },
                        shape = RoundedCornerShape(18.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        colors = customOutlinedTextFieldColors()
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { viewModel.register(email, name, password, selectedRole) { onRegisterSuccess() } },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        enabled = !isLoading
                    ) {
                        if (isLoading) CircularProgressIndicator(color = PureWhite, modifier = Modifier.size(24.dp))
                        else Text("Create Account", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            TextButton(onClick = { onNavigateToLogin() }) {
                Text(text = "Already have an account? Sign In", color = EmeraldGreen, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// AdminLoginScreen and ResetPasswordScreen would follow same pattern...
@Composable
fun AdminLoginScreen(
    viewModel: FoodShareViewModel,
    onNavigateToLogin: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val errorMessage by viewModel.errorMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        PremiumBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(64.dp))

            Icon(
                imageVector = Icons.Default.AdminPanelSettings,
                contentDescription = null,
                tint = RubyRed,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Administrator Access",
                style = MaterialTheme.typography.displaySmall,
                color = PrimaryText,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Restricted to authorized personnel only",
                style = MaterialTheme.typography.bodyMedium,
                color = RubyRed
            )

            Spacer(modifier = Modifier.height(32.dp))

            val isUserFacingAuthError = !errorMessage.isNullOrBlank() &&
                !errorMessage.orEmpty().contains("Gemini", ignoreCase = true) &&
                !errorMessage.orEmpty().contains("API key", ignoreCase = true) &&
                !errorMessage.orEmpty().contains("AI Studio", ignoreCase = true) &&
                !errorMessage.orEmpty().contains("AI services", ignoreCase = true)

            if (isUserFacingAuthError) {
                Text(
                    text = errorMessage.orEmpty(),
                    color = RubyRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = PureWhite,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Admin Email") },
                        shape = RoundedCornerShape(18.dp),
                        colors = customOutlinedTextFieldColors()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Password") },
                        shape = RoundedCornerShape(18.dp),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        colors = customOutlinedTextFieldColors()
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { viewModel.login(email.trim(), password, "admin") { onLoginSuccess() } },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RubyRed),
                        enabled = !isLoading
                    ) {
                        if (isLoading) CircularProgressIndicator(color = PureWhite, modifier = Modifier.size(24.dp))
                        else Text("Verify Credentials", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            TextButton(onClick = { onNavigateToLogin() }) {
                Text(text = "Back to Standard Login", color = SecondaryText)
            }
        }
    }
}

@Composable
fun ResetPasswordScreen(
    viewModel: FoodShareViewModel,
    onNavigateToLogin: () -> Unit,
    onResetSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    val isLoading by viewModel.isLoading.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        PremiumBackground()

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(64.dp))
            Text("Reset Password", style = MaterialTheme.typography.displaySmall, color = PrimaryText, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(48.dp))
            
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email Address") },
                shape = RoundedCornerShape(18.dp),
                colors = customOutlinedTextFieldColors()
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { viewModel.resetPassword(email, "reset") { onResetSuccess() } },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                enabled = !isLoading
            ) {
                Text("Send Reset Link")
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            TextButton(onClick = { onNavigateToLogin() }) {
                Text("Back to Login", color = SecondaryText)
            }
        }
    }
}
