package com.example

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.data.*
import com.example.ui.ChatMessage
import com.example.ui.MainViewModel
import com.example.ui.MainViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.PasswordVisualTransformation
import coil.compose.AsyncImage
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Local SQLite Room database setup
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "nutrimind_saas_db"
        ).fallbackToDestructiveMigration().build()
        val dao = db.dao()
        val repository = SaaSUserRepository(dao)
        val factory = MainViewModelFactory(repository)
        val viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        setContent {
            val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()
            
            // Custom modern theme block
            NutriTheme(isDark = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppMainView(viewModel = viewModel)
                }
            }
        }
    }
}

// --- Custom Theme System ---
@Composable
fun NutriTheme(
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    val emeraldPrimary = Color(0xFF10B981)
    val oceanTertiary = Color(0xFF3B82F6)
    val goldAccent = Color(0xFFFBBF24)

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = emeraldPrimary,
            secondary = Color(0xFF1F2937),
            tertiary = oceanTertiary,
            background = Color(0xFF090E0C),
            surface = Color(0xFF101714),
            onPrimary = Color.Black,
            onSecondary = Color.White,
            onBackground = Color(0xFFECFDF5),
            onSurface = Color(0xFFF3F4F6)
        )
    } else {
        lightColorScheme(
            primary = emeraldPrimary,
            secondary = Color(0xFFE5E7EB),
            tertiary = oceanTertiary,
            background = Color(0xFFF4FBF7),
            surface = Color(0xFFFFFFFF),
            onPrimary = Color.White,
            onSecondary = Color(0xFF1F2937),
            onBackground = Color(0xFF064E3B),
            onSurface = Color(0xFF111827)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            headlineLarge = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                letterSpacing = (-0.5).sp
            ),
            titleLarge = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp
            ),
            bodyLarge = TextStyle(
                fontSize = 15.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.2.sp
            )
        ),
        content = content
    )
}

// --- Main App Wrapper (Auth Check, Layout Navigation) ---
@Composable
fun AppMainView(viewModel: MainViewModel) {
    val currentEmail by viewModel.currentUserEmail.collectAsStateWithLifecycle()
    val alertBanner by viewModel.systemAlertBanner.collectAsStateWithLifecycle()
    val scaffoldState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(alertBanner) {
        alertBanner?.let {
            scaffoldState.showSnackbar(it)
            viewModel.dismissAlert()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(scaffoldState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (currentEmail == null) {
                AuthScreen(viewModel = viewModel)
            } else {
                DashboardNavigationLayout(viewModel = viewModel)
            }
        }
    }
}

// --- Authentication Screen (Signup / Login Panel) ---
@Composable
fun AuthScreen(viewModel: MainViewModel) {
    var isSignup by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val isAuthenticating by viewModel.isAuthenticating.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .verticalScroll(rememberScrollState())
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    RoundedCornerShape(24.dp)
                )
                .testTag("auth_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with Glowing Badge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Spa,
                        contentDescription = "NutriMind Logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isSignup) "Create NutriMind Profile" else "Access NutriMind AI",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Smart AI-powered nutrition specialist and SaaS advisor",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                // Validation errors
                authError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                // Inputs
                if (isSignup) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Your Full Name") },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_name_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    label = { Text("Email (Use any to test)") },
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_email_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    label = { Text("Password (4+ characters)") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_password_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isAuthenticating) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                } else {
                    Button(
                        onClick = {
                            if (isSignup) {
                                viewModel.registerUser(email, name, password)
                            } else {
                                viewModel.authenticateUser(email, password)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("auth_action_button")
                    ) {
                        Text(
                            text = if (isSignup) "Initialize Specialist" else "Secure Unlock"
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = { isSignup = !isSignup },
                        modifier = Modifier.testTag("auth_toggle_button")
                    ) {
                        Text(
                            text = if (isSignup) "Already have a diagnostic profile? Login" else "New visitor? Create a profile"
                        )
                    }
                }
            }
        }
    }
}

// --- Navigation Tabs definitions ---
sealed class AppTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Dashboard : AppTab("Dashboard", Icons.Default.Home)
    object AIChat : AppTab("AI Chatbot", Icons.Default.Forum)
    object SymptomChecker : AppTab("Diagnostics", Icons.Default.LocalHospital)
    object SaaSAdmin : AppTab("SaaS Suite", Icons.Default.Settings)
}

@Composable
fun DashboardNavigationLayout(viewModel: MainViewModel) {
    var selectedTab by remember { mutableStateOf<AppTab>(AppTab.Dashboard) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                val items = listOf(AppTab.Dashboard, AppTab.AIChat, AppTab.SymptomChecker, AppTab.SaaSAdmin)
                items.forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        alwaysShowLabel = true,
                        modifier = Modifier.testTag("nav_tab_${tab.title.lowercase().replace(" ", "_")}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                AppTab.Dashboard -> DashboardScreen(viewModel = viewModel)
                AppTab.AIChat -> ChatbotScreen(viewModel = viewModel)
                AppTab.SymptomChecker -> SymptomCheckerScreen(viewModel = viewModel)
                AppTab.SaaSAdmin -> AdminSaaSScreeen(viewModel = viewModel)
            }
        }
    }
}

