package com.example.vidhyarthibus

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

// --- WORLD-CLASS STARTUP DESIGN SYSTEM ---
val BrandBlue = Color(0xFF2563EB)
val BrandLightBlue = Color(0xFF60A5FA)
val BrandPurple = Color(0xFF8B5CF6)
val BrandYellow = Color(0xFFF59E0B)
val BrandDark = Color(0xFF0F172A)
val BrandGray = Color(0xFF64748B)
val BrandBg = Color(0xFFF8FAFC)
val GlassWhite = Color(0xD9FFFFFF)
val GlassBorder = Color(0x4DFFFFFF)
val DividerGray = Color(0xFFE2E8F0)
val StatusGreen = Color(0xFF10B981)
val StatusOrange = Color(0xFFF59E0B)
val StatusRed = Color(0xFFEF4444)

val PrimaryGradient = Brush.linearGradient(listOf(BrandBlue, BrandLightBlue))
val AnimatedBgGradient = Brush.verticalGradient(listOf(Color(0xFFEFF6FF), Color(0xFFDBEAFE), Color.White))
val AiGradient = Brush.linearGradient(listOf(BrandPurple, BrandBlue))

data class District(val code: String, val name: String, val centerPoint: GeoPoint, val colleges: List<College>)
data class College(val id: String, val name: String, val routes: List<BusRoute>)
data class BusRoute(val id: String, val name: String, val location: GeoPoint, val contacts: List<String>, val aiInsight: String = "")
data class ChatMessage(val sender: String = "", val text: String = "", val timestamp: Long = 0L, val badge: String = "", val isAi: Boolean = false)

enum class AppState { WELCOME, AUTH, ROLE_SELECTION, MAIN_DASHBOARD, DRIVER_DASHBOARD }
enum class UserRole { WAITING, ON_BUS, DRIVER }

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] != true) {
            Toast.makeText(this, "Location permission required.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // EDGE-TO-EDGE IMMERSIVE UI
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        try { FirebaseDatabase.getInstance().setPersistenceEnabled(true) } catch (e: Exception) {}
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = BrandBlue, background = Color.Transparent)) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) { AppEngine() }
            }
        }
    }
}

fun getTimeAgo(timestamp: Long, reporter: String): String {
    if (timestamp == 0L) return ""
    val diffMillis = System.currentTimeMillis() - timestamp
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
    return when {
        minutes < 1 -> "just now"
        minutes == 1L -> "1m"
        minutes < 60 -> "${minutes}m"
        else -> "${minutes/60}h"
    }
}

fun getTrustBadge(points: Int): String = when {
    points >= 300 -> "🛡️"
    points >= 150 -> "✅"
    points >= 50 -> "🌟"
    else -> ""
}

fun View.performHapticClick() {
    this.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}

@Composable
fun BouncyCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "bounce")

    Box(modifier = modifier.scale(scale).clickable(interactionSource = interactionSource, indication = null, onClick = onClick)) {
        content()
    }
}

@Composable
fun TypewriterText(text: String, modifier: Modifier = Modifier, color: Color, fontSize: androidx.compose.ui.unit.TextUnit, fontWeight: FontWeight) {
    var textToDisplay by remember { mutableStateOf("") }
    LaunchedEffect(text) {
        textToDisplay = ""
        for (i in text.indices) {
            textToDisplay += text[i]
            delay(20)
        }
    }
    Text(textToDisplay, modifier = modifier, color = color, fontSize = fontSize, fontWeight = fontWeight)
}

@Composable
fun ShimmerLoader(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -500f, targetValue = 1500f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = FastOutLinearInEasing), repeatMode = RepeatMode.Restart),
        label = "shimmerAnim"
    )
    val brush = Brush.linearGradient(
        colors = listOf(Color.LightGray.copy(alpha=0.2f), Color.LightGray.copy(alpha=0.6f), Color.LightGray.copy(alpha=0.2f)),
        start = Offset.Zero, end = Offset(translateAnim, translateAnim)
    )
    Box(modifier = modifier.background(brush))
}

@Composable
fun AppEngine() {
    val auth = FirebaseAuth.getInstance()
    var appState by remember { mutableStateOf(if (auth.currentUser == null) AppState.WELCOME else AppState.ROLE_SELECTION) }
    var currentRole by remember { mutableStateOf(UserRole.WAITING) }

    Box(modifier = Modifier.fillMaxSize().background(BrandBg)) {
        Crossfade(
            targetState = appState,
            animationSpec = tween(600, easing = FastOutSlowInEasing),
            label = "App State Transition",
            content = { state ->
                when (state) {
                    AppState.WELCOME -> { WelcomeScreen(onNext = { appState = AppState.AUTH }) }
                    AppState.AUTH -> { AuthScreen(onAuthSuccess = { appState = AppState.ROLE_SELECTION }) }
                    AppState.ROLE_SELECTION -> {
                        RoleSelectionScreen(
                            onSelectRole = { role ->
                                currentRole = role
                                appState = if (role == UserRole.DRIVER) AppState.DRIVER_DASHBOARD else AppState.MAIN_DASHBOARD
                            },
                            onLogout = { auth.signOut(); appState = AppState.WELCOME }
                        )
                    }
                    AppState.MAIN_DASHBOARD -> { MainAppScreen(initialRole = currentRole, onBackToRoles = { appState = AppState.ROLE_SELECTION }, onLogout = { auth.signOut(); appState = AppState.WELCOME }) }
                    AppState.DRIVER_DASHBOARD -> { DriverAppScreen(onBackToRoles = { appState = AppState.ROLE_SELECTION }, onLogout = { auth.signOut(); appState = AppState.WELCOME }) }
                }
            }
        )
    }
}

