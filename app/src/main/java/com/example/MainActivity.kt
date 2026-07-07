package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AdGuardStatus
import com.example.ui.AdGuardTestState
import com.example.ui.AdGuardUiState
import com.example.ui.AdGuardViewModel
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Request Notification Permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    AdGuardApp(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun AdGuardApp(
    modifier: Modifier = Modifier,
    viewModel: AdGuardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val testState by viewModel.testState.collectAsStateWithLifecycle()
    val isToggling by viewModel.isToggling.collectAsStateWithLifecycle()
    val trustSelfSigned by viewModel.trustSelfSigned.collectAsStateWithLifecycle()
    val logsEnabled by viewModel.logsEnabled.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf("dashboard") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val state = uiState) {
            is AdGuardUiState.SetupRequired -> {
                SetupScreen(
                    testState = testState,
                    onTest = { url, user, pass, selfSigned ->
                        viewModel.testCredentials(url, user, pass, selfSigned)
                    },
                    onSave = { url, user, pass ->
                        viewModel.saveCredentials(url, user, pass)
                    },
                    trustSelfSigned = trustSelfSigned,
                    onTrustSelfSignedChange = { viewModel.setTrustSelfSigned(it) },
                    onClearTest = { viewModel.clearTestState() }
                )
            }
            is AdGuardUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("main_loading_indicator")
                    )
                }
            }
            is AdGuardUiState.Success -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header App Bar
                    HeaderBar(
                        onRefresh = { viewModel.refreshStatus() },
                        isRefreshing = isToggling
                    )

                    // Tab Contents
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (activeTab == "dashboard") {
                            DashboardScreen(
                                status = state.status,
                                isToggling = isToggling,
                                onToggle = { viewModel.toggleProtection() }
                            )
                        } else {
                            SettingsScreen(
                                currentUrl = viewModel.serverUrl.collectAsStateWithLifecycle().value,
                                currentUsername = viewModel.username.collectAsStateWithLifecycle().value,
                                trustSelfSigned = trustSelfSigned,
                                logsEnabled = logsEnabled,
                                notificationsEnabled = notificationsEnabled,
                                testState = testState,
                                onTest = { url, user, pass, selfSigned ->
                                    viewModel.testCredentials(url, user, pass, selfSigned)
                                },
                                onSave = { url, user, pass ->
                                    viewModel.saveCredentials(url, user, pass)
                                },
                                onForget = { viewModel.forgetCredentials() },
                                onTrustSelfSignedChange = { viewModel.setTrustSelfSigned(it) },
                                onLogsEnabledChange = { viewModel.setLogsEnabled(it) },
                                onNotificationsEnabledChange = { viewModel.setNotificationsEnabled(it) },
                                onClearTest = { viewModel.clearTestState() }
                            )
                        }
                    }

                    // Bottom navigation bar
                    BottomNavigationBar(
                        activeTab = activeTab,
                        onTabSelected = { activeTab = it }
                    )
                }
            }
            is AdGuardUiState.Error -> {
                ErrorScreen(
                    message = state.message,
                    onRetry = { viewModel.refreshStatus() },
                    onGoToSettings = { viewModel.forgetCredentials() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeaderBar(
    onRefresh: () -> Unit,
    isRefreshing: Boolean
) {
    CenterAlignedTopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_shield_protect),
                    contentDescription = "Shield logo",
                    tint = TextAccent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AdGuard Toggle",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        },
        actions = {
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.testTag("refresh_status_button")
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = TextAccent
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh protection status",
                        tint = TextAccent
                    )
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
fun SetupScreen(
    testState: AdGuardTestState,
    onTest: (String, String, String, Boolean) -> Unit,
    onSave: (String, String, String) -> Unit,
    trustSelfSigned: Boolean,
    onTrustSelfSignedChange: (Boolean) -> Unit,
    onClearTest: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Identity Header
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(Color(0xFF381E72), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_shield_protect),
                contentDescription = "Shield logo",
                tint = TextAccent,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Connect to AdGuard Home",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Enter your self-hosted AdGuard Home server credentials below.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Input Fields Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        onClearTest()
                    },
                    label = { Text("Server URL or IP") },
                    placeholder = { Text("http://192.168.1.25:3000") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_url_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TextAccent,
                        unfocusedBorderColor = BorderColor,
                        focusedLabelColor = TextAccent,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    leadingIcon = {
                        Icon(Icons.Default.Cloud, contentDescription = "Server URL")
                    }
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        onClearTest()
                    },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("username_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TextAccent,
                        unfocusedBorderColor = BorderColor,
                        focusedLabelColor = TextAccent,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = "Username")
                    }
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onClearTest()
                    },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(icon, contentDescription = "Toggle password visibility")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TextAccent,
                        unfocusedBorderColor = BorderColor,
                        focusedLabelColor = TextAccent,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = "Password")
                    }
                )

                // Trust Self-Signed Certificate Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onTrustSelfSignedChange(!trustSelfSigned) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Trust self-signed certificate",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            text = "Enable for custom local SSL certificates",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = trustSelfSigned,
                        onCheckedChange = { onTrustSelfSignedChange(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ActiveGreen,
                            checkedTrackColor = Color(0xFF381E72)
                        ),
                        modifier = Modifier.testTag("trust_self_signed_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Connection Test status feedback
        when (testState) {
            is AdGuardTestState.Testing -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().testTag("test_state_testing")
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = TextAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Testing connection...", color = TextAccent, style = MaterialTheme.typography.bodyMedium)
                }
            }
            is AdGuardTestState.Success -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().testTag("test_state_success")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = ActiveGreen, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connection successful!", color = ActiveGreen, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
            is AdGuardTestState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x1FFF5252), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0x3FFF5252), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .testTag("test_state_error")
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Error, contentDescription = "Error", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = testState.message,
                            color = Color(0xFFFF5252),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            is AdGuardTestState.Idle -> { /* Nothing */ }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { onTest(url, username, password, trustSelfSigned) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("test_connection_button"),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, BorderColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Test Connection", fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = { onSave(url, username, password) },
                enabled = url.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("submit_button"),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TextAccent,
                    contentColor = OnPrimaryText,
                    disabledContainerColor = BorderColor,
                    disabledContentColor = TextSecondary
                )
            ) {
                Text("Save & Connect", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DashboardScreen(
    status: AdGuardStatus,
    isToggling: Boolean,
    onToggle: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Connection Instance Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SERVER INSTANCE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = status.version,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Green Active Connected Badge
                    Row(
                        modifier = Modifier
                            .background(Color(0xFF1C1B1F), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(ActiveGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Connected",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = BorderColor)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("PROTOCOL", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("HTTP REST", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("API VERSION", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("v0.107.0+", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // Main Toggle Controller Card
        val targetColor by animateColorAsState(if (status.protection_enabled) ActiveGreen else BorderColor)
        val targetGlowColor by animateColorAsState(if (status.protection_enabled) ActiveGreenGlow else Color.Transparent)
        val buttonBackground by animateColorAsState(if (status.protection_enabled) TextAccent else BorderColor)
        val buttonText by animateColorAsState(if (status.protection_enabled) OnPrimaryText else Color.White)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(28.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Large Badge with Pulse Ring effect
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .background(targetGlowColor, CircleShape)
                        .border(4.dp, targetColor.copy(alpha = 0.3f), CircleShape)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(targetColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_shield_protect),
                            contentDescription = "Shield state logo",
                            tint = if (status.protection_enabled) Color(0xFF1C1B1F) else Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (status.protection_enabled) "Protection Enabled" else "Protection Disabled",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (status.protection_enabled) {
                        "All tracking and advertisement traffic is currently filtered."
                    } else {
                        "AdGuard Home filtering is temporarily suspended."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onToggle,
                    enabled = !isToggling,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("toggle_protection_button"),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonBackground,
                        contentColor = buttonText
                    )
                ) {
                    if (isToggling) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = buttonText)
                    } else {
                        Text(
                            text = if (status.protection_enabled) "Disable Protection" else "Enable Protection",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        // Quick Settings Tile Preview Walkthrough
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "QUICK SETTINGS PREVIEW",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Active Tile preview
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(TextAccent, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_shield_protect),
                            contentDescription = "Active Tile Preview",
                            tint = OnPrimaryText,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = "AdGuard: ON",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextAccent
                    )
                }

                // Inactive Tile preview
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.alpha(0.5f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(BorderColor, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_shield_protect),
                            contentDescription = "Inactive Tile Preview",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = "OFF",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    currentUrl: String,
    currentUsername: String,
    trustSelfSigned: Boolean,
    logsEnabled: Boolean,
    notificationsEnabled: Boolean,
    testState: AdGuardTestState,
    onTest: (String, String, String, Boolean) -> Unit,
    onSave: (String, String, String) -> Unit,
    onForget: () -> Unit,
    onTrustSelfSignedChange: (Boolean) -> Unit,
    onLogsEnabledChange: (Boolean) -> Unit,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    onClearTest: () -> Unit
) {
    val scrollState = rememberScrollState()

    var url by remember { mutableStateOf(currentUrl) }
    var username by remember { mutableStateOf(currentUsername) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Credentials Configuration",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        // Creds editing Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        onClearTest()
                    },
                    label = { Text("Server URL or IP") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_url_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TextAccent,
                        unfocusedBorderColor = BorderColor,
                        focusedLabelColor = TextAccent,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        onClearTest()
                    },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_username_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TextAccent,
                        unfocusedBorderColor = BorderColor,
                        focusedLabelColor = TextAccent,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onClearTest()
                    },
                    label = { Text("New Password (leave empty to keep current)") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(icon, contentDescription = "Toggle password visibility")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_password_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TextAccent,
                        unfocusedBorderColor = BorderColor,
                        focusedLabelColor = TextAccent,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onTest(url, username, password, trustSelfSigned) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_test_button"),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, BorderColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Test")
                    }

                    Button(
                        onClick = { onSave(url, username, password) },
                        enabled = url.isNotBlank() && username.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_save_button"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TextAccent,
                            contentColor = OnPrimaryText,
                            disabledContainerColor = BorderColor
                        )
                    ) {
                        Text("Save changes")
                    }
                }
            }
        }

        // Connection Test status feedback
        when (testState) {
            is AdGuardTestState.Testing -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().testTag("settings_test_testing")
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = TextAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Testing connection...", color = TextAccent)
                }
            }
            is AdGuardTestState.Success -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().testTag("settings_test_success")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = ActiveGreen, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connection successful!", color = ActiveGreen, fontWeight = FontWeight.Bold)
                }
            }
            is AdGuardTestState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x1FFF5252), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0x3FFF5252), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .testTag("settings_test_error")
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Error, contentDescription = "Error", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = testState.message, color = Color(0xFFFF5252), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            is AdGuardTestState.Idle -> { /* Nothing */ }
        }

        Text(
            text = "App Utilities",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        // Preferences switch list
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Trust Self Signed Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Trust self-signed certificates", color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Bypass SSL verification for self-signed or local IPs.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = trustSelfSigned,
                        onCheckedChange = onTrustSelfSignedChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = ActiveGreen, checkedTrackColor = Color(0xFF381E72)),
                        modifier = Modifier.testTag("settings_trust_self_signed_switch")
                    )
                }

                HorizontalDivider(color = BorderColor)

                // Enable Logs Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable HTTP Logs", color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Log REST API request details to logcat.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = logsEnabled,
                        onCheckedChange = onLogsEnabledChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = ActiveGreen, checkedTrackColor = Color(0xFF381E72)),
                        modifier = Modifier.testTag("settings_logs_switch")
                    )
                }

                HorizontalDivider(color = BorderColor)

                // Enable Notifications Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Quick Settings Notifications", color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Notify when toggled from status bar tile.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = onNotificationsEnabledChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = ActiveGreen, checkedTrackColor = Color(0xFF381E72)),
                        modifier = Modifier.testTag("settings_notifications_switch")
                    )
                }
            }
        }

        // Danger Zone: Wipe / Forget credentials
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0x0FFF5252)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0x1FFF5252))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Danger Zone",
                    color = Color(0xFFFF5252),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Wipe credentials from secure storage. You will need to log in again.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = onForget,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252), contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("forget_credentials_button")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Forget configuration")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Forget Configuration & Credentials", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    activeTab: String,
    onTabSelected: (String) -> Unit
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 16.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dashboard Option
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onTabSelected("dashboard") }
                    .padding(8.dp)
                    .testTag("nav_dashboard")
            ) {
                Icon(
                    imageVector = Icons.Default.Dashboard,
                    contentDescription = "Dashboard navigation",
                    tint = if (activeTab == "dashboard") TextAccent else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "DASHBOARD",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (activeTab == "dashboard") TextAccent else TextSecondary
                )
            }

            // Settings Option
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onTabSelected("settings") }
                    .padding(8.dp)
                    .testTag("nav_settings")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings navigation",
                    tint = if (activeTab == "settings") TextAccent else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "SETTINGS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (activeTab == "settings") TextAccent else TextSecondary
                )
            }
        }
    }
}

@Composable
fun ErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onGoToSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color(0x1FFF5252), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error icon",
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Connection Failed",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = TextAccent, contentColor = OnPrimaryText),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("error_retry_button")
        ) {
            Text("Retry Connection", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onGoToSettings,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, BorderColor),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("error_settings_button")
        ) {
            Text("Edit Setup Settings", fontWeight = FontWeight.SemiBold)
        }
    }
}