// --- TAB 1: DASHBOARD SCREEN ---
@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val todayWater by viewModel.currentDayWater.collectAsStateWithLifecycle()
    val score by viewModel.weeklyNutritionScore.collectAsStateWithLifecycle()
    val habits by viewModel.todayHabits.collectAsStateWithLifecycle()

    var showProfileEditor by remember { mutableStateOf(false) }
    var activeSubTool by remember { mutableStateOf<String?>("scanner") } // scanner, hair_care, eye_care, calculators, reports

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Custom Header with Greeting & Theme toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Welcome, ${profile?.name ?: "Valued User"}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                // Gamification XP Level progress tracker row
                val currentXp = profile?.xp ?: 0
                val currentLvl = profile?.level ?: 1
                val xpThreshold = currentLvl * 200
                val progressFraction = (currentXp.toFloat() / xpThreshold.toFloat()).coerceIn(0f, 1f)
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "🔥 ${profile?.streak ?: 1} Days",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        color = Color(0xFFF59E0B)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Lv $currentLvl",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Subtle mini XP progress bar
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Color.Gray.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressFraction)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$currentXp/$xpThreshold XP",
                        fontSize = 9.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val badgeColor = when (profile?.plan) {
                        "Elite" -> Color(0xFF8B5CF6)       // Purple glowing Elite
                        "ProPlus" -> Color(0xFF3B82F6)     // Blue ProPlus AI Coach
                        "Pro" -> Color(0xFF10B981)         // Green Pro Health
                        else -> Color(0xFF6B7280)          // Gray Starter Trail
                    }
                    Badge(containerColor = badgeColor) {
                        Text(
                            text = "PLAN: ${profile?.plan?.uppercase() ?: "FREE TRIAL"}",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            IconButton(
                onClick = { viewModel.toggleTheme() },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = "Toggle Theme",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Glassmorphic Health Score Circle & Hydration Tracker
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    RoundedCornerShape(20.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Circle Graphics
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.size(90.dp)) {
                            drawCircle(
                                color = Color.Gray.copy(alpha = 0.15f),
                                style = Stroke(width = 8.dp.toPx())
                            )
                            drawArc(
                                color = Color(0xFF10B981),
                                startAngle = -90f,
                                sweepAngle = (score * 3.6f),
                                useCenter = false,
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$score%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Weekly Score",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Water and Habits Trackers
                Column(
                    modifier = Modifier
                        .weight(1.0f)
                        .padding(start = 20.dp)
                ) {
                    Text(
                        text = "Today's Hydration",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "$todayWater ml / ${profile?.targetWater ?: 2500} ml drank",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.67f)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { (todayWater.toFloat() / (profile?.targetWater ?: 2500f).toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = Color(0xFF3B82F6),
                        trackColor = Color.LightGray.copy(alpha = 0.3f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.addWater(250) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("add_water_250")
                        ) {
                            Text("+250ml", fontSize = 10.sp, color = Color.White)
                        }

                        Button(
                            onClick = { viewModel.addWater(500) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("add_water_500")
                        ) {
                            Text("+500ml", fontSize = 10.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Stats profile settings expander
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your Wellness Parameters",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = { showProfileEditor = !showProfileEditor }) {
                        Icon(
                            imageVector = if (showProfileEditor) Icons.Default.Close else Icons.Default.Settings,
                            contentDescription = "Edit parameters"
                        )
                    }
                }

                if (showProfileEditor) {
                    var heightStr by remember { mutableStateOf(profile?.height?.toString() ?: "170") }
                    var weightStr by remember { mutableStateOf(profile?.weight?.toString() ?: "65") }
                    var ageStr by remember { mutableStateOf(profile?.age?.toString() ?: "26") }
                    var pref by remember { mutableStateOf(profile?.dietPreference ?: "Vegetarian Food (Indian)") }
                    var activity by remember { mutableStateOf(profile?.activityLevel ?: "Moderate") }

                    OutlinedTextField(
                        value = heightStr,
                        onValueChange = { heightStr = it },
                        label = { Text("Height (cm)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = weightStr,
                        onValueChange = { weightStr = it },
                        label = { Text("Weight (kg)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ageStr,
                        onValueChange = { ageStr = it },
                        label = { Text("Age") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Diet Preference:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        val prefsList = listOf("Vegetarian Food (Indian)", "Balanced Diet", "Vegan", "Keto/Low Carb")
                        prefsList.forEach { p ->
                            FilterChip(
                                selected = pref == p,
                                onClick = { pref = p },
                                label = { Text(p) },
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.savePhysicalProfile(
                                height = heightStr.toFloatOrNull() ?: 170f,
                                weight = weightStr.toFloatOrNull() ?: 65f,
                                age = ageStr.toIntOrNull() ?: 26,
                                activityLevel = activity,
                                dietPref = pref
                            )
                            showProfileEditor = false
                        },
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .testTag("save_profile_button")
                    ) {
                        Text("Recalculate Indices & Save")
                    }
                } else {
                    val actualBmi = if (profile != null && profile!!.height > 0) {
                        profile!!.weight / ((profile!!.height / 100f) * (profile!!.height / 100f))
                    } else 22.4f

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("BMI Index", fontSize = 12.sp, color = Color.Gray)
                            Text(String.format("%.1f", actualBmi), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = when {
                                    actualBmi < 18.5 -> "Underweight"
                                    actualBmi < 25 -> "Healthy Weight"
                                    else -> "Overweight"
                                },
                                color = if (actualBmi in 18.5..25.0) MaterialTheme.colorScheme.primary else Color.Red,
                                fontSize = 11.sp
                            )
                        }
                        Column {
                            Text("Target Calories", fontSize = 12.sp, color = Color.Gray)
                            Text("${profile?.targetCalories ?: 2100} kcal/day", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        Column {
                            Text("Preference", fontSize = 12.sp, color = Color.Gray)
                            Text(profile?.dietPreference?.take(16) ?: "Indian Veg", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Selection Grid for modular specialty features
        Text(
            text = "NutriMind Specialists & Tools",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val sections = listOf(
                "scanner" to "AI Food Scanner",
                "voice_agent" to "🎙️ AI Voice Coach",
                "hair_care" to "Hair specialist",
                "eye_care" to "Screen & Eye protection",
                "calculators" to "Smart Calculators",
                "reports" to "Weekly PDF Report"
            )

            sections.forEach { (key, name) ->
                val isActive = activeSubTool == key
                Button(
                    onClick = { activeSubTool = key },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.border(
                        1.dp,
                        if (isActive) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        RoundedCornerShape(12.dp)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = name,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sub Tools Container dynamically rendering the chosen specialization tab
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    RoundedCornerShape(16.dp)
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                when (activeSubTool) {
                    "scanner" -> ActiveScannerWidget(viewModel = viewModel)
                    "voice_agent" -> AIVoiceCoachWidget(viewModel = viewModel)
                    "hair_care" -> HairSpecialistWidget(viewModel = viewModel)
                    "eye_care" -> EyeScreenCareWidget(viewModel = viewModel)
                    "calculators" -> EmbeddedCalculatorsWidget()
                    "reports" -> PDFReportGeneratorWidget(viewModel = viewModel)
                    else -> Text("Select a tool above to start analyzing nutrition.")
                }
            }
        }
    }
}

// --- AI VOICE AGENT PREMIUM WIDGET ---
@Composable
fun AIVoiceCoachWidget(viewModel: MainViewModel) {
    val activeLanguage by viewModel.voiceLanguage.collectAsStateWithLifecycle()
    val activeEmotion by viewModel.voiceEmotion.collectAsStateWithLifecycle()
    val outputSpeech by viewModel.voiceOutputText.collectAsStateWithLifecycle()
    val isThinking by viewModel.isVoiceThinking.collectAsStateWithLifecycle()
    val isVoiceMuted by viewModel.isVoiceMuted.collectAsStateWithLifecycle()

    var textInputSpeech by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "🎙️ AI Voice Specialist & Coach",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Initiate instant spoken wellness diagnostics. Talk naturally with our dialect-aware voice model for real-time body coaching feedback.",
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Configuration Control Panel: Dialects selector
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Select Coach Dialect Voice:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val langs = listOf("en" to "English 🇺🇸", "hi" to "Hindi 🇮🇳", "bn" to "Bengali 🇮🇳")
                    langs.forEach { (code, name) ->
                        val isSel = activeLanguage == code
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { viewModel.updateVoiceLanguage(code) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Animated sound wave visualizer box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Color.Black, RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(12.dp)
            ) {
                // Wave pulsing bars generator using fully qualified core animations
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "Waves")
                    val heights = listOf(14.dp, 28.dp, 45.dp, 35.dp, 18.dp, 28.dp, 40.dp, 12.dp)
                    
                    heights.forEachIndexed { idx, baseHeight ->
                        val animScale by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1.7f,
                            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                animation = androidx.compose.animation.core.tween(
                                    durationMillis = 350 + idx * 75,
                                    easing = androidx.compose.animation.core.LinearEasing
                                ),
                                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                            ),
                            label = "PulseBar$idx"
                        )
                        val activeHeight = if (isThinking) {
                            baseHeight * animScale
                        } else if (outputSpeech.isNotEmpty() && !isThinking) {
                            baseHeight * 0.7f
                        } else {
                            4.dp
                        }
                        val color = if (isThinking) {
                            MaterialTheme.colorScheme.primary
                        } else if (isVoiceMuted) {
                            Color.Red
                        } else {
                            Color(0xFF10B981)
                        }

                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(activeHeight)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = if (isThinking) "Coach thinking dynamically..." else "ACTIVE SPEECH EMOTION STATE: ${activeEmotion.uppercase()}",
                    fontSize = 11.sp,
                    color = if (isThinking) MaterialTheme.colorScheme.secondary else Color(0xFF10B981),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Simulated Voice Output dialog card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🗣️ Coach NutriMind Speech System Output:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    IconButton(
                        onClick = { viewModel.toggleVoiceMute() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isVoiceMuted) Icons.Default.Close else Icons.Default.Check,
                            contentDescription = "Mute",
                            tint = if (isVoiceMuted) Color.Red else Color(0xFF1EC274),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        text = outputSpeech,
                        fontSize = 12.sp,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Preconfigured query triggers chips
        Text("Trigger Speech Command Presets:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val chips = listOf(
                "How to boost deep sleep recovery cycle?",
                "Superfoods for dry skin diagnosis.",
                "Custom diet guide for hair care growth.",
                "Morning hydration & chromium metrics checklist."
            )
            chips.forEach { chipQuery ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { viewModel.simulateVoiceInteraction(chipQuery) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(chipQuery, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Manual micro utterance box
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInputSpeech,
                onValueChange = { textInputSpeech = it },
                placeholder = { Text("Speak custom inquiry or ask about sleep care...") },
                label = { Text("Utterance simulator input") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(6.dp))
            Button(
                onClick = {
                    if (textInputSpeech.isNotBlank()) {
                        viewModel.simulateVoiceInteraction(textInputSpeech)
                        textInputSpeech = ""
                    }
                },
                modifier = Modifier.height(56.dp)
            ) {
                Text("Speak")
            }
        }
    }
}

@Composable
fun CameraPreviewView(
    imageCapture: ImageCapture,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var hasError by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (hasError) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Camera feedback failed. Try manual upload.", color = Color.White, textAlign = TextAlign.Center)
            }
        } else {
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)
            
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                } catch (exc: Exception) {
                    hasError = true
                }
            }, executor)
            previewView
        },
        modifier = Modifier.fillMaxSize(),
        onRelease = {
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            } catch (_: Exception) {}
        }
    )
        }
    }
}

// --- SUB TOOL 1: SPECIALIST SCANNER VIEW ---
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ActiveScannerWidget(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val logs by viewModel.loggedMeals.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanningFood.collectAsStateWithLifecycle()
    val response by viewModel.scannedFoodOutput.collectAsStateWithLifecycle()
    val uploadProgress by viewModel.uploadProgress.collectAsStateWithLifecycle()
    val cameraFlash by viewModel.cameraFlashEnabled.collectAsStateWithLifecycle()
    val imageUriState by viewModel.scannedFoodImageUri.collectAsStateWithLifecycle()

    var customMealInput by remember { mutableStateOf("") }
    var inputModeIsCamera by remember { mutableStateOf(true) } // true: Camera Scanner view, false: Gallery Upload view
    var selectedPhotoName by remember { mutableStateOf("Fresh Fruits Almonds Quinoa Salad Bowl") }
    var autofocusState by remember { mutableStateOf(true) }
    var isImageCropped by remember { mutableStateOf(false) }

    // Permissions
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    
    // Gallery Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.setScannedFoodImageUri(uri.toString())
                viewModel.scanFoodImageWithUri(uri, "Gallery Uploaded Dish")
            }
        }
    )

    // Camera Capture Launcher
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { ImageCapture.Builder().build() }
    
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
    
    fun takePhoto() {
        val photoFile = File(
            context.cacheDir,
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis()) + ".jpg"
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    viewModel.setScannedFoodImageUri(savedUri.toString())
                    viewModel.scanFoodImageWithUri(savedUri, "Camera Captured Meal")
                }
                override fun onError(exc: ImageCaptureException) {
                    viewModel.triggerGlobalAlert("Camera Capture Error: ${exc.message}")
                }
            }
        )
    }

    Text(
        text = "📸 AI Camera Food Scanner & Analyzer",
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        text = "Scan meals in real time using the live viewport analyzer or upload your dishes directly from your health gallery.",
        fontSize = 12.sp,
        color = Color.Gray,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    // Selector: Live Camera Preview vs Gallery Explorer
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val modes = listOf(true to "📹 Live Camera Eye", false to "🖼️ Gallery Uploads")
        modes.forEach { (mode, text) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (inputModeIsCamera == mode) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { inputModeIsCamera = mode }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (inputModeIsCamera == mode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // VIEWPORT ACCORDING TO SELECTED MODE
    if (inputModeIsCamera) {
        // --- 📹 LIVE CAMERA PREVIEW ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (cameraPermissionState.status.isGranted) {
                    CameraPreviewView(
                        imageCapture = imageCapture,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Viewfinder Brackets Overlay
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .border(2.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .align(Alignment.Center)
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Camera access is needed for live scanning.", color = Color.White, textAlign = TextAlign.Center, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                            Text("Request Permission")
                        }
                    }
                }

                // HUD Overlay
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(if (cameraPermissionState.status.isGranted) Color.Red else Color.Gray, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("LIVE VIEWPORT", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    if (cameraPermissionState.status.isGranted) {
                        Button(
                            onClick = { takePhoto() },
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                            shape = CircleShape,
                            contentPadding = PaddingValues(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Capture", tint = Color.Black, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("Active Image Selection Source:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Text(text = if (imageUriState != null) "Using: $imageUriState" else "No image captured yet (Using simulation defaults below)", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
        
        Spacer(modifier = Modifier.height(8.dp))
        // Simulated items as fallbacks
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val cameraPlates = listOf(
                "Fresh Spinach Strawberries Goat Cheese Salad" to "🥗 Fruit Salad",
                "Full Extra-Cheese Pepperoni Pizza Slice" to "🍕 Pepperoni Pizza",
                "High Protein Grilled Chicken Broccoli Jasmine Rice" to "🍗 Chicken Plate"
            )
            cameraPlates.forEach { (fullName, display) ->
                val isSelected = selectedPhotoName == fullName
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { selectedPhotoName = fullName }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(text = display, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { viewModel.scanFoodImageWithMode(selectedPhotoName, true) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Analyze Selected Item Metadata")
        }

    } else {
        // --- 🖼️ REAL GALLERY UPLOAD ---
        Card(
            modifier = Modifier.fillMaxWidth().clickable { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Tap to Select from Gallery", fontWeight = FontWeight.Bold)
                Text(text = if (imageUriState != null) "Selected: $imageUriState" else "No file selected", fontSize = 10.sp, color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { 
                if (imageUriState != null) {
                   viewModel.scanFoodImageWithUri(Uri.parse(imageUriState), "Gallery Upload")
                } else {
                   viewModel.scanFoodImageWithMode(selectedPhotoName, true) 
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Analyze Gallery Media Asset Now")
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // MANUAL LOGGING TEXT ENTERING
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Or Log Diet Plan/Food Manually by Text Name:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = customMealInput,
                    onValueChange = { customMealInput = it },
                    placeholder = { Text("e.g. Avocado Toast with Egg") },
                    modifier = Modifier.weight(1.0f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        viewModel.scanFoodImageWithMode(customMealInput, false)
                        customMealInput = ""
                    },
                    modifier = Modifier.height(56.dp)
                ) {
                    Icon(Icons.Default.Add, "Scan")
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // UPLOAD PROGRESS BAR SIMULATOR INDICATOR
    if (uploadProgress != null) {
        val progressVal = uploadProgress ?: 0f
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Active Progression Log...", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("${(progressVal * 100).toInt()}% Done", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progressVal },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.LightGray.copy(alpha = 0.3f)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    // MAIN ANALYSIS SCAN REPORT INTERFACE
    if (isScanning && uploadProgress == null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text("NutriMind AI is parsing macronutrient indices...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
    }

    if (response.isNotEmpty()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier
                .padding(vertical = 12.dp)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📋 AI DETECTED WELLNESS REPORT:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("ACTIVE REPORT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(response, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }

    // List of scanned food history logs
    Text("My Scanned Food Log History:", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
    if (logs.isEmpty()) {
        Text("No products analyzed yet today. Toggle presets above to log analytics data points.", fontSize = 12.sp, color = Color.Gray)
    } else {
        logs.forEach { meal ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .background(Color.Gray.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1.0f)) {
                    Text(meal.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${meal.calories} kcal | ${meal.protein}g protein", fontSize = 12.sp, color = Color.Gray)
                }
                Box(
                    modifier = Modifier
                        .background(
                            if (meal.isHealthy) Color(0xFF10B981).copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.2f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (meal.isHealthy) "Healthy (${meal.healthScore})" else "Caution (${meal.healthScore})",
                        color = if (meal.isHealthy) Color(0xFF10B981) else Color.Red,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = { viewModel.deleteLoggedMeal(meal.id) }) {
                    Icon(Icons.Default.Delete, "Delete", tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// --- SUB TOOL 2: HAIR CARE SPECIALIST VIEW ---
@Composable
fun HairSpecialistWidget(viewModel: MainViewModel) {
    var hairType by remember { mutableStateOf("Normal Scalp") }
    val logs by viewModel.loggedMeals.collectAsStateWithLifecycle()

    Text(
        text = "💇 Hair Fall & Scalp Wellness specialist",
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        text = "Get diagnostic foods highlighting biotin and organic proteins for hair-follicle growth.",
        fontSize = 12.sp,
        color = Color.Gray,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    Text("Select Scalp / Hair Type Category:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
    val hairTypesList = listOf("Oily Scalp", "Dry & Brittle", "Thinning Root", "Curly & Frizz")
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        hairTypesList.forEach { t ->
            FilterChip(
                selected = hairType == t,
                onClick = { hairType = t },
                label = { Text(t) }
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text("Recommended Hair Growth Foods:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val hairFoods = listOf(
            "🥚 Eggs" to "Rich in protein and biotin. Keratin foundation helper.",
            "🥬 Spinach" to "High in iron, folate, and Vitamin A to keep scalp moist.",
            "🌰 Almonds & Walnuts" to "Packed with Selenium, Zinc, and healthy Omega-3 fats.",
            "🥑 Avocados" to "Loaded with Vitamin E, improving antioxidants protection."
        )

        hairFoods.forEach { (title, desc) ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(desc, fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Scalp treatment reminder checklist card
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Weekly Scalp Action Checklist:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("✅ 1. scalp Oil massage with rosemary essence", fontSize = 11.sp)
            Text("✅ 2. Drink 3 liters of vitamin, biotin-enriched water.", fontSize = 11.sp)
            Text("✅ 3. Avoid hot hair dry styling tool this week.", fontSize = 11.sp)
        }
    }
}

// --- SUB TOOL 3: EYE SCREEN CARE SPECIALIST VIEW ---
@Composable
fun EyeScreenCareWidget(viewModel: MainViewModel) {
    var activeTimerSeconds by remember { mutableStateOf(0) }
    var runningExercise by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(activeTimerSeconds, runningExercise) {
        if (runningExercise && activeTimerSeconds > 0) {
            delay(1000)
            activeTimerSeconds -= 1
        } else if (activeTimerSeconds == 0) {
            runningExercise = false
        }
    }

    Text(
        text = "👁️ Screen Care & Blue-Light protection",
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        text = "Frequent monitor use causes fatigue. Apply the 20-20-20 rule to restore eye wellness.",
        fontSize = 12.sp,
        color = Color.Gray,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Interactive Screen Break Timer",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                text = "Look at an object 20 feet away for 20 seconds.",
                fontSize = 11.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (activeTimerSeconds > 0) "00:${String.format("%02d", activeTimerSeconds)}" else "Break Ready!",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = if (activeTimerSeconds > 0) MaterialTheme.colorScheme.primary else Color.Gray
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    activeTimerSeconds = 20
                    runningExercise = true
                },
                modifier = Modifier.height(36.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Text("Start Eye Break", fontSize = 12.sp)
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text("Eye hydration foods list:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
    Text("• 🥕 Carrots & Sweet potato (High Vitamin A)", fontSize = 11.sp, color = Color.Gray)
    Text("• 🐟 Wild salmon - Omega 3 supports dry tear-duct hydration.", fontSize = 11.sp, color = Color.Gray)
    Text("• 🕶️ Setup night-shield blue filter on device settings.", fontSize = 11.sp, color = Color.Gray)
}

// --- SUB TOOL 4: SMART CALCULATORS VIEW ---
@Composable
fun EmbeddedCalculatorsWidget() {
    var picker by remember { mutableStateOf("bmi") } // bmi, water, calorie

    Text(
        text = "🧮 Wellness Calculators Suite",
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )

    TabRow(
        selectedTabIndex = when (picker) {
            "bmi" -> 0
            "water" -> 1
            else -> 2
        },
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        Tab(selected = picker == "bmi", onClick = { picker = "bmi" }, text = { Text("BMI Index", fontSize = 11.sp) })
        Tab(selected = picker == "water", onClick = { picker = "water" }, text = { Text("Water Need", fontSize = 11.sp) })
        Tab(selected = picker == "calorie", onClick = { picker = "calorie" }, text = { Text("Calorie Limit", fontSize = 11.sp) })
    }

    when (picker) {
        "bmi" -> {
            var hInput by remember { mutableStateOf("175") }
            var wInput by remember { mutableStateOf("70") }
            val h = hInput.toFloatOrNull() ?: 175f
            val w = wInput.toFloatOrNull() ?: 70f
            val calculated = w / ((h / 100f) * (h / 100f))

            OutlinedTextField(value = hInput, onValueChange = { hInput = it }, label = { Text("Height (cm)") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(value = wInput, onValueChange = { wInput = it }, label = { Text("Weight (kg)") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Your BMI index is: " + String.format("%.2f", calculated) + " (" + (if (calculated < 18.5) "Underweight" else if (calculated < 25) "Perfect weight" else "Overweight") + ")",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        "water" -> {
            var weightStr by remember { mutableStateOf("70") }
            val computedWater = (weightStr.toFloatOrNull() ?: 70f) * 35

            OutlinedTextField(value = weightStr, onValueChange = { weightStr = it }, label = { Text("Body weight (kg)") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Minimum suggested water hydration intake: ${computedWater.toInt()} ml/day",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF3B82F6)
            )
        }
        "calorie" -> {
            var activeAge by remember { mutableStateOf("28") }
            var activeWeight by remember { mutableStateOf("70") }
            val calorieBase = 10 * (activeWeight.toFloatOrNull() ?: 70f) + (6.25f * 175) - (5 * (activeAge.toIntOrNull() ?: 28)) + 5

            OutlinedTextField(value = activeAge, onValueChange = { activeAge = it }, label = { Text("Age in Years") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(value = activeWeight, onValueChange = { activeWeight = it }, label = { Text("Weight in kg") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Ideal Active metabolic calorie limit: ${calorieBase.toInt()} kcal to sustain balance.",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// --- SUB TOOL 5: PDF DATA REPORT GENERATOR VIEW ---
@Composable
fun PDFReportGeneratorWidget(viewModel: MainViewModel) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val waterCount by viewModel.currentDayWater.collectAsStateWithLifecycle()
    val foodsScanned by viewModel.loggedMeals.collectAsStateWithLifecycle()

    var compiledReportText by remember { mutableStateOf("") }

    Text(
        text = "📄 Export compiled PDF health summary",
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        text = "Generates clinical styled report containing diet plans log, deficiencies checker inputs ready to share.",
        fontSize = 11.sp,
        color = Color.Gray,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    Button(
        onClick = {
            compiledReportText = """
                ==============================
                NUTRIMIND AI HEALTH DIAGNOSTIC REPORT
                ==============================
                Issued Date: ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}
                Client Profile Profile: ${profile?.name ?: "Valued User"} (${profile?.email ?: "anonymous"})
                Authorized subscription Tier: ${profile?.plan ?: "Free"}
                
                METRICS SUMMARY:
                Height: ${profile?.height ?: 170.0} cm
                Weight: ${profile?.weight ?: 65.0} kg
                Diet Category: ${profile?.dietPreference ?: "Vegetarian Food (Indian)"}
                Target Calories Limit: ${profile?.targetCalories ?: 2100} kcal/day
                
                HYDRATION SCORE:
                Total ml Drank: $waterCount ml / ${profile?.targetWater ?: 2500} ml
                
                PRODUCTIVITY MEALS TRACKED TODAY:
                ${if (foodsScanned.isEmpty()) "• No custom foods logged." else foodsScanned.joinToString("\n") { "• " + it.name + " (" + it.calories + " kcal) -- " + (if (it.isHealthy) "Healthy" else "Caution") }}
                
                CLINICAL SPECIALIST REPORT CONTEXTS:
                "Clean food is medicine, focus on mental serenity."
                ==============================
            """.trimIndent()
            viewModel.triggerGlobalAlert("Health Report generated! Copy clipboard or PDF print ready.")
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Share, "Report")
        Spacer(modifier = Modifier.width(8.dp))
        Text("Generate compiled Report")
    }

    if (compiledReportText.isNotEmpty()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = compiledReportText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// --- TAB 2: AI SPECIALIST CHATBOT VIEW ---
@Composable
fun ChatbotScreen(viewModel: MainViewModel) {
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isChatLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()

    var textInput by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("en") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Chat screen top header with quick language selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "💬 NutriMind Wellness AI Chatbot",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val languages = listOf("en" to "English", "hi" to "हिन्दी", "bn" to "বাংলা")
                languages.forEach { (code, label) ->
                    val isSel = language == code
                    Button(
                        onClick = { language = code },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSel) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.2f)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

        // Chat messages scrollable frame
        Box(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxWidth()
        ) {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
            ) {
                items(messages.size) { index ->
                    val msg = messages[index]
                    val isMe = msg.isUser

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            ),
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isMe) 16.dp else 4.dp,
                                bottomEnd = if (isMe) 4.dp else 16.dp
                            ),
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = msg.sender,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = msg.message,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                if (isChatLoading) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "AI Nutritionist is formulating response...",
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(12.dp),
                                    color = Color.LightGray
                                )
                            }
                        }
                    }
                }
            }
        }

        // Send input container
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Ask about hair loss, dry eyes, high fat, sleep...") },
                modifier = Modifier
                    .weight(1.0f)
                    .testTag("chat_input_text_field"),
                shape = RoundedCornerShape(24.dp),
                maxLines = 3
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Micro simulated speech indicator button (Hindi / Bengali voice input simulator)
            var mockVoiceTypingActive by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            IconButton(
                onClick = {
                    mockVoiceTypingActive = true
                    textInput = "I need a high-protein Indian diet plan for weight loss"
                    scope.launch {
                        delay(1200)
                        mockVoiceTypingActive = false
                    }
                },
                modifier = Modifier
                    .background(
                        if (mockVoiceTypingActive) Color.Red.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Simulated Voice Search input",
                    tint = if (mockVoiceTypingActive) Color.Red else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        viewModel.askChatbot(textInput, language)
                        textInput = ""
                    }
                },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .testTag("chat_send_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

// --- TAB 3: DIAGNOSTICS & SYMPTOM CHECKER SCREEN ---
@Composable
fun SymptomCheckerScreen(viewModel: MainViewModel) {
    val output by viewModel.symptomAnalyzerOutput.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzingSymptoms.collectAsStateWithLifecycle()
    val logs by viewModel.symptomLogs.collectAsStateWithLifecycle()

    var customSymptomText by remember { mutableStateOf("") }
    val selectedCheckboxes = remember { mutableStateListOf<String>() }

    val coreSymptoms = listOf(
        "Hair Fall / Brittle Nails" to "Iron & Biotin Deficiency",
        "Dry Eyes / Screentime pain" to "Vitamin A & Blue Light strain",
        "Chronic Fatigue" to "Low Vitamin D or B12",
        "Dry & Flaky Skin" to "Deficit Fatty Acids & Zinc",
        "Insomnia & Bad Sleep" to "Magnesium Depleted State"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "🩺 Clinical Symptom & Deficiency Analyzer",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Select active symptoms below and let NutriMind formulate food and diet interventions.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        // Symptoms checklist grid
        Text("Interactive Symptom Checklist:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
            coreSymptoms.forEach { (sympName, diagnosis) ->
                val contains = selectedCheckboxes.contains(sympName)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (contains) selectedCheckboxes.remove(sympName)
                            else selectedCheckboxes.add(sympName)
                        }
                        .background(
                            if (contains) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Gray.copy(alpha = 0.03f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = contains,
                        onCheckedChange = { checked ->
                            if (checked == true) selectedCheckboxes.add(sympName)
                            else selectedCheckboxes.remove(sympName)
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(sympName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Triggers: $diagnosis", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Direct Text description box
        OutlinedTextField(
            value = customSymptomText,
            onValueChange = { customSymptomText = it },
            placeholder = { Text("Describe any other wellness anomalies (e.g., headache, muscle cramps after workout, etc.)") },
            label = { Text("Explain additional wellness symptoms") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("symptom_manual_input"),
            maxLines = 4
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val fullList = selectedCheckboxes.toMutableList()
                if (customSymptomText.isNotBlank()) {
                    fullList.add(customSymptomText)
                }
                if (fullList.isNotEmpty()) {
                    viewModel.analyzeNewSymptoms(fullList.joinToString(", "))
                    customSymptomText = ""
                } else {
                    viewModel.triggerGlobalAlert("Please check at least one symptom checker checkbox!")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("symptom_analyze_button")
        ) {
            Icon(Icons.Default.Spa, "Analyze")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Compute Therapeutic Diet Plan")
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (isAnalyzing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("NutriMind AI is parsing your health biomarkers...", fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        if (output.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("AI ANALYSIS & RECOMMENDATIONS:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(output, fontSize = 13.sp, lineHeight = 20.sp)
                }
            }
        }

        // Historical Symptoms Log checks
        Text("Historical Diagnostic Logs:", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 24.dp))
        if (logs.isEmpty()) {
            Text("Deficiency logs are empty. Start checking symptoms above.", fontSize = 12.sp, color = Color.Gray)
        } else {
            logs.take(3).forEach { log ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Log ID: #${log.id} | Checked: ${log.symptoms}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(log.generalAdvice.take(240) + "...", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

// --- TAB 4: SAAS ADMIN PANEL SCREEN ---
@Composable
fun AdminSaaSScreeen(viewModel: MainViewModel) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val tokenCount by viewModel.aiTokenCount.collectAsStateWithLifecycle()
    val activeBanner by viewModel.adminNotificationTopic.collectAsStateWithLifecycle()

    // Razorpay integrations states
    val txs by viewModel.transactions.collectAsStateWithLifecycle()
    val couponCode by viewModel.couponCode.collectAsStateWithLifecycle()
    val couponDiscountPercent by viewModel.couponDiscountPercent.collectAsStateWithLifecycle()
    val currentCountry by viewModel.detectedCountry.collectAsStateWithLifecycle()
    val currentCurrency by viewModel.detectedCurrency.collectAsStateWithLifecycle()
    val isPaying by viewModel.isProcessingPayment.collectAsStateWithLifecycle()

    var inputTopic by remember { mutableStateOf("") }
    var inputCoupon by remember { mutableStateOf("") }
    var selectedPlanTitle by remember { mutableStateOf("Yearly ProPlus Plan") }
    var showCheckoutModal by remember { mutableStateOf(false) }

    // Multi-country Pricing Config Map
    // (PlanTitle, Country) -> BaseAmount
    val monthlyBasePriceMap = mapOf(
        "India" to 299.0, "United States" to 9.99, "United Kingdom" to 7.99,
        "France" to 8.99, "UAE" to 39.0, "Singapore" to 14.99,
        "Australia" to 15.99, "Canada" to 13.99
    )
    val yearlyBasePriceMap = mapOf(
        "India" to 1999.0, "United States" to 79.99, "United Kingdom" to 69.99,
        "France" to 74.99, "UAE" to 299.0, "Singapore" to 119.99,
        "Australia" to 129.99, "Canada" to 109.99
    )

    val activeBasePrice = if (selectedPlanTitle.contains("Monthly")) {
        monthlyBasePriceMap[currentCountry] ?: 9.99
    } else {
        yearlyBasePriceMap[currentCountry] ?: 79.99
    }

    val activeDiscountedPrice = if (couponDiscountPercent > 0) {
        activeBasePrice * (1.0 - couponDiscountPercent / 100.0)
    } else {
        activeBasePrice
    }

    // Dynamic metrics calculation
    val totalEarningsStr = remember(txs) {
        val successOnly = txs.filter { it.transactionStatus == "Success" }
        val usdEquiv = successOnly.sumOf { tx ->
            val rate = when (tx.currency.uppercase()) {
                "INR" -> 1.0 / 83.0
                "GBP" -> 1.30
                "EUR" -> 1.10
                "AED" -> 1.0 / 3.67
                "SGD" -> 0.74
                "AUD" -> 0.66
                "CAD" -> 0.73
                else -> 1.0
            }
            tx.amount * rate
        }
        String.format("$%.2f USD equivalent (%d items)", usdEquiv, successOnly.size)
    }

    val countryRevenueList = remember(txs) {
        txs.filter { it.transactionStatus == "Success" }
            .groupBy { it.country }
            .map { entry ->
                val firstTx = entry.value.firstOrNull()
                val totalAmount = entry.value.sumOf { it.amount }
                val currency = firstTx?.currency ?: "USD"
                entry.key to "$currency ${String.format("%.2f", totalAmount)}"
            }
    }

    val activeSubscriptionsCount = remember(txs) {
        txs.filter { it.transactionStatus == "Success" }.size
    }

    val failedTransactions = remember(txs) {
        txs.filter { it.transactionStatus == "Failed" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "👑 NutriMind Premium SaaS Upgrades",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Invest in your cellular health. Access unlimited biometric charts, continuous symptom traces, and priority clinical counselor advice.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        // --- SECTION A: ACTIVE STATUS MATRIX & TRIAL PULSE ---
        val trialDaysVal by viewModel.trialDaysRemaining.collectAsStateWithLifecycle()
        val isExpiredVal by viewModel.isTrialExpired.collectAsStateWithLifecycle()

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("CURRENT ACCOUNT PROFILE STATUS:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Text(
                            text = if (profile?.plan != "Free") "⭐ Premium ${profile?.plan} Holder" else "🛡️ Standard Free Account",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = profile?.plan ?: "Free",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Trial Limit Info",
                        tint = if (isExpiredVal) Color.Red else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isExpiredVal) "🔴 14-Day Free Trial HAS EXPIRED. Upgrade now to preserve access."
                               else "⚡ Free Trial Status: $trialDaysVal days remaining of your 14-day free trial limit.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isExpiredVal) Color.Red else MaterialTheme.colorScheme.secondary
                    )
                }

                // Interactive Login Streak Mechanism
                val streakClaimed by viewModel.claimedStreakToday.collectAsStateWithLifecycle()
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("🔥 Daily Motivation Streak Logs", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Current Streak: ${profile?.streak ?: 1} days active. Claim daily +60 XP!", fontSize = 10.sp, color = Color.Gray)
                        }
                        Button(
                            onClick = { viewModel.claimDailyStreak() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (streakClaimed) Color.Gray else MaterialTheme.colorScheme.secondary
                            ),
                            enabled = !streakClaimed,
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(if (streakClaimed) "Claimed" else "Claim Goal", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- SECTION B: GLOBAL MULTI-CURRENCY RAZORPAY GATEWAY ---
        Text(
            text = "💳 Razorpay Gate Multi-Currency Checkout",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Pricing scales globally with automatic Razorpay currency adjustment based on select country presets.",
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Country Selection Chips
        Text("Select Payee Residence Country:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val countryList = listOf("India", "United States", "United Kingdom", "France", "UAE", "Singapore", "Australia", "Canada")
            countryList.forEach { country ->
                val isSel = currentCountry == country
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { viewModel.selectCountry(country) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = when (country) {
                            "India" -> "🇮🇳 India (INR)"
                            "United States" -> "🇺🇸 USA (USD)"
                            "United Kingdom" -> "🇬🇧 UK (GBP)"
                            "France" -> "🇫🇷 France (EUR)"
                            "UAE" -> "🇦🇪 UAE (AED)"
                            "Singapore" -> "🇸🇬 Singapore (SGD)"
                            "Australia" -> "🇦🇺 Australia (AUD)"
                            "Canada" -> "🇨🇦 Canada (CAD)"
                            else -> country
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Spacer(modifier = Modifier.height(10.dp))

        // Toggle Billing cycle (yearly billing gets massive discounts)
        var isYearlyBilling by remember { mutableStateOf(true) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Select Billing Interval Frequency:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Monthly",
                    fontSize = 11.sp,
                    fontWeight = if (!isYearlyBilling) FontWeight.Bold else FontWeight.Normal,
                    color = if (!isYearlyBilling) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.clickable { isYearlyBilling = false }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = isYearlyBilling,
                    onCheckedChange = { isYearlyBilling = it }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Yearly (Save 82%)",
                    fontSize = 11.sp,
                    fontWeight = if (isYearlyBilling) FontWeight.Bold else FontWeight.Normal,
                    color = if (isYearlyBilling) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.clickable { isYearlyBilling = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Price helper definition mapping custom plans
        val baseMonthly = monthlyBasePriceMap[currentCountry] ?: 9.99
        val baseYearly = yearlyBasePriceMap[currentCountry] ?: 79.99

        val activeBasePrice = remember(selectedPlanTitle, currentCountry, isYearlyBilling) {
            val base = if (isYearlyBilling) baseYearly else baseMonthly
            when {
                selectedPlanTitle.contains("Pro Health", ignoreCase = true) -> base
                selectedPlanTitle.contains("AI Coach", ignoreCase = true) || selectedPlanTitle.contains("Pro Plus", ignoreCase = true) -> base * 1.5
                selectedPlanTitle.contains("Elite", ignoreCase = true) -> base * 2.8
                else -> base
            }
        }

        val activeDiscountedPrice = if (couponDiscountPercent > 0) {
            activeBasePrice * (1.0 - couponDiscountPercent / 100.0)
        } else {
            activeBasePrice
        }

        // FEATURE 2: 4 SMART SUBSCRIPTION PLANS CARDS DISPLAY
        Text("Choose SaaS Plan Configuration (AI Tier level scaled):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))

        val planInfos = listOf(
            Triple(
                "🛡️ Free Starter 14-Day Limit Plan",
                "Free ($currentCurrency 0.00)",
                "14-day evaluation limits. Concise standard response structures (max 140 words). Basic food nutrient checks today."
            ),
            Triple(
                "⭐ Pro Health Plan Specialist",
                if (isYearlyBilling) "$currentCurrency ${String.format("%.2f", baseYearly / 12.0)}/mo billed yearly ($currentCurrency ${String.format("%.2f", baseYearly)})"
                else "$currentCurrency ${String.format("%.2f", baseMonthly)}/mo",
                "Unlimited scanning/symptoms. Detailed superfoods recommendations, custom vitamin checks, professional clinical style diet feedback list."
            ),
            Triple(
                "🔥 Pro Plus AI Coach Plan",
                if (isYearlyBilling) "$currentCurrency ${String.format("%.2f", (baseYearly * 1.5) / 12.0)}/mo billed yearly ($currentCurrency ${String.format("%.2f", baseYearly * 1.5)})"
                else "$currentCurrency ${String.format("%.2f", baseMonthly * 1.5)}/mo",
                "Empathic motivational mentor tone. Custom sleep and hydration scheduling triggers, active metabolic chronobiology timing suggestions."
            ),
            Triple(
                "👑 Elite Transformation Mentor",
                if (isYearlyBilling) "$currentCurrency ${String.format("%.2f", (baseYearly * 2.8) / 12.0)}/mo billed yearly ($currentCurrency ${String.format("%.2f", baseYearly * 2.8)})"
                else "$currentCurrency ${String.format("%.2f", baseMonthly * 2.8)}/mo",
                "The pinnacle. Active Long-term memory profile binding (demographics weight, allergies parameters, style). Instant executive private coach lines."
            )
        )

        planInfos.forEach { (title, subtitle, desc) ->
            val isSel = selectedPlanTitle == title
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { selectedPlanTitle = title }
                    .border(
                        2.dp,
                        if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent,
                        RoundedCornerShape(12.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                                     else MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(subtitle, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(desc, fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // FEATURE 5: AI UPGRADE PERSUASION ATTRACTION SYSTEMS (Locked previews & Comparison list)
        
        // 1. Comparison checklist card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("📋 Feature Matrix Eligibility:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                val comparisons = listOf(
                    "Daily Symptom Scans Check" to "5 Checks/Day | Unlimited Pro | Unlimited ProPlus | Unlimited Elite",
                    "AI Output Intelligence words" to "Max 140 words | Full Tech Detail | Chronobiology scheduling | Full Client Memory",
                    "Simulated AI Voice Companion" to "Blocked | 2 mins/Day | Unlimited | Unlimited Voice with 3 Dialects",
                    "Gamification Streak bonus" to "60 XP claim | 60 XP claim + Multiplier | 80 XP Bonus | 150 Elite XP daily",
                    "Long-Term AI memory binding" to "No Profile Memory | Standard profile | Coach profile | Full memory context"
                )
                comparisons.forEach { (feature, coverage) ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(feature, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                        Text(coverage, fontSize = 10.sp, color = Color.Gray)
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.15f), modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Beautiful Locked Feature Blur simulation card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🔒 EXTENDED BIOMETRICS CHRONOSHIFT DIET CHART",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                // Blurred style text
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(2.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Breakfast: Egg Scramble - peak glucose timing 07:44 AM", fontSize = 11.sp, color = Color.DarkGray, fontWeight = FontWeight.W300)
                        Text("Lunch: Lentil quinoa mix - chromium assimilation index level 98%", fontSize = 11.sp, color = Color.DarkGray, fontWeight = FontWeight.W300)
                    }
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "PREMIUM PREVIEW LOCKED",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }
                Text(
                    text = "Sarah restored gut balance, glowing skin, and stopped custom hair fall in 24 days using Elite Transformation memories.",
                    fontSize = 10.sp,
                    color = Color.LightGray,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Coupon code input block
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputCoupon,
                onValueChange = { inputCoupon = it },
                label = { Text("Enter Promo Coupon") },
                placeholder = { Text("e.g. NUTRIMIND20, NUTRIMIND50") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val valid = viewModel.applyPromoCoupon(inputCoupon)
                    if (valid) inputCoupon = ""
                },
                modifier = Modifier.height(56.dp)
            ) {
                Text("Apply")
            }
        }

        if (couponDiscountPercent > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Applied active discount: $couponCode ($couponDiscountPercent% OFF)",
                    color = Color(0xFF10B981),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { viewModel.clearPromoCoupon() }) {
                    Text("Remove", fontSize = 11.sp, color = Color.Red)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Razorpay payment checkout execution trigger
        Button(
            onClick = {
                if (selectedPlanTitle.contains("Free", ignoreCase = true)) {
                    viewModel.updateSubscriptionPlan("Free")
                } else {
                    showCheckoutModal = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Star, "Razorpay Check")
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (selectedPlanTitle.contains("Free", ignoreCase = true)) "Revert to Free starter"
                else "Pay $currentCurrency ${String.format("%.2f", activeDiscountedPrice)} via Razorpay"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- SECTION C: SECURE RAZORPAY CHECKOUT POPUP MODAL DIALOG ---
        if (showCheckoutModal) {
            AlertDialog(
                onDismissRequest = { showCheckoutModal = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("国民 💳 Razorpay Secure checkout", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Checkout Sandbox Simulator. Secure verification of Razorpay signatures & auto webhook fulfillment is simulated.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f), thickness = 1.dp)

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Active Subscription", fontSize = 12.sp, color = Color.Gray)
                            Text(selectedPlanTitle, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Payer Country / Currency", fontSize = 12.sp, color = Color.Gray)
                            Text("$currentCountry / $currentCurrency", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Plan Base Price", fontSize = 12.sp, color = Color.Gray)
                            Text("$currentCurrency ${String.format("%.2f", activeBasePrice)}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        if (couponDiscountPercent > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Applied Coupon ($couponCode)", fontSize = 12.sp, color = Color(0xFF10B981))
                                Text("-$couponDiscountPercent%", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF10B981))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Payable Amount", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("$currentCurrency ${String.format("%.2f", activeDiscountedPrice)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val adminPaymentSettings by viewModel.adminPaymentSettings.collectAsStateWithLifecycle()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (adminPaymentSettings.isTestMode) Color(0xFFFEF3C7) else Color(0xFFD1FAE5)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (adminPaymentSettings.isTestMode) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (adminPaymentSettings.isTestMode) Color(0xFF92400E) else Color(0xFF065F46),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (adminPaymentSettings.isTestMode) 
                                        "Payment Gateway: SANDBOX MODE (Simulated)" 
                                        else "Payment Gateway: LIVE MODE (Real Transaction)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (adminPaymentSettings.isTestMode) Color(0xFF92400E) else Color(0xFF065F46)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Select Secure Payment Method via Razorpay:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isPaying) {
                            val qrUrl by viewModel.displayedPaymentQr.collectAsStateWithLifecycle()
                            
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (qrUrl != null) {
                                    Text("Dynamic Razorpay QR", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Card(
                                        modifier = Modifier.size(200.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                    ) {
                                        AsyncImage(
                                            model = qrUrl,
                                            contentDescription = "Payment QR",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Scan with any UPI App to Pay", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text("Expires in 04:59", fontSize = 10.sp, color = Color.Red)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { 1f }, // Fixed progress for expiration timer visual
                                        modifier = Modifier.width(180.dp)
                                    )
                                    Text("Waiting for payment detection...", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(36.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Verifying Secure Transaction...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Please do not close this window", fontSize = 10.sp, color = Color.Gray)
                                }
                            }
                        } else {
                            val paymentMethods = listOf(
                                Triple("UPI", "Pay via Google Pay, PhonePe, Paytm", Icons.Default.AddCircle),
                                Triple("QR", "Scan Dynamic QR to Pay", Icons.Default.Search),
                                Triple("Card", "Credit / Debit / International Cards", Icons.Default.ShoppingCart),
                                Triple("Netbanking", "Pay via all Indian/Global Banks", Icons.Default.Star),
                                Triple("Wallet", "Amazon Pay, Mobikwik, etc.", Icons.Default.PlayArrow)
                            )

                            paymentMethods.forEach { (method, sub, icon) ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            viewModel.processSecurePayment(
                                                planTitle = selectedPlanTitle,
                                                baseAmount = activeBasePrice,
                                                currency = currentCurrency,
                                                country = currentCountry,
                                                paymentMethod = method,
                                                onSuccess = { showCheckoutModal = false }
                                            )
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(method, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text(sub, fontSize = 10.sp, color = Color.Gray)
                                        }
                                        Spacer(modifier = Modifier.weight(1f))
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(
                                onClick = {
                                    viewModel.registerFailedPayment(selectedPlanTitle, activeBasePrice, currentCurrency, currentCountry)
                                    showCheckoutModal = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Simulate Test Transaction Failure", color = Color.Red, fontSize = 10.sp)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showCheckoutModal = false }) {
                        Text("Cancel Checkout")
                    }
                }
            )
        }

        // --- SECTION D: ADMIN PAYMENT ANALYTICS DASHBOARD panel ---
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "📊 Admin Payment Analytics Dashboard",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Aggregated business conversion indices parsed securely from Room transactions logs.",
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Total Earnings Valuation (Success only):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Text(totalEarningsStr, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(10.dp))

                Text("Dynamic Subscriptions Tracker:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Text("• Enrolled Active subscribers: $activeSubscriptionsCount users", fontSize = 12.sp, color = Color.Gray)
                Text("• Total API Tokens: $tokenCount tokens used", fontSize = 12.sp, color = Color.Gray)

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(10.dp))

                // Revenue by Country Breakdown
                Text("Revenue Partition by Payee Country:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                if (countryRevenueList.isEmpty()) {
                    Text("No logs gathered yet.", fontSize = 11.sp, color = Color.Gray)
                } else {
                    countryRevenueList.forEach { (country, revenue) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(country, fontSize = 12.sp, color = Color.Gray)
                            Text(revenue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                if (failedTransactions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Failed Payment Log History (${failedTransactions.size} reports):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Red)
                    failedTransactions.take(3).forEach { ftx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Red.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Order ID: ${ftx.orderId}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Payer: ${ftx.userEmail}", fontSize = 9.sp, color = Color.Gray)
                            }
                            Text("-${ftx.currency} ${String.format("%.2f", ftx.amount)}", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // History logs items list details
        Text("Detailed Transaction Audit History:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (txs.isEmpty()) {
            Text("No transactions logged yet in the database.", fontSize = 11.sp, color = Color.Gray)
        } else {
            txs.take(8).forEach { tx ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Color.Gray.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Invoice No: ${tx.invoiceDetails}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("User: ${tx.userEmail} | ${tx.country}", fontSize = 10.sp, color = Color.Gray)
                        Text("Status: ${tx.transactionStatus} | Plan: ${tx.subscriptionType}", fontSize = 10.sp, color = if (tx.transactionStatus == "Success") Color(0xFF10B981) else Color.Red)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${tx.currency} ${String.format("%.2f", tx.amount)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (tx.transactionStatus == "Success") MaterialTheme.colorScheme.primary else Color.Red
                        )
                        if (tx.transactionStatus == "Success") {
                            TextButton(
                                onClick = { viewModel.refundTransaction(tx.paymentId) },
                                modifier = Modifier.height(24.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Issue Refund", fontSize = 9.sp, color = Color.Red)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- USER TRANSACTION HISTORY LINK ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("🗂️ My Payment History & Invoices", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("View your active receipts, download invoices, and manage renewal status.", fontSize = 11.sp, color = Color.Gray)
                
                Spacer(modifier = Modifier.height(10.dp))
                
                val userTxs = txs.filter { it.userEmail == profile?.email }
                if (userTxs.isEmpty()) {
                    Text("No transactions found yet.", fontSize = 10.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                } else {
                    userTxs.take(3).forEach { tx ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("${tx.invoiceDetails} - ${tx.subscriptionType}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(SimpleDateFormat("MMM dd, yyyy", Locale.US).format(tx.timestamp), fontSize = 10.sp, color = Color.Gray)
                        }
                        Text("${tx.currency} ${String.format("%.2f", tx.amount)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (tx.transactionStatus == "Success") Color(0xFF10B981) else Color.Red)
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

        // --- ORIGINAL ADMIN NOTIFICATION BROADCAST SIMULATOR ---
        Text(
            text = "⚙️ Admin Control Board & Broadcast",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // KPIs Metrics Section
                Text("📈 Live SaaS Business KPI Diagnostics:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                val conversionVal by viewModel.adminConversions.collectAsStateWithLifecycle()
                val churnRateVal by viewModel.adminChurnRate.collectAsStateWithLifecycle()
                val totalRev by viewModel.totalRevenue.collectAsStateWithLifecycle()
                val activeSubs by viewModel.activeSubscriptionsCount.collectAsStateWithLifecycle()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Lifetime Revenue", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text("$currentCurrency ${String.format("%.2f", totalRev)}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Active Clients", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text("$activeSubs", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Conversion Ratio", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text("$conversionVal%", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Churn Rate Ratio", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text("$churnRateVal%", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.Red)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(10.dp))

                // Global Payment Infrastructure Configuration Section
                val paySettings by viewModel.adminPaymentSettings.collectAsStateWithLifecycle()
                Text("🏦 Global Payment Infrastructure Configuration:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Securely manage Razorpay & Future Stripe credentials:", fontSize = 10.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = paySettings.razorpayKeyId,
                    onValueChange = { viewModel.updateAdminPaymentSettings(paySettings.copy(razorpayKeyId = it)) },
                    label = { Text("Razorpay Key ID", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = paySettings.razorpayKeySecret,
                    onValueChange = { viewModel.updateAdminPaymentSettings(paySettings.copy(razorpayKeySecret = it)) },
                    label = { Text("Razorpay Secret Key", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = paySettings.isTestMode, onCheckedChange = { viewModel.updateAdminPaymentSettings(paySettings.copy(isTestMode = it)) })
                    Text(" Enable Sandbox / Test Mode", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                   Switch(checked = paySettings.isRazorpayEnabled, onCheckedChange = { viewModel.updateAdminPaymentSettings(paySettings.copy(isRazorpayEnabled = it)) })
                   Text(" Primary Gateway: Razorpay", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                   Switch(checked = paySettings.isStripeEnabled, onCheckedChange = { viewModel.updateAdminPaymentSettings(paySettings.copy(isStripeEnabled = it)) })
                   Text(" Secondary Gateway: Stripe (Locked)", fontSize = 12.sp, color = Color.Gray)
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(14.dp))

                // Config Limits adjustment section
                Text("⚙️ Dynamic Free-Tier Limits Configuration:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Modify configs down directly to test trial constraints live:", fontSize = 10.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                val freeScansLimit by viewModel.adminMaxFreeScans.collectAsStateWithLifecycle()
                val freeSymptomsLimit by viewModel.adminMaxFreeSymptoms.collectAsStateWithLifecycle()
                val freeVoiceLimit by viewModel.adminMaxFreeVoiceConvs.collectAsStateWithLifecycle()

                // Limit adjuster Row 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Daily Scans Allowed:", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { viewModel.updateAdminLimits((freeScansLimit - 1).coerceAtLeast(1), freeSymptomsLimit, freeVoiceLimit) },
                            modifier = Modifier.size(36.dp),
                            contentPadding = PaddingValues(0.dp),
                            shape = CircleShape
                        ) {
                            Text("-", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("$freeScansLimit", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = { viewModel.updateAdminLimits(freeScansLimit + 1, freeSymptomsLimit, freeVoiceLimit) },
                            modifier = Modifier.size(36.dp),
                            contentPadding = PaddingValues(0.dp),
                            shape = CircleShape
                        ) {
                            Text("+", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Limit adjuster Row 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Daily Symptoms Allowed:", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { viewModel.updateAdminLimits(freeScansLimit, (freeSymptomsLimit - 1).coerceAtLeast(1), freeVoiceLimit) },
                            modifier = Modifier.size(36.dp),
                            contentPadding = PaddingValues(0.dp),
                            shape = CircleShape
                        ) {
                            Text("-", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("$freeSymptomsLimit", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = { viewModel.updateAdminLimits(freeScansLimit, freeSymptomsLimit + 1, freeVoiceLimit) },
                            modifier = Modifier.size(36.dp),
                            contentPadding = PaddingValues(0.dp),
                            shape = CircleShape
                        ) {
                            Text("+", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Limit adjuster Row 3
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Daily Voice Coach Allowed:", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { viewModel.updateAdminLimits(freeScansLimit, freeSymptomsLimit, (freeVoiceLimit - 1).coerceAtLeast(0)) },
                            modifier = Modifier.size(36.dp),
                            contentPadding = PaddingValues(0.dp),
                            shape = CircleShape
                        ) {
                            Text("-", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("$freeVoiceLimit", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = { viewModel.updateAdminLimits(freeScansLimit, freeSymptomsLimit, freeVoiceLimit + 1) },
                            modifier = Modifier.size(36.dp),
                            contentPadding = PaddingValues(0.dp),
                            shape = CircleShape
                        ) {
                            Text("+", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(14.dp))

                Text("Dispatch Campaigns Notification Tag:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Broadcasting dynamic text reminders to patient mobile notification channels.", fontSize = 11.sp, color = Color.Gray)

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = inputTopic,
                    onValueChange = { inputTopic = it },
                    placeholder = { Text("e.g. Daily meal water optimization alert of high glucose!") },
                    label = { Text("Alert campaign theme text") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (inputTopic.isNotBlank()) {
                            viewModel.triggerSimulatedNotification(inputTopic)
                            inputTopic = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Broadcast Reminder Notification")
                }

                if (activeBanner.isNotEmpty()) {
                    Text(
                        text = "LAST DISPATCHED ALERT: '$activeBanner'",
                        color = Color.Red,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Logout panel
        Button(
            onClick = { viewModel.userLogout() },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("logout_button")
        ) {
            Icon(Icons.Default.Close, "Logout")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Safely Lock Diagnostic Account", color = Color.White)
        }
    }
}