@Composable
fun WelcomeScreen(onNext: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val view = LocalView.current

    LaunchedEffect(Unit) { delay(100); visible = true }

    Box(modifier = Modifier.fillMaxSize().background(AnimatedBgGradient), contentAlignment = Alignment.BottomCenter) {
        Canvas(modifier = Modifier.fillMaxSize().alpha(0.04f)) {
            val stroke = 4f
            val color = BrandDark
            drawLine(color, Offset(0f, size.height * 0.35f), Offset(size.width * 0.6f, size.height * 0.35f), strokeWidth = stroke)
            drawLine(color, Offset(size.width * 0.6f, size.height * 0.35f), Offset(size.width * 0.8f, size.height * 0.5f), strokeWidth = stroke)
            drawRoundRect(color, topLeft = Offset(size.width * 0.3f, size.height * 0.2f), size = Size(120f, 250f), cornerRadius = CornerRadius(24f), style = Stroke(width = stroke))
            drawCircle(color, radius = 16f, center = Offset(size.width * 0.6f, size.height * 0.35f), style = Stroke(width = stroke * 1.5f))
        }

        Column(modifier = Modifier.fillMaxSize().padding(32.dp).systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            AnimatedVisibility(visible = visible, enter = slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(800)) + fadeIn(tween(800))) {
                Box(modifier = Modifier.size(140.dp).shadow(32.dp, CircleShape, spotColor = BrandBlue).background(PrimaryGradient, CircleShape), contentAlignment = Alignment.Center) {
                    Text("🚌", fontSize = 64.sp)
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
            AnimatedVisibility(visible = visible, enter = slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(800, delayMillis = 200)) + fadeIn(tween(800, delayMillis = 200))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Vidyarthi", fontSize = 48.sp, fontWeight = FontWeight.Black, color = BrandDark, letterSpacing = (-1.5).sp)
                    Text("Next-Gen Campus Transit", fontSize = 16.sp, color = BrandGray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        AnimatedVisibility(visible = visible, enter = slideInVertically(initialOffsetY = { 100 }, animationSpec = tween(800, delayMillis = 400)) + fadeIn(tween(800, delayMillis = 400))) {
            BouncyCard(
                onClick = { view.performHapticClick(); onNext() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp).navigationBarsPadding().height(64.dp).shadow(20.dp, RoundedCornerShape(24.dp), spotColor = BrandBlue)
            ) {
                Box(modifier = Modifier.fillMaxSize().background(BrandDark, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                    Text("Get Started", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val view = LocalView.current

    LaunchedEffect(Unit) { visible = true }

    Column(modifier = Modifier.fillMaxSize().background(Color.White).padding(32.dp).systemBarsPadding().imePadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(500))) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Welcome Back", fontSize = 32.sp, fontWeight = FontWeight.Black, color = BrandDark, letterSpacing = (-1).sp)
                Text("Sign in to continue to Vidyarthi", fontSize = 15.sp, color = BrandGray, modifier = Modifier.padding(top = 8.dp, bottom = 48.dp))
            }
        }

        AnimatedVisibility(visible = visible, enter = slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(600, delayMillis = 100)) + fadeIn(tween(600, delayMillis = 100))) {
            Column {
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    placeholder = { Text("University Email", color = BrandGray) },
                    modifier = Modifier.fillMaxWidth().background(BrandBg, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandBlue, unfocusedBorderColor = Color.Transparent, focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    placeholder = { Text("Password", color = BrandGray) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().background(BrandBg, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandBlue, unfocusedBorderColor = Color.Transparent, focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedVisibility(visible = visible, enter = slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(600, delayMillis = 200)) + fadeIn(tween(600, delayMillis = 200))) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BouncyCard(
                    onClick = {
                        view.performHapticClick()
                        if (email.isNotEmpty() && password.isNotEmpty()) auth.signInWithEmailAndPassword(email, password).addOnSuccessListener { onAuthSuccess() }.addOnFailureListener { Toast.makeText(context, "Login Failed", Toast.LENGTH_SHORT).show() }
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp).shadow(12.dp, RoundedCornerShape(20.dp), spotColor = BrandBlue.copy(alpha=0.5f))
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(PrimaryGradient, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                        Text("Log In", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = {
                    view.performHapticClick()
                    if (email.isNotEmpty() && password.isNotEmpty()) auth.createUserWithEmailAndPassword(email, password).addOnSuccessListener { onAuthSuccess() }
                }) {
                    Text("Don't have an account? Sign up.", fontWeight = FontWeight.Bold, color = BrandBlue)
                }
            }
        }
    }
}

@Composable
fun RoleSelectionScreen(onSelectRole: (UserRole) -> Unit, onLogout: () -> Unit) {
    val view = LocalView.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(modifier = Modifier.fillMaxSize().background(AnimatedBgGradient)) {
        Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 24.dp).zIndex(2f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Select Profile", fontSize = 26.sp, fontWeight = FontWeight.Black, color = BrandDark, letterSpacing = (-0.5).sp)
            IconButton(onClick = { view.performHapticClick(); onLogout() }, modifier = Modifier.background(Color.White, CircleShape).shadow(4.dp, CircleShape)) {
                Icon(Icons.Rounded.ExitToApp, contentDescription = "Logout", tint = StatusRed)
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Spacer(modifier = Modifier.height(80.dp))

            AnimatedVisibility(visible = visible, enter = slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(500)) + fadeIn(tween(500))) {
                BouncyCard(onClick = { view.performHapticClick(); onSelectRole(UserRole.WAITING) }, modifier = Modifier.fillMaxWidth().height(110.dp).shadow(16.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha=0.05f))) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.White, RoundedCornerShape(24.dp))) {
                        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(60.dp).background(BrandBg, CircleShape), contentAlignment = Alignment.Center) { Text("🧍‍♂️", fontSize = 28.sp) }
                            Spacer(modifier = Modifier.width(20.dp))
                            Column { Text("Waiting for a bus", fontWeight = FontWeight.Black, fontSize = 18.sp, color = BrandDark); Text("Track arrivals & capacity", color = BrandGray, fontSize = 14.sp) }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            AnimatedVisibility(visible = visible, enter = slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(500, delayMillis = 100)) + fadeIn(tween(500, delayMillis = 100))) {
                BouncyCard(onClick = { view.performHapticClick(); onSelectRole(UserRole.ON_BUS) }, modifier = Modifier.fillMaxWidth().height(110.dp).shadow(20.dp, RoundedCornerShape(24.dp), spotColor = BrandBlue.copy(alpha=0.3f))) {
                    Box(modifier = Modifier.fillMaxSize().background(PrimaryGradient, RoundedCornerShape(24.dp))) {
                        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(60.dp).background(Color.White.copy(alpha=0.2f), CircleShape), contentAlignment = Alignment.Center) { Text("🚌", fontSize = 28.sp) }
                            Spacer(modifier = Modifier.width(20.dp))
                            Column { Text("Commuting on bus", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.White); Text("Report live crowd status", color = Color.White.copy(alpha=0.8f), fontSize = 14.sp) }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            AnimatedVisibility(visible = visible, enter = slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(500, delayMillis = 200)) + fadeIn(tween(500, delayMillis = 200))) {
                BouncyCard(onClick = { view.performHapticClick(); onSelectRole(UserRole.DRIVER) }, modifier = Modifier.fillMaxWidth().height(110.dp).shadow(16.dp, RoundedCornerShape(24.dp), spotColor = BrandDark.copy(alpha=0.3f))) {
                    Box(modifier = Modifier.fillMaxSize().background(BrandDark, RoundedCornerShape(24.dp))) {
                        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(60.dp).background(Color.White.copy(alpha=0.1f), CircleShape), contentAlignment = Alignment.Center) { Text("👨‍✈️", fontSize = 28.sp) }
                            Spacer(modifier = Modifier.width(20.dp))
                            Column { Text("Fleet Driver", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.White); Text("Broadcast live telemetry", color = Color.White.copy(alpha=0.6f), fontSize = 14.sp) }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverAppScreen(onBackToRoles: () -> Unit, onLogout: () -> Unit) {
    val context = LocalContext.current
    val database = FirebaseDatabase.getInstance("https://vidhyarthibus-default-rtdb.firebaseio.com").reference
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current
    var isBroadcasting by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableFloatStateOf(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 1.08f, animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse), label = "pulseScale")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Driver Console", fontWeight = FontWeight.Black, color = BrandDark, fontSize = 22.sp, letterSpacing = (-0.5).sp) },
                navigationIcon = { IconButton(onClick = { view.performHapticClick(); onBackToRoles() }) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = BrandDark) } },
                actions = { IconButton(onClick = { view.performHapticClick(); onLogout() }) { Icon(Icons.Rounded.ExitToApp, contentDescription = "Logout", tint = StatusRed) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = BrandBg,
        content = { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(32.dp))

                Box(contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(240.dp).scale(if (isBroadcasting) pulseScale else 1f).shadow(if(isBroadcasting) 48.dp else 12.dp, CircleShape, spotColor = if(isBroadcasting) StatusGreen else Color.Black).background(if (isBroadcasting) StatusGreen else BrandDark, CircleShape).clickable {
                        view.performHapticClick()
                        isBroadcasting = !isBroadcasting
                        if (isBroadcasting) {
                            Toast.makeText(context, "Telemetry Active", Toast.LENGTH_SHORT).show()
                            coroutineScope.launch(Dispatchers.IO) {
                                while (isBroadcasting) {
                                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc ->
                                        if (loc != null) {
                                            currentSpeed = (loc.speed * 3.6f)
                                            database.child("bus_live").setValue(mapOf("lat" to loc.latitude, "lng" to loc.longitude, "speed" to currentSpeed, "timestamp" to System.currentTimeMillis()))
                                        }
                                    }
                                    delay(5000)
                                }
                            }
                        } else { Toast.makeText(context, "Telemetry Offline", Toast.LENGTH_SHORT).show() }
                    }, contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(if (isBroadcasting) Icons.Rounded.LocationOn else Icons.Rounded.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(72.dp))
                            Text(if (isBroadcasting) "LIVE" else "GO ONLINE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(60.dp))

                Box(modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(24.dp)).shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha=0.05f)).animateContentSize()) {
                    Column(modifier = Modifier.padding(32.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Current Speed", color = BrandGray, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp)
                        Text("${currentSpeed.toInt()} km/h", fontSize = 56.sp, fontWeight = FontWeight.Black, color = BrandDark)
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(initialRole: UserRole, onBackToRoles: () -> Unit, onLogout: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    var userReputation by remember { mutableIntStateOf(150) }
    var isWaitingMode by remember { mutableStateOf(initialRole == UserRole.WAITING) }
    val view = LocalView.current

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0,0,0,0),
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding().shadow(32.dp, RoundedCornerShape(32.dp), spotColor = BrandBlue.copy(alpha = 0.3f))) {
                NavigationBar(containerColor = GlassWhite, contentColor = BrandDark, tonalElevation = 0.dp, modifier = Modifier.height(76.dp).clip(RoundedCornerShape(32.dp)).border(1.dp, GlassBorder, RoundedCornerShape(32.dp))) {
                    NavigationBarItem(selected = selectedTab == 0, onClick = { view.performHapticClick(); selectedTab = 0 }, icon = { Icon(Icons.Rounded.LocationOn, contentDescription = "Transit") }, label = { Text("Transit", fontWeight = FontWeight.Bold, fontSize = 11.sp) }, colors = NavigationBarItemDefaults.colors(indicatorColor = BrandBlue.copy(alpha=0.15f), selectedIconColor = BrandBlue, unselectedIconColor = BrandGray, selectedTextColor = BrandBlue, unselectedTextColor = BrandGray))
                    NavigationBarItem(selected = selectedTab == 1, onClick = { view.performHapticClick(); selectedTab = 1 }, icon = { Icon(Icons.Rounded.Star, contentDescription = "Rewards") }, label = { Text("Rewards", fontWeight = FontWeight.Bold, fontSize = 11.sp) }, colors = NavigationBarItemDefaults.colors(indicatorColor = BrandBlue.copy(alpha=0.15f), selectedIconColor = BrandBlue, unselectedIconColor = BrandGray, selectedTextColor = BrandBlue, unselectedTextColor = BrandGray))
                    NavigationBarItem(selected = selectedTab == 2, onClick = { view.performHapticClick(); selectedTab = 2 }, icon = { Icon(Icons.Rounded.AccountCircle, contentDescription = "Profile") }, label = { Text("Profile", fontWeight = FontWeight.Bold, fontSize = 11.sp) }, colors = NavigationBarItemDefaults.colors(indicatorColor = BrandBlue.copy(alpha=0.15f), selectedIconColor = BrandBlue, unselectedIconColor = BrandGray, selectedTextColor = BrandBlue, unselectedTextColor = BrandGray))
                }
            }
        },
        content = { paddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.padding(paddingValues))
                Crossfade(targetState = selectedTab, label = "Tab Fade", animationSpec = tween(400), content = { tab ->
                    when (tab) {
                        0 -> BusDashboard(isWaitingMode = isWaitingMode, onSwitchMode = { isWaitingMode = it }, onBackToRoles = onBackToRoles, userReputation = userReputation, onReputationGain = { userReputation += it })
                        1 -> RewardsScreen(userReputation)
                        2 -> ProfileScreen(onLogout, userReputation)
                    }
                })
            }
        }
    )
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusDashboard(isWaitingMode: Boolean, onSwitchMode: (Boolean) -> Unit, onBackToRoles: () -> Unit, userReputation: Int, onReputationGain: (Int) -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val database = FirebaseDatabase.getInstance("https://vidhyarthibus-default-rtdb.firebaseio.com").reference
    val auth = FirebaseAuth.getInstance()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val rawEmail = auth.currentUser?.email ?: ""
    val currentUsername = if (rawEmail.contains("@")) rawEmail.substringBefore("@").replaceFirstChar { it.uppercase() } else "Akash"
    val currentUserBadge = getTrustBadge(userReputation)

    val districtList = getStatewideSubmissionData()
    var districtMenuExpanded by remember { mutableStateOf(false) }
    var collegeMenuExpanded by remember { mutableStateOf(false) }
    var routeMenuExpanded by remember { mutableStateOf(false) }

    var selectedDistrict by remember { mutableStateOf<District?>(null) }
    var selectedCollege by remember { mutableStateOf<College?>(null) }
    var selectedRoute by remember { mutableStateOf<BusRoute?>(null) }

    var crowdLevel by remember { mutableFloatStateOf(0.0f) }
    var lastUpdatedTime by remember { mutableLongStateOf(0L) }
    var occupiedSeats by remember { mutableIntStateOf(0) }
    val totalSeats = 50
    var usersOnBusCount by remember { mutableLongStateOf(0L) }

    val animatedCrowdProgress by animateFloatAsState(targetValue = crowdLevel, animationSpec = tween(1000, easing = FastOutSlowInEasing), label = "crowd")
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    var newChatMessage by remember { mutableStateOf("") }
    var showAutoDialog by remember { mutableStateOf(false) }
    var pendingCrowdUpdate by remember { mutableStateOf<Int?>(null) }

    var liveBusLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var myLiveLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var myLiveSpeed by remember { mutableFloatStateOf(0f) }
    var busSpeed by remember { mutableFloatStateOf(0f) }

    var userLocationInput by remember { mutableStateOf("") }
    var isLocatingUser by remember { mutableStateOf(false) }
    var hudAlertMessage by remember { mutableStateOf("") }

    val quickReplies = listOf("🚦 Traffic", "🏃‍♂️ Fast", "⏳ Delayed", "🟢 Clear", "🚌 Full")

    LaunchedEffect(Unit) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    myLiveLocation = GeoPoint(loc.latitude, loc.longitude)
                    myLiveSpeed = loc.speed * 3.6f
                }
            }
        } catch (e: Exception) {}

        database.child("bus_live").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = snapshot.child("lat").value?.toString()?.toDoubleOrNull()
                val lng = snapshot.child("lng").value?.toString()?.toDoubleOrNull()
                busSpeed = snapshot.child("speed").value?.toString()?.toFloatOrNull() ?: 0f
                if (lat != null && lng != null) liveBusLocation = GeoPoint(lat, lng)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    DisposableEffect(selectedRoute) {
        chatMessages.clear()
        hudAlertMessage = ""
        if (selectedRoute == null) return@DisposableEffect onDispose { }

        val routeRef = database.child("routes_live").child(selectedRoute!!.id)
        val statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                occupiedSeats = snapshot.child("occupied_seats").value?.toString()?.toIntOrNull() ?: 0
                val time = snapshot.child("timestamp").value?.toString()?.toLongOrNull() ?: 0L
                crowdLevel = (occupiedSeats.toFloat() / totalSeats).coerceIn(0f, 1f)
                if (isWaitingMode && crowdLevel >= 0.9f) hudAlertMessage = "⚠️ Alert: Fleet is almost at max capacity!"
                if (System.currentTimeMillis() - time > 900000) { crowdLevel = 0.0f; lastUpdatedTime = 0L } else { lastUpdatedTime = time }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        routeRef.addValueEventListener(statusListener)

        val usersRef = database.child("bus_users").child(selectedRoute!!.id)
        val userListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { usersOnBusCount = snapshot.childrenCount }
            override fun onCancelled(error: DatabaseError) {}
        }
        usersRef.addValueEventListener(userListener)

        val chatRef = database.child("routes_chat").child(selectedRoute!!.id)
        val chatListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                chatMessages.clear()
                for (child in snapshot.children) {
                    val msg = child.getValue(ChatMessage::class.java)
                    if (msg != null) chatMessages.add(msg)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        chatRef.limitToLast(20).addValueEventListener(chatListener)

        onDispose {
            routeRef.removeEventListener(statusListener)
            usersRef.removeEventListener(userListener)
            chatRef.removeEventListener(chatListener)
        }
    }

    fun geocodeUserLocation() {
        if (userLocationInput.isBlank()) return
        isLocatingUser = true
        keyboardController?.hide()
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val query = if (userLocationInput.contains("Karnataka", true)) userLocationInput else "$userLocationInput, Karnataka, India"
                val addresses = geocoder.getFromLocationName(query, 1)
                withContext(Dispatchers.Main) {
                    isLocatingUser = false
                    if (!addresses.isNullOrEmpty()) {
                        myLiveLocation = GeoPoint(addresses[0].latitude, addresses[0].longitude)
                    } else {
                        myLiveLocation = GeoPoint(13.0305, 77.5649)
                        userLocationInput = "$userLocationInput (Simulated)"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLocatingUser = false
                    myLiveLocation = GeoPoint(13.0305, 77.5649)
                    userLocationInput = "$userLocationInput (Simulated)"
                }
            }
        }
    }

    fun fetchHardwareGPS() {
        view.performHapticClick()
        isLocatingUser = true
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc ->
                isLocatingUser = false
                if (loc != null) {
                    myLiveLocation = GeoPoint(loc.latitude, loc.longitude)
                    myLiveSpeed = loc.speed * 3.6f
                    userLocationInput = "Live GPS Locked"
                }
            }
        } catch (e: Exception) { isLocatingUser = false }
    }

    fun updateCrowd(seats: Int) {
        val currentRoute = selectedRoute ?: return
        if (myLiveLocation != null && liveBusLocation != null) {
            val results = FloatArray(1)
            Location.distanceBetween(myLiveLocation!!.latitude, myLiveLocation!!.longitude, liveBusLocation!!.latitude, liveBusLocation!!.longitude, results)
            if (results[0] > 1000f) {
                Toast.makeText(context, "Anti-Fraud: Must be near bus to report.", Toast.LENGTH_LONG).show()
                return
            }
        }
        onReputationGain(15)
        database.child("routes_live").child(currentRoute.id).updateChildren(mapOf("occupied_seats" to seats, "total_seats" to totalSeats, "timestamp" to System.currentTimeMillis(), "reportedBy" to currentUsername))
        Toast.makeText(context, "Broadcasted! Earned 15 Pts.", Toast.LENGTH_SHORT).show()
    }

    fun sendChatMessage() {
        val currentRoute = selectedRoute ?: return
        if (newChatMessage.isBlank()) return
        database.child("routes_chat").child(currentRoute.id).push().setValue(ChatMessage(sender = currentUsername, text = newChatMessage.trim(), timestamp = System.currentTimeMillis(), badge = currentUserBadge, isAi = false))
        newChatMessage = ""
    }

    if (pendingCrowdUpdate != null) {
        val statusText = when(pendingCrowdUpdate) {
            (totalSeats * 0.2).toInt() -> "Empty"
            (totalSeats * 0.6).toInt() -> "Moderate"
            else -> "Full"
        }
        AlertDialog(
            onDismissRequest = { pendingCrowdUpdate = null },
            title = { Text("Confirm Broadcast", fontWeight = FontWeight.Bold) },
            text = { Text("Set network status to '$statusText'?", fontSize = 15.sp) },
            confirmButton = { Button(onClick = { view.performHapticClick(); updateCrowd(pendingCrowdUpdate!!); pendingCrowdUpdate = null }, colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)) { Text("Broadcast", color = Color.White, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { view.performHapticClick(); pendingCrowdUpdate = null }) { Text("Cancel", color = BrandGray) } },
            shape = RoundedCornerShape(16.dp), containerColor = Color.White
        )
    }

    if (showAutoDialog && selectedRoute != null) {
        AlertDialog(
            onDismissRequest = { showAutoDialog = false },
            title = { Text("Partnered Transit", fontWeight = FontWeight.Black) },
            text = {
                Column {
                    Text("Verified local drivers:", color = BrandGray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    if (selectedRoute?.contacts.isNullOrEmpty()) {
                        Text("No direct contacts found.", fontWeight = FontWeight.Bold, color = StatusRed)
                    } else {
                        selectedRoute?.contacts?.forEach { contact ->
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(BrandBg, RoundedCornerShape(12.dp)).padding(16.dp)) {
                                Text(contact, fontWeight = FontWeight.Bold, color = BrandDark)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().border(2.dp, StatusGreen, RoundedCornerShape(12.dp)).background(StatusGreen.copy(alpha=0.1f), RoundedCornerShape(12.dp))) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Estimated Fare:", color = StatusGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("₹45.00", color = BrandDark, fontWeight = FontWeight.Black, fontSize = 24.sp)
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { view.performHapticClick(); showAutoDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = BrandDark)) { Text("Close", color = Color.White) } },
            dismissButton = {
                OutlinedButton(onClick = {
                    view.performHapticClick()
                    val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode("auto rickshaw stand near ${selectedRoute!!.location.latitude}, ${selectedRoute!!.location.longitude}")}")
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }) } catch (e: Exception) { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                }) { Text("Search Maps", color = BrandDark, fontWeight = FontWeight.Bold) }
            }, containerColor = Color.White, shape = RoundedCornerShape(24.dp)
        )
    }

    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded, skipHiddenState = true))

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = if (selectedRoute != null) 360.dp else 0.dp,
        sheetContainerColor = GlassWhite,
        sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        sheetDragHandle = { BottomSheetDefaults.DragHandle() },
        content = { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {

                OpenMapScreen(
                    modifier = Modifier.fillMaxSize(),
                    selectedRoute = selectedRoute,
                    liveBusLoc = liveBusLocation,
                    myLoc = myLiveLocation,
                    crowdLvl = crowdLevel,
                    busSpeed = busSpeed,
                    onRecenter = {
                        if (liveBusLocation != null || selectedRoute != null) {
                            view.performHapticClick()
                        }
                    }
                )

                Box(modifier = Modifier.fillMaxWidth().height(160.dp).background(Brush.verticalGradient(colors = listOf(Color.White.copy(alpha=0.95f), Color.White.copy(alpha=0.6f), Color.Transparent))).zIndex(1.5f))

                Column(modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 16.dp, start = 24.dp).zIndex(5f)) {
                    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    val greeting = when (hour) { in 5..11 -> "Good Morning"; in 12..16 -> "Good Afternoon"; in 17..20 -> "Good Evening"; else -> "Hello" }
                    Text("$greeting, $currentUsername 👋", fontSize = 24.sp, fontWeight = FontWeight.Black, color = BrandDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (selectedRoute != null) "Tracking ${selectedRoute!!.name}" else "Find your campus route", fontSize = 14.sp, color = BrandGray, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top=4.dp))
                }

                Box(modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 16.dp, end = 24.dp).zIndex(4f)) {
                    IconButton(onClick = {
                        view.performHapticClick()
                        val intent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:112") }
                        try { context.startActivity(intent) } catch (e: Exception) {}
                    }, modifier = Modifier.size(48.dp).shadow(12.dp, CircleShape, spotColor=StatusRed).background(Color.White, CircleShape).border(2.dp, StatusRed, CircleShape)) {
                        Text("SOS", color = StatusRed, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    }
                }

                Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().zIndex(2f).statusBarsPadding().padding(top = if(hudAlertMessage.isNotEmpty()) 80.dp else 80.dp, start = 20.dp, end = 20.dp).animateContentSize()) {

                    if (selectedRoute == null) {
                        ExposedDropdownMenuBox(expanded = districtMenuExpanded, onExpandedChange = { districtMenuExpanded = !districtMenuExpanded }, content = {
                            OutlinedTextField(
                                value = selectedDistrict?.let { "${it.code} - ${it.name}" } ?: "Select Operational Zone...", onValueChange = {}, readOnly = true,
                                leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = BrandBlue) },
                                modifier = Modifier.menuAnchor().fillMaxWidth().height(64.dp).shadow(16.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha=0.1f)),
                                shape = RoundedCornerShape(20.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = BrandBlue, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White),
                                textStyle = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            )
                            ExposedDropdownMenu(expanded = districtMenuExpanded, onDismissRequest = { districtMenuExpanded = false }, modifier = Modifier.background(Color.White)) {
                                districtList.forEach { district -> DropdownMenuItem(text = { Text("${district.code} - ${district.name}", fontWeight = FontWeight.Bold) }, onClick = { view.performHapticClick(); selectedDistrict = district; selectedCollege = null; selectedRoute = null; districtMenuExpanded = false }) }
                            }
                        })

                        if (selectedDistrict != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            ExposedDropdownMenuBox(expanded = collegeMenuExpanded, onExpandedChange = { collegeMenuExpanded = !collegeMenuExpanded }, content = {
                                OutlinedTextField(
                                    value = selectedCollege?.name ?: "Select Institution...", onValueChange = {}, readOnly = true,
                                    leadingIcon = { Icon(Icons.Rounded.Home, contentDescription = null, tint = BrandBlue) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth().height(64.dp).shadow(16.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha=0.1f)),
                                    shape = RoundedCornerShape(20.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = BrandBlue, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                )
                                ExposedDropdownMenu(expanded = collegeMenuExpanded, onDismissRequest = { collegeMenuExpanded = false }, modifier = Modifier.background(Color.White)) {
                                    selectedDistrict!!.colleges.forEach { college -> DropdownMenuItem(text = { Text(college.name, fontWeight = FontWeight.Bold) }, onClick = { view.performHapticClick(); selectedCollege = college; selectedRoute = null; collegeMenuExpanded = false }) }
                                }
                            })
                        }

                        if (selectedCollege != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            ExposedDropdownMenuBox(expanded = routeMenuExpanded, onExpandedChange = { routeMenuExpanded = !routeMenuExpanded }, content = {
                                OutlinedTextField(
                                    value = selectedRoute?.name ?: "Select Target Route...", onValueChange = {}, readOnly = true,
                                    leadingIcon = { Icon(Icons.Rounded.List, contentDescription = null, tint = BrandBlue) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth().height(64.dp).shadow(16.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha=0.1f)),
                                    shape = RoundedCornerShape(20.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = BrandBlue, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                )
                                ExposedDropdownMenu(expanded = routeMenuExpanded, onDismissRequest = { routeMenuExpanded = false }, modifier = Modifier.background(Color.White)) {
                                    selectedCollege!!.routes.forEach { route -> DropdownMenuItem(text = { Text(route.name, fontWeight = FontWeight.Bold) }, onClick = { view.performHapticClick(); selectedRoute = route; routeMenuExpanded = false }) }
                                }
                            })
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { view.performHapticClick(); onBackToRoles() }, modifier = Modifier.background(Color.White, CircleShape).border(1.dp, DividerGray, CircleShape).size(44.dp).shadow(4.dp, CircleShape)) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = BrandDark)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.background(Color.White.copy(alpha=0.9f), RoundedCornerShape(12.dp)).border(1.dp, GlassBorder, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text("🟢 LIVE TRACKING", fontSize = 10.sp, fontWeight = FontWeight.Black, color = StatusGreen)
                                }
                                Box(modifier = Modifier.background(Color.White.copy(alpha=0.9f), RoundedCornerShape(12.dp)).border(1.dp, GlassBorder, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text("👥 ${usersOnBusCount + 3} WAITING", fontSize = 10.sp, fontWeight = FontWeight.Black, color = BrandBlue)
                                }
                            }
                        }

                        if (isWaitingMode) {
                            OutlinedTextField(
                                value = userLocationInput, onValueChange = { userLocationInput = it },
                                placeholder = { Text("Search location...", color = BrandGray, fontSize = 15.sp) },
                                leadingIcon = { IconButton(onClick = { fetchHardwareGPS() }) { Icon(Icons.Rounded.LocationOn, contentDescription = "GPS", tint = BrandBlue) } },
                                trailingIcon = {
                                    IconButton(onClick = { view.performHapticClick(); geocodeUserLocation() }) { if (isLocatingUser) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = BrandBlue, strokeWidth = 2.dp) else Icon(Icons.Rounded.Search, tint = BrandDark, contentDescription = null) }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { geocodeUserLocation() }),
                                modifier = Modifier.fillMaxWidth().height(64.dp).shadow(16.dp, RoundedCornerShape(20.dp), spotColor=Color.Black.copy(alpha=0.1f)),
                                shape = RoundedCornerShape(20.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = BrandBlue, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
                            )
                        }
                    }
                }
            }
        },
        sheetContent = {
            if (selectedRoute != null) {
                // Ensure padding allows clear scrolling above the floating nav bar
                Column(modifier = Modifier.padding(horizontal = 24.dp).fillMaxHeight(0.85f).padding(bottom = 120.dp).verticalScroll(rememberScrollState()).animateContentSize()) {

                    if (isWaitingMode) {
                        BouncyCard(onClick = {}, modifier = Modifier.fillMaxWidth().shadow(16.dp, RoundedCornerShape(24.dp), spotColor = BrandPurple.copy(alpha=0.4f))) {
                            Box(modifier = Modifier.fillMaxWidth().background(AiGradient, RoundedCornerShape(24.dp)).border(1.dp, GlassBorder, RoundedCornerShape(24.dp))) {
                                Column(modifier = Modifier.padding(24.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Star, contentDescription = null, tint = BrandYellow, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("AI Transit Intelligence", fontWeight = FontWeight.Black, color = Color.White, fontSize = 15.sp)
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    TypewriterText(
                                        text = if (crowdLevel > 0.8f) "Heavy congestion predicted based on historical data. Consider alternative routes or partnered autos." else "Route clear. Optimal time to commute.",
                                        color = Color.White.copy(alpha=0.9f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        var distanceMeters = 0.0
                        var etaMins = 0
                        var travelStatus = "Awaiting Telemetry"

                        val targetDistLoc = liveBusLocation ?: selectedRoute?.location
                        if (myLiveLocation != null && targetDistLoc != null) {
                            val results = FloatArray(1)
                            Location.distanceBetween(myLiveLocation!!.latitude, myLiveLocation!!.longitude, targetDistLoc.latitude, targetDistLoc.longitude, results)
                            distanceMeters = results[0].toDouble()
                            etaMins = (distanceMeters / 8.33 / 60).toInt()
                            if (liveBusLocation != null) {
                                if (distanceMeters < 5000 && etaMins > 20) { etaMins += 10 }
                                travelStatus = when {
                                    distanceMeters < 500 -> "Arriving Now"
                                    distanceMeters < 2000 -> "Approaching (~${etaMins}m)"
                                    else -> "On the way (~${etaMins}m)"
                                }
                            } else {
                                travelStatus = "Bus Offline (~${etaMins}m)"
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha=0.05f)), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(24.dp)) {
                            Row(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Fleet Status", fontSize = 13.sp, color = BrandGray, fontWeight = FontWeight.Bold)
                                    Text(travelStatus, fontSize = 20.sp, fontWeight = FontWeight.Black, color = if(liveBusLocation != null && distanceMeters in 1.0..500.0) StatusGreen else BrandDark)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Distance", fontSize = 13.sp, color = BrandGray, fontWeight = FontWeight.Bold)
                                    val distStr = if (distanceMeters > 1000) String.format("%.1f km", distanceMeters/1000) else String.format("%.0f m", distanceMeters)
                                    Text(if (distanceMeters == 0.0) "Calc..." else distStr, fontSize = 24.sp, fontWeight = FontWeight.Black, color = BrandBlue)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Text("PREDICTED CROWD TRENDS", fontSize = 12.sp, fontWeight = FontWeight.Black, color = BrandGray, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            val currentCalendar = java.util.Calendar.getInstance()
                            val hourFormat = java.text.SimpleDateFormat("h a", Locale.getDefault())

                            val timeSlots = (0..3).map { offset ->
                                val cal = currentCalendar.clone() as java.util.Calendar
                                cal.add(java.util.Calendar.HOUR_OF_DAY, offset)
                                val timeString = hourFormat.format(cal.time)
                                val hour24 = cal.get(java.util.Calendar.HOUR_OF_DAY)

                                val crowdColor = if (hour24 in 8..10 || hour24 in 17..19) StatusRed else if (hour24 == 7 || hour24 == 11 || hour24 == 16 || hour24 == 20) StatusOrange else StatusGreen
                                Pair(timeString, crowdColor)
                            }

                            timeSlots.forEachIndexed { index, slot ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(modifier = Modifier.size(16.dp).shadow(4.dp, CircleShape, spotColor=slot.second).background(slot.second, CircleShape))
                                    Text(slot.first, fontSize = 12.sp, color = BrandGray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top=8.dp))
                                }
                                if (index < timeSlots.size - 1) {
                                    Box(modifier = Modifier.height(3.dp).weight(1f).background(BrandBg).align(Alignment.CenterVertically).offset(y = -12.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))

                        if (crowdLevel >= 0.7f) {
                            BouncyCard(onClick = { view.performHapticClick(); showAutoDialog = true }, modifier = Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(24.dp), spotColor = StatusOrange.copy(alpha=0.3f))) {
                                Box(modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(24.dp))) {
                                    Column(modifier = Modifier.padding(24.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.Warning, contentDescription = null, tint = StatusOrange, modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Bus Heavily Crowded", fontWeight = FontWeight.Black, color = BrandDark, fontSize = 18.sp)
                                        }
                                        Text("Skip the wait. Book a ride directly from your location.", fontSize = 14.sp, color = BrandGray, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                                        Box(modifier = Modifier.fillMaxWidth().height(56.dp).background(BrandDark, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                                            Text("Find Partnered Autos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        }
                                    }
                                }
                            }
                        } else {
                            Text("Capacity: ${totalSeats - occupiedSeats} seats available", fontWeight = FontWeight.Bold, color = StatusGreen, modifier = Modifier.align(Alignment.CenterHorizontally), fontSize = 16.sp)
                        }

                    } else {
                        Text("SEAT TRACKING", fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp, color = BrandGray, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(modifier = Modifier.height(16.dp))

                        val barColor = if (crowdLevel < 0.3f) StatusGreen else if (crowdLevel < 0.7f) StatusOrange else StatusRed
                        LinearProgressIndicator(progress = animatedCrowdProgress, modifier = Modifier.fillMaxWidth().height(20.dp).clip(CircleShape), color = barColor, trackColor = BrandBg)

                        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Occupied: $occupiedSeats", fontWeight = FontWeight.Bold, color = BrandGray, fontSize = 14.sp)
                            Text("Available: ${totalSeats - occupiedSeats}", fontWeight = FontWeight.Bold, color = BrandGray, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                        Text("Update Status:", fontWeight = FontWeight.Black, fontSize = 18.sp, color = BrandDark)
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BouncyCard(onClick = { view.performHapticClick(); pendingCrowdUpdate = (totalSeats * 0.2).toInt() }, modifier = Modifier.weight(1f).height(60.dp).shadow(8.dp, RoundedCornerShape(16.dp), spotColor = StatusGreen)) { Box(modifier=Modifier.fillMaxSize().background(StatusGreen, RoundedCornerShape(16.dp)), contentAlignment=Alignment.Center) { Text("Empty", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.White) } }
                            BouncyCard(onClick = { view.performHapticClick(); pendingCrowdUpdate = (totalSeats * 0.6).toInt() }, modifier = Modifier.weight(1f).height(60.dp).shadow(8.dp, RoundedCornerShape(16.dp), spotColor = StatusOrange)) { Box(modifier=Modifier.fillMaxSize().background(StatusOrange, RoundedCornerShape(16.dp)), contentAlignment=Alignment.Center) { Text("Moderate", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.White) } }
                            BouncyCard(onClick = { view.performHapticClick(); pendingCrowdUpdate = (totalSeats * 1.0).toInt() }, modifier = Modifier.weight(1f).height(60.dp).shadow(8.dp, RoundedCornerShape(16.dp), spotColor = StatusRed)) { Box(modifier=Modifier.fillMaxSize().background(StatusRed, RoundedCornerShape(16.dp)), contentAlignment=Alignment.Center) { Text("Full", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.White) } }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 32.dp), color = DividerGray, thickness = 1.dp)

                    Text("COMMUNITY CHAT", fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp, color = BrandGray)

                    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp).animateContentSize()) {
                        if (chatMessages.isEmpty()) {
                            Text("No messages yet. Be the first!", color = BrandGray, fontSize = 15.sp, modifier = Modifier.padding(vertical = 8.dp))
                        } else {
                            chatMessages.forEach { msg ->
                                val isMe = msg.sender == currentUsername

                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = if(isMe) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
                                    if (!isMe) {
                                        Box(modifier = Modifier.size(40.dp).shadow(4.dp, CircleShape).background(if (msg.isAi) BrandPurple else Color.White, CircleShape), contentAlignment = Alignment.Center) {
                                            Icon(if (msg.isAi) Icons.Rounded.Star else Icons.Rounded.Person, contentDescription = null, tint = if (msg.isAi) Color.White else BrandGray, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }
                                    Column(horizontalAlignment = if(isMe) Alignment.End else Alignment.Start, modifier = Modifier.weight(1f, fill = false)) {
                                        if (!isMe) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                                                Text(if (msg.isAi) "System" else msg.sender, fontWeight = FontWeight.Black, fontSize = 14.sp, color = BrandDark)
                                                if (msg.badge.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(msg.badge, fontSize = 11.sp, color = BrandGray, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        val bubbleModifier = if (msg.isAi) Modifier.background(AiGradient, RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)) else if (isMe) Modifier.background(PrimaryGradient, RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)) else Modifier.background(Color.White, RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)).border(1.dp, DividerGray, RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp))
                                        val textColor = if (isMe || msg.isAi) Color.White else BrandDark

                                        Box(modifier = bubbleModifier.padding(horizontal = 20.dp, vertical = 14.dp).shadow(if(isMe||msg.isAi) 8.dp else 2.dp, RoundedCornerShape(20.dp), spotColor = if(isMe||msg.isAi) BrandBlue else Color.Transparent)) {
                                            if(msg.isAi){
                                                TypewriterText(text = msg.text, color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                            } else {
                                                Text(msg.text, fontSize = 16.sp, color = textColor, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                        Text(getTimeAgo(msg.timestamp, ""), fontSize = 12.sp, color = BrandGray, modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(quickReplies) { reply ->
                            ElevatedAssistChip(
                                onClick = { view.performHapticClick(); newChatMessage = reply },
                                label = { Text(reply, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BrandDark) },
                                shape = RoundedCornerShape(20.dp),
                                colors = AssistChipDefaults.elevatedAssistChipColors(containerColor = Color.White)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newChatMessage, onValueChange = { newChatMessage = it },
                            placeholder = { Text("Add a comment...", fontSize = 15.sp, color = BrandGray) },
                            modifier = Modifier.weight(1f).height(60.dp), shape = RoundedCornerShape(28.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandBlue, unfocusedBorderColor = Color.Transparent, focusedContainerColor = BrandBg, unfocusedContainerColor = BrandBg)
                        )
                        Spacer(modifier = Modifier.width(12.dp))

                        IconButton(onClick = {
                            view.performHapticClick()
                            val currentRouteSnap = selectedRoute
                            if (currentRouteSnap != null) {
                                val locationContext = if (userLocationInput.isNotEmpty()) "User at $userLocationInput. " else ""
                                val aiMessageText = when {
                                    lastUpdatedTime == 0L -> "✨ AI Insight: Awaiting fresh telemetry."
                                    crowdLevel < 0.4f -> "✨ AI Insight: ${locationContext}Safe to wait! Plenty of seats."
                                    crowdLevel < 0.8f -> "✨ AI Insight: ${locationContext}Nearing capacity."
                                    else -> "✨ AI Insight: ${locationContext}Fleet FULL. Seek alternatives."
                                }
                                database.child("routes_chat").child(currentRouteSnap.id).push().setValue(ChatMessage(sender = "AI Agent", text = aiMessageText, timestamp = System.currentTimeMillis(), badge = "🤖 System", isAi = true))
                            }
                        }, modifier = Modifier.background(BrandBg, CircleShape).size(60.dp)) { Icon(Icons.Rounded.Star, contentDescription = "Ask AI", tint = BrandPurple, modifier = Modifier.size(24.dp)) }

                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = {
                            view.performHapticClick()
                            val currentRoute = selectedRoute ?: return@IconButton
                            if (newChatMessage.isBlank()) return@IconButton
                            database.child("routes_chat").child(currentRoute.id).push().setValue(ChatMessage(sender = currentUsername, text = newChatMessage.trim(), timestamp = System.currentTimeMillis(), badge = currentUserBadge, isAi = false))
                            newChatMessage = ""
                        }, modifier = Modifier.background(PrimaryGradient, CircleShape).size(60.dp).shadow(8.dp, CircleShape, spotColor=BrandBlue)) { Icon(Icons.Rounded.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(24.dp)) }
                    }
                }
            } else { Box(modifier = Modifier.height(1.dp)) }
        }
    )
}

@Composable
fun OpenMapScreen(modifier: Modifier = Modifier, selectedRoute: BusRoute?, liveBusLoc: GeoPoint?, myLoc: GeoPoint?, crowdLvl: Float, busSpeed: Float, onRecenter: () -> Unit) {
    val context = LocalContext.current
    Configuration.getInstance().userAgentValue = context.packageName

    val mapView = remember { MapView(context).apply { setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true); minZoomLevel = 4.0 } }

    var lastRenderedBusLat by remember { mutableDoubleStateOf(0.0) }
    var lastRenderedBusLng by remember { mutableDoubleStateOf(0.0) }
    var lastRenderedMyLat by remember { mutableDoubleStateOf(0.0) }
    var lastRenderedMyLng by remember { mutableDoubleStateOf(0.0) }

    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val pulseRadius by infiniteTransition.animateFloat(initialValue = 100f, targetValue = 600f, animationSpec = infiniteRepeatable(animation = tween(2000), repeatMode = RepeatMode.Restart), label = "radRadius")
    val pulseAlpha by infiniteTransition.animateFloat(initialValue = 0.4f, targetValue = 0f, animationSpec = infiniteRepeatable(animation = tween(2000)), label = "radAlpha")

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
            update = { map ->
                val targetLoc = liveBusLoc ?: selectedRoute?.location
                var needsUpdate = false

                if (targetLoc != null) {
                    val deltaLat = Math.abs(targetLoc.latitude - lastRenderedBusLat)
                    val deltaLng = Math.abs(targetLoc.longitude - lastRenderedBusLng)
                    if (deltaLat >= 0.0001 || deltaLng >= 0.0001) needsUpdate = true
                }
                if (myLoc != null) {
                    val deltaMyLat = Math.abs(myLoc.latitude - lastRenderedMyLat)
                    val deltaMyLng = Math.abs(myLoc.longitude - lastRenderedMyLng)
                    if (deltaMyLat >= 0.0001 || deltaMyLng >= 0.0001) needsUpdate = true
                }

                if (!needsUpdate) {
                    map.overlays.removeAll { it is Polygon && it.title == "pulse" }
                    if (targetLoc != null) {
                        val pulseColor = android.graphics.Color.argb((pulseAlpha * 255).toInt(), 37, 99, 235)
                        val pulse = Polygon().apply { points = Polygon.pointsAsCircle(targetLoc, pulseRadius.toDouble()); fillColor = pulseColor; strokeColor = android.graphics.Color.TRANSPARENT; title = "pulse" }
                        map.overlays.add(pulse)
                    }
                    map.invalidate()
                    return@AndroidView
                }

                if (targetLoc != null) { lastRenderedBusLat = targetLoc.latitude; lastRenderedBusLng = targetLoc.longitude }
                if (myLoc != null) { lastRenderedMyLat = myLoc.latitude; lastRenderedMyLng = myLoc.longitude }

                map.overlays.clear()

                myLoc?.let {
                    val meMarker = Marker(map).apply { position = it; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); title = "🧍 You"; icon = ContextCompat.getDrawable(context, android.R.drawable.presence_online) }
                    map.overlays.add(meMarker)
                }

                if (myLoc != null && targetLoc != null) {
                    val heatColor = when {
                        crowdLvl < 0.4f -> android.graphics.Color.parseColor("#10B981")
                        crowdLvl < 0.8f -> android.graphics.Color.parseColor("#F59E0B")
                        else -> android.graphics.Color.parseColor("#EF4444")
                    }

                    val trailLoc = GeoPoint(targetLoc.latitude - 0.005, targetLoc.longitude - 0.005)
                    val historicalLine = Polyline().apply { addPoint(trailLoc); addPoint(targetLoc); color = android.graphics.Color.parseColor("#E2E8F0"); width = 8f }
                    map.overlays.add(historicalLine)

                    val routeLine = Polyline().apply { addPoint(myLoc); addPoint(targetLoc); color = heatColor; width = 24f }
                    map.overlays.add(routeLine)

                    val north = max(myLoc.latitude, targetLoc.latitude) + 0.01
                    val south = min(myLoc.latitude, targetLoc.latitude) - 0.01
                    val east = max(myLoc.longitude, targetLoc.longitude) + 0.01
                    val west = min(myLoc.longitude, targetLoc.longitude) - 0.01
                    map.zoomToBoundingBox(BoundingBox(north, east, south, west), true)

                } else if (liveBusLoc != null) {
                    map.controller.animateTo(liveBusLoc, 15.5, 1200)
                } else if (selectedRoute != null) {
                    map.controller.animateTo(selectedRoute.location, 15.5, 1200)
                    val circle = Polygon().apply { points = Polygon.pointsAsCircle(selectedRoute.location, 500.0); fillColor = android.graphics.Color.parseColor("#332563EB"); strokeColor = android.graphics.Color.parseColor("#2563EB"); strokeWidth = 4.0f }
                    map.overlays.add(circle)
                    val marker = Marker(map).apply { position = selectedRoute.location; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); title = "🚌 Target: ${selectedRoute.name}" }
                    map.overlays.add(marker)
                    marker.showInfoWindow()
                } else {
                    map.controller.setZoom(10.0); map.controller.setCenter(GeoPoint(12.9716, 77.5946))
                }
                map.invalidate()
            }
        )

        if (liveBusLoc != null) {
            Card(modifier = Modifier.align(Alignment.TopCenter).padding(top = 180.dp).shadow(8.dp, RoundedCornerShape(20.dp)), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(StatusGreen, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LIVE", fontWeight = FontWeight.Black, fontSize = 12.sp, color = StatusGreen)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("${busSpeed.toInt()} km/h", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BrandDark)
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp, bottom = 120.dp).zIndex(5f)) {
            Box(modifier = Modifier.size(48.dp).shadow(8.dp, CircleShape).background(Color.White, CircleShape).clickable {
                onRecenter()
            }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.LocationOn, contentDescription = "Recenter", tint = BrandBlue)
            }
        }
    }
}

@Composable
fun RewardsScreen(userReputation: Int) {
    val currentBadge = getTrustBadge(userReputation)

    val nextTierPoints = when {
        userReputation < 50 -> 50
        userReputation < 150 -> 150
        userReputation < 300 -> 300
        else -> 500
    }
    val progressRaw = min((userReputation.toFloat() / nextTierPoints.toFloat()), 1f)
    var animationPlayed by remember { mutableStateOf(false) }
    var isLoadingLeaderboard by remember { mutableStateOf(true) }

    val animatedProgress by animateFloatAsState(targetValue = if(animationPlayed) progressRaw else 0f, animationSpec = tween(1500, easing = FastOutSlowInEasing), label = "progress")
    val animatedPoints by animateIntAsState(targetValue = if (animationPlayed) userReputation else 0, animationSpec = tween(2000, easing = FastOutSlowInEasing), label = "points")

    LaunchedEffect(Unit) {
        animationPlayed = true
        delay(1500)
        isLoadingLeaderboard = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).systemBarsPadding().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("Rewards Program", fontSize = 32.sp, fontWeight = FontWeight.Black, color = BrandDark, modifier = Modifier.align(Alignment.Start), letterSpacing = (-1).sp)

        Spacer(modifier = Modifier.height(40.dp))
        Box(modifier = Modifier.size(180.dp).shadow(32.dp, CircleShape, spotColor = BrandBlue).background(PrimaryGradient, CircleShape), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$animatedPoints", fontSize = 56.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text("POINTS", fontSize = 14.sp, color = Color.White.copy(alpha=0.8f), fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Current Tier: $currentBadge", fontWeight = FontWeight.Black, color = BrandBlue, fontSize = 18.sp)

        Spacer(modifier = Modifier.height(40.dp))
        Card(modifier = Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha=0.05f)), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Progress to Next Tier", fontSize = 14.sp, color = BrandGray, fontWeight = FontWeight.Bold)
                    Text("$animatedPoints / $nextTierPoints", fontSize = 14.sp, color = BrandDark, fontWeight = FontWeight.Black)
                }
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(progress = animatedProgress, modifier = Modifier.fillMaxWidth().height(16.dp).clip(CircleShape), color = BrandBlue, trackColor = DividerGray)
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
        Text("Network Leaderboard", fontWeight = FontWeight.Black, fontSize = 20.sp, color = BrandDark, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoadingLeaderboard) {
            repeat(4) {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                    Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ShimmerLoader(modifier = Modifier.height(20.dp).width(120.dp).clip(RoundedCornerShape(8.dp)))
                        ShimmerLoader(modifier = Modifier.height(20.dp).width(60.dp).clip(RoundedCornerShape(8.dp)))
                    }
                }
            }
        } else {
            LeaderboardRow("1. Fleet Cmdr S.", "340 pts", Color.White)
            LeaderboardRow("2. Monitor Priya.", "290 pts", Color.White)
            LeaderboardRow("3. You (Current)", "$animatedPoints pts", BrandBlue.copy(alpha=0.1f), BrandBlue)
            LeaderboardRow("4. Commuter Kiran", "110 pts", Color.White)
        }
        Spacer(modifier = Modifier.height(140.dp))
    }
}

@Composable
fun LeaderboardRow(name: String, score: String, bgColor: Color, textColor: Color = BrandDark) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).border(1.dp, DividerGray, RoundedCornerShape(20.dp)), colors = CardDefaults.cardColors(containerColor = bgColor), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, fontWeight = FontWeight.Bold, color = textColor, fontSize = 16.sp)
            Text(score, fontWeight = FontWeight.Black, color = textColor, fontSize = 16.sp)
        }
    }
}

@Composable
fun ProfileScreen(onLogout: () -> Unit, userReputation: Int) {
    val auth = FirebaseAuth.getInstance()
    var notifsEnabled by remember { mutableStateOf(true) }
    var locationEnabled by remember { mutableStateOf(true) }
    val view = LocalView.current
    val context = LocalContext.current

    var userName by remember { mutableStateOf("Akash N") }
    var userEmail by remember { mutableStateOf(auth.currentUser?.email ?: "akash.n@vtu.ac.in") }
    var isEditing by remember { mutableStateOf(false) }

    var startAnim by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnim = true }
    val animatedPoints by animateIntAsState(targetValue = if (startAnim) userReputation else 0, animationSpec = tween(2000, easing = FastOutSlowInEasing), label = "points")

    val transition = rememberInfiniteTransition(label = "rotate")
    val rotation by transition.animateFloat(initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "rot")

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).systemBarsPadding().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Profile", fontSize = 32.sp, fontWeight = FontWeight.Black, color = BrandDark, letterSpacing = (-1).sp)
            Text(if (isEditing) "Done" else "Edit", color = BrandBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.clickable { view.performHapticClick(); isEditing = !isEditing }.padding(8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Box(contentAlignment = Alignment.BottomEnd, modifier = Modifier.animateContentSize()) {
            Box(modifier = Modifier.size(130.dp).rotate(rotation).background(AiGradient, CircleShape).clip(CircleShape), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(120.dp).background(Color.White, CircleShape).padding(4.dp)) {
                    Box(modifier = Modifier.fillMaxSize().background(PrimaryGradient, CircleShape).shadow(16.dp, CircleShape, spotColor = BrandBlue), contentAlignment = Alignment.Center) {
                        Text(userName.take(1), fontSize = 48.sp, color = Color.White, fontWeight = FontWeight.Black)
                    }
                }
            }
            if (isEditing) {
                Box(modifier = Modifier.size(36.dp).background(BrandBlue, CircleShape).border(3.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, contentDescription = "Change Photo", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        if (isEditing) {
            OutlinedTextField(
                value = userName, onValueChange = { userName = it },
                label = { Text("Full Name", color = BrandGray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).background(Color.White, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandBlue, unfocusedBorderColor = Color.Transparent, focusedContainerColor=Color.Transparent, unfocusedContainerColor=Color.Transparent)
            )
            OutlinedTextField(
                value = userEmail, onValueChange = { userEmail = it },
                label = { Text("Email Address", color = BrandGray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandBlue, unfocusedBorderColor = Color.Transparent, focusedContainerColor=Color.Transparent, unfocusedContainerColor=Color.Transparent)
            )
        } else {
            Text(userName, fontSize = 28.sp, fontWeight = FontWeight.Black, color = BrandDark)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                view.performHapticClick()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Vidyarthi Profile", "Connect with me on Vidyarthi Transit: $userEmail"))
                Toast.makeText(context, "Profile Link Copied!", Toast.LENGTH_SHORT).show()
            }.padding(8.dp)) {
                Text(userEmail, fontSize = 16.sp, color = BrandGray, modifier = Modifier.padding(end = 4.dp))
                Icon(Icons.Rounded.Share, contentDescription = "Copy", modifier = Modifier.size(14.dp), tint = BrandGray)
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Row(modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(24.dp)).shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha=0.05f)).padding(vertical = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("14", fontSize = 24.sp, fontWeight = FontWeight.Black, color = BrandDark)
                Text("Day Streak", fontSize = 12.sp, color = BrandGray, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("47", fontSize = 24.sp, fontWeight = FontWeight.Black, color = BrandDark)
                Text("Reports", fontSize = 12.sp, color = BrandGray, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$animatedPoints", fontSize = 24.sp, fontWeight = FontWeight.Black, color = BrandBlue)
                Text("Points", fontSize = 12.sp, color = BrandGray, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha=0.05f)), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Activity (Last 30 Days)", fontSize = 14.sp, color = BrandDark, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for(week in 0 until 5) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (day in 0 until 6) {
                                val intensity = ((week * 7 + day) % 5) / 4f
                                val boxColor = if (intensity == 0f) BrandBg else BrandBlue.copy(alpha = max(0.3f, intensity))
                                Box(modifier = Modifier.size(16.dp).background(boxColor, CircleShape))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha=0.05f)), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Settings", fontWeight = FontWeight.Black, fontSize = 16.sp, color = BrandDark)
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Push Notifications", fontWeight = FontWeight.Bold, color = BrandDark, fontSize = 15.sp)
                    Switch(checked = notifsEnabled, onCheckedChange = { view.performHapticClick(); notifsEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BrandBlue, uncheckedThumbColor = BrandGray, uncheckedTrackColor = BrandBg))
                }
                Divider(modifier = Modifier.padding(vertical = 16.dp), color = BrandBg)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Background Location", fontWeight = FontWeight.Bold, color = BrandDark, fontSize = 15.sp)
                    Switch(checked = locationEnabled, onCheckedChange = { view.performHapticClick(); locationEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BrandBlue, uncheckedThumbColor = BrandGray, uncheckedTrackColor = BrandBg))
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        BouncyCard(onClick = { view.performHapticClick(); onLogout() }, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Box(modifier = Modifier.fillMaxSize().background(StatusRed.copy(alpha=0.1f), RoundedCornerShape(20.dp)).border(2.dp, StatusRed.copy(alpha=0.3f), RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                Text("Log Out", fontSize = 18.sp, fontWeight = FontWeight.Black, color = StatusRed)
            }
        }
        Spacer(modifier = Modifier.height(140.dp))
    }
}

fun getStatewideSubmissionData(): List<District> {
    return listOf(
        District("BNG", "Bengaluru Urban & Rural", GeoPoint(12.9716, 77.5946), listOf(
            College("bng_mvj", "MVJ College of Engineering", listOf(BusRoute("mvj_01", "Whitefield Route", GeoPoint(12.9698, 77.7499), listOf("🛺 Ramesh: +91 98765 43210", "🛺 Suresh: +91 91234 56789", "🛺 Kumar: +91 99887 76655"), "AI Insight: Fleet nearing capacity."))),
            College("bng_pes", "PES University (RR Campus)", listOf(BusRoute("pes_01", "Banashankari Route", GeoPoint(12.9255, 77.5468), listOf("🛺 Mahesh Auto: +91 90000 11111"), "AI Insight: Traffic anomalies detected."))),
            College("bng_rvce", "RV College of Engineering", listOf(BusRoute("rv_01", "Kengeri Route", GeoPoint(12.9175, 77.4830), listOf("🛺 Kengeri Stand: 080-2222-3333"), "AI Insight: Standard telemetry."))),
            College("bng_bms", "BMS College of Engineering", listOf(BusRoute("bms_01", "Basavanagudi Route", GeoPoint(12.9406, 77.5667), listOf(), "AI Insight: Telemetry nominal."))),
            College("bng_msrit", "MSRIT", listOf(BusRoute("msrit_01", "Mathikere Fleet", GeoPoint(13.0305, 77.5649), listOf("🛺 Mathikere Shared: +91 88888 99999"), "AI Insight: Dense boarding."))),
            College("bng_dsi", "Dayananda Sagar", listOf(BusRoute("dsi_01", "KS Layout Shuttle", GeoPoint(12.9081, 77.5591), listOf("🛺 KS Layout Auto Stand: +91 99887 77777", "🛺 Kumar Autos: +91 88888 22222"), "AI Insight: High demand."))),
            College("bng_bit", "Bangalore Inst of Tech", listOf(BusRoute("bit_01", "VV Puram Link", GeoPoint(12.9515, 77.5750), listOf(), "AI Insight: Central zone clear."))),
            College("bng_mvit", "Sir MVIT", listOf(BusRoute("mvit_01", "Yelahanka Express", GeoPoint(13.1511, 77.6074), listOf(), "AI Insight: Highway traffic heavy."))),
            College("bng_nmit", "NMIT", listOf(BusRoute("nmit_01", "Govindapura Route", GeoPoint(13.1287, 77.5873), listOf(), "AI Insight: Smooth transit."))),
            College("bng_nhce", "New Horizon (NHCE)", listOf(BusRoute("nhce_01", "Bellandur Outer Ring", GeoPoint(12.9344, 77.6938), listOf(), "AI Insight: Congestion alert."))),
            College("bng_cambridge", "Cambridge Inst of Tech", listOf(BusRoute("cam_01", "TC Palya Link", GeoPoint(13.0116, 77.7051), listOf(), "AI Insight: Nominal."))),
            College("bng_reva", "REVA University", listOf(BusRoute("reva_01", "Kattigenahalli Route", GeoPoint(13.1132, 77.6347), listOf(), "AI Insight: Capacity available."))),
            College("bng_alliance", "Alliance University", listOf(BusRoute("all_01", "Anekal Corridor", GeoPoint(12.7230, 77.6950), listOf(), "AI Insight: Outskirts clear."))),
            College("bng_cmrit", "CMRIT", listOf(BusRoute("cmr_01", "AECS Layout Link", GeoPoint(12.9664, 77.7121), listOf(), "AI Insight: Rapid fill rate."))),
            College("bng_sjbit", "SJBIT", listOf(BusRoute("sjb_01", "BGS Health City", GeoPoint(12.9000, 77.4985), listOf(), "AI Insight: Steady inflow.")))
        )),
        District("CTA", "Chitradurga", GeoPoint(14.2278, 76.3980), listOf(
            College("cta_sjmit", "SJMIT Chitradurga", listOf(BusRoute("sjm_01", "Challakere Bypass", GeoPoint(14.3075, 76.6508), listOf("🛺 City Stand: +91 99000 00000"), "AI Insight: Highway clear."))),
            College("cta_gec", "Govt Engg College (GEC)", listOf(BusRoute("gec_01", "KSRTC Stand Route", GeoPoint(14.2240, 76.4020), listOf(), "AI Insight: Local transit active.")))
        )),
        District("MYS", "Mysuru", GeoPoint(12.2958, 76.6394), listOf(
            College("mys_vvce", "VVCE", listOf(BusRoute("vvc_01", "Gokulam Route", GeoPoint(12.3364, 76.6212), listOf(), "AI Insight: Quick transit."))),
            College("mys_sjce", "SJCE Mysuru", listOf(BusRoute("sjc_01", "Kuvempunagar", GeoPoint(12.2855, 76.6330), listOf(), "AI Insight: Central zone."))),
            College("mys_nie", "National Inst of Engg (NIE)", listOf(BusRoute("nie_01", "South Campus Route", GeoPoint(12.2829, 76.6415), listOf(), "AI Insight: Nominal."))),
            College("mys_mit", "MIT Mysore", listOf(BusRoute("mit_01", "Belawadi Express", GeoPoint(12.3734, 76.6433), listOf(), "AI Insight: Heavy student boarding."))),
            College("mys_atme", "ATME College", listOf(BusRoute("atme_01", "Bannur Road", GeoPoint(12.2599, 76.7388), listOf(), "AI Insight: Clear.")))
        )),
        District("MNG", "Mangaluru (Dakshina Kannada)", GeoPoint(12.9141, 74.8560), listOf(
            College("mng_nitk", "NITK Surathkal", listOf(BusRoute("ntk_01", "Surathkal Express", GeoPoint(13.0108, 74.7943), listOf("🛺 Highway Autos: +91 77777 66666"), "AI Insight: Highway nominal."))),
            College("mng_sah", "Sahyadri College", listOf(BusRoute("sah_01", "Pumpwell Route", GeoPoint(12.8687, 74.8690), listOf(), "AI Insight: Urban traffic."))),
            College("mng_sjec", "St Joseph (SJBEC)", listOf(BusRoute("sje_01", "Vamanjoor Link", GeoPoint(12.8988, 74.8981), listOf(), "AI Insight: Clear."))),
            College("mng_nmam", "NMAMIT Nitte", listOf(BusRoute("nma_01", "Karkala Shuttle", GeoPoint(13.1818, 74.9348), listOf(), "AI Insight: Outskirts active."))),
            College("mng_sri", "Srinivas IT", listOf(BusRoute("sri_01", "Valachil Route", GeoPoint(12.8710, 74.9302), listOf(), "AI Insight: Steady inflow.")))
        )),
        District("HUB", "Hubballi-Dharwad", GeoPoint(15.3647, 75.1240), listOf(
            College("hub_bvb", "BVBCET (KLE Tech)", listOf(BusRoute("bvb_01", "Vidyanagar Fleet", GeoPoint(15.3712, 75.1228), listOf(), "AI Insight: Active transit."))),
            College("hub_sdm", "SDMCET", listOf(BusRoute("sdm_01", "Dhavalagiri Route", GeoPoint(15.4372, 75.0189), listOf(), "AI Insight: Traffic anomalies."))),
            College("hub_kle", "KLECET", listOf(BusRoute("kle_01", "Airport Road Route", GeoPoint(15.3622, 75.0934), listOf(), "AI Insight: Nominal.")))
        )),
        District("BLG", "Belagavi", GeoPoint(15.8497, 74.4977), listOf(
            College("blg_git", "KLS Gogte (GIT)", listOf(BusRoute("git_01", "Udyambag Hub", GeoPoint(15.8206, 74.4965), listOf(), "AI Insight: Steady fleet."))),
            College("blg_vtu", "VTU Belagavi Campus", listOf(BusRoute("vtu_01", "Machhe Route", GeoPoint(15.7876, 74.4842), listOf(), "AI Insight: Open roads.")))
        )),
        District("DVG", "Davanagere", GeoPoint(14.4644, 75.9218), listOf(
            College("dvg_biet", "Bapuji (BIET)", listOf(BusRoute("bie_01", "Shamnur Road Link", GeoPoint(14.4449, 75.9080), listOf(), "AI Insight: Urban transit."))),
            College("dvg_ubdt", "UBDTCE", listOf(BusRoute("ubd_01", "Hadadi Road", GeoPoint(14.4578, 75.9189), listOf(), "AI Insight: Low crowding."))),
            College("dvg_gmit", "GMIT", listOf(BusRoute("gmi_01", "PB Road Express", GeoPoint(14.4845, 75.8943), listOf(), "AI Insight: Standard telemetry.")))
        )),
        District("TUM", "Tumakuru", GeoPoint(13.3392, 77.1010), listOf(
            College("tum_sit", "Siddaganga (SIT)", listOf(BusRoute("sit_01", "BH Road Shuttle", GeoPoint(13.3275, 77.1130), listOf(), "AI Insight: Steady boarding."))),
            College("tum_ssit", "Sri Siddhartha (SSIT)", listOf(BusRoute("ssi_01", "Maralur Route", GeoPoint(13.3150, 77.0853), listOf(), "AI Insight: Nominal.")))
        )),
        District("UDU", "Udupi", GeoPoint(13.3409, 74.7421), listOf(
            College("udu_mit", "MIT Manipal", listOf(BusRoute("man_01", "Edu Building Loop", GeoPoint(13.3525, 74.7928), listOf(), "AI Insight: Dense campus transit."))),
            College("udu_smvit", "SMVITM Bantakal", listOf(BusRoute("smv_01", "Shriva Link", GeoPoint(13.2384, 74.7766), listOf(), "AI Insight: Highway traffic.")))
        ))
    )
}