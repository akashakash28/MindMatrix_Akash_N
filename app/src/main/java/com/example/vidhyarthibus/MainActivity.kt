package com.example.vidhyarthibus

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
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

// --- ULTRA-PREMIUM MINIMALIST DESIGN SYSTEM ---
val IgBackground = Color(0xFFFAFAFA)
val IgWhite = Color(0xFFFFFFFF)
val IgBlack = Color(0xFF121212)
val IgGray = Color(0xFF8E8E8E)
val IgLightGray = Color(0xFFEFEFEF)
val IgDivider = Color(0xFFDBDBDB)
val IgBlue = Color(0xFF0095F6)
val StatusGreen = Color(0xFF2ECA71)
val StatusOrange = Color(0xFFF39C12)
val StatusRed = Color(0xFFE74C3C)

data class District(val code: String, val name: String, val centerPoint: GeoPoint, val colleges: List<College>)
data class College(val id: String, val name: String, val routes: List<BusRoute>)
data class BusRoute(val id: String, val name: String, val location: GeoPoint, val contacts: List<String>, val aiInsight: String = "")
data class ChatMessage(val sender: String = "", val text: String = "", val timestamp: Long = 0L, val badge: String = "")

enum class AppState { AUTH, ROLE_SELECTION, MAIN_DASHBOARD, DRIVER_DASHBOARD }
enum class UserRole { WAITING, ON_BUS, DRIVER }

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] != true) {
            Toast.makeText(this, "Location permission required.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { FirebaseDatabase.getInstance().setPersistenceEnabled(true) } catch (e: Exception) {}
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = IgBlack, secondary = IgBlue, background = IgBackground, surface = IgWhite)) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { AppEngine() }
            }
        }
    }
}

fun getTimeAgo(timestamp: Long, reporter: String): String {
    if (timestamp == 0L) return ""
    val diffMillis = System.currentTimeMillis() - timestamp
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
    val username = if (reporter.isNotEmpty() && reporter.contains("@")) reporter.substringBefore("@") else "User"
    return when {
        minutes < 1 -> "by $username just now"
        minutes == 1L -> "by $username 1 min ago"
        else -> "by $username $minutes mins ago"
    }
}

fun getTrustBadge(points: Int): String = when {
    points >= 300 -> "🛡️ Fleet Cmdr"
    points >= 150 -> "✅ Verified"
    points >= 50 -> "🌟 Contributor"
    else -> "👤 Commuter"
}

fun View.performHapticClick() {
    this.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}

@Composable
fun AppEngine() {
    val auth = FirebaseAuth.getInstance()
    var appState by remember { mutableStateOf(if (auth.currentUser == null) AppState.AUTH else AppState.ROLE_SELECTION) }
    var currentRole by remember { mutableStateOf(UserRole.WAITING) }

    Crossfade(
        targetState = appState,
        animationSpec = tween(400),
        label = "App State Transition",
        content = { state ->
            when (state) {
                AppState.AUTH -> { AuthScreen(onAuthSuccess = { appState = AppState.ROLE_SELECTION }) }
                AppState.ROLE_SELECTION -> {
                    RoleSelectionScreen(
                        onSelectRole = { role ->
                            currentRole = role
                            appState = if (role == UserRole.DRIVER) AppState.DRIVER_DASHBOARD else AppState.MAIN_DASHBOARD
                        },
                        onLogout = { auth.signOut(); appState = AppState.AUTH }
                    )
                }
                AppState.MAIN_DASHBOARD -> { MainAppScreen(initialRole = currentRole, onBackToRoles = { appState = AppState.ROLE_SELECTION }, onLogout = { auth.signOut(); appState = AppState.AUTH }) }
                AppState.DRIVER_DASHBOARD -> { DriverAppScreen(onBackToRoles = { appState = AppState.ROLE_SELECTION }, onLogout = { auth.signOut(); appState = AppState.AUTH }) }
            }
        }
    )
}

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val view = LocalView.current

    Column(modifier = Modifier.fillMaxSize().background(IgWhite).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.size(100.dp).background(IgLightGray, CircleShape), contentAlignment = Alignment.Center) {
            Text("🚌", fontSize = 48.sp)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text("Vidyarthi", fontSize = 32.sp, fontWeight = FontWeight.Black, color = IgBlack)
        Text("Connect to your campus network.", fontSize = 14.sp, color = IgGray, modifier = Modifier.padding(top = 8.dp))

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it },
            placeholder = { Text("University Email", color = IgGray) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = IgBlue, unfocusedBorderColor = IgDivider, focusedContainerColor = IgBackground, unfocusedContainerColor = IgBackground)
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            placeholder = { Text("Password", color = IgGray) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = IgBlue, unfocusedBorderColor = IgDivider, focusedContainerColor = IgBackground, unfocusedContainerColor = IgBackground)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                view.performHapticClick()
                if (email.isNotEmpty() && password.isNotEmpty()) auth.signInWithEmailAndPassword(email, password).addOnSuccessListener { onAuthSuccess() }.addOnFailureListener { Toast.makeText(context, "Login Failed", Toast.LENGTH_SHORT).show() }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IgBlue, contentColor = IgWhite)
        ) {
            Text("Log In", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Divider(color = IgDivider, thickness = 1.dp)
        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = {
            view.performHapticClick()
            if (email.isNotEmpty() && password.isNotEmpty()) auth.createUserWithEmailAndPassword(email, password).addOnSuccessListener { onAuthSuccess() }
        }) {
            Text("Don't have an account? Sign up.", fontWeight = FontWeight.Bold, color = IgBlue)
        }
    }
}

@Composable
fun RoleSelectionScreen(onSelectRole: (UserRole) -> Unit, onLogout: () -> Unit) {
    val view = LocalView.current

    Column(modifier = Modifier.fillMaxSize().background(IgBackground)) {
        Row(modifier = Modifier.fillMaxWidth().background(IgWhite).border(1.dp, IgDivider).padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Select Profile", fontSize = 22.sp, fontWeight = FontWeight.Black, color = IgBlack)
            Text("Logout", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = StatusRed, modifier = Modifier.clickable { view.performHapticClick(); onLogout() }.padding(8.dp))
        }

        Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(32.dp))

            Card(onClick = { view.performHapticClick(); onSelectRole(UserRole.WAITING) }, modifier = Modifier.fillMaxWidth().border(1.dp, IgDivider, RoundedCornerShape(16.dp)), colors = CardDefaults.cardColors(containerColor = IgWhite), shape = RoundedCornerShape(16.dp)) {
                Row(modifier = Modifier.padding(24.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(60.dp).background(IgLightGray, CircleShape), contentAlignment = Alignment.Center) { Text("🧍‍♂️", fontSize = 28.sp) }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column {
                        Text("Waiting for a bus", fontWeight = FontWeight.Black, fontSize = 18.sp, color = IgBlack)
                        Text("Track arrivals & network capacity", color = IgGray, fontSize = 14.sp, modifier = Modifier.padding(top=4.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            Card(onClick = { view.performHapticClick(); onSelectRole(UserRole.ON_BUS) }, modifier = Modifier.fillMaxWidth().border(1.dp, IgDivider, RoundedCornerShape(16.dp)), colors = CardDefaults.cardColors(containerColor = IgWhite), shape = RoundedCornerShape(16.dp)) {
                Row(modifier = Modifier.padding(24.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(60.dp).background(IgLightGray, CircleShape), contentAlignment = Alignment.Center) { Text("🚌", fontSize = 28.sp) }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column {
                        Text("Commuting on bus", fontWeight = FontWeight.Black, fontSize = 18.sp, color = IgBlue)
                        Text("Report live crowd to the network", color = IgGray, fontSize = 14.sp, modifier = Modifier.padding(top=4.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            Card(onClick = { view.performHapticClick(); onSelectRole(UserRole.DRIVER) }, modifier = Modifier.fillMaxWidth().border(1.dp, IgDivider, RoundedCornerShape(16.dp)), colors = CardDefaults.cardColors(containerColor = IgWhite), shape = RoundedCornerShape(16.dp)) {
                Row(modifier = Modifier.padding(24.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(60.dp).background(IgLightGray, CircleShape), contentAlignment = Alignment.Center) { Text("👨‍✈️", fontSize = 28.sp) }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column {
                        Text("Fleet Driver", fontWeight = FontWeight.Black, fontSize = 18.sp, color = IgBlack)
                        Text("Broadcast live telemetry", color = IgGray, fontSize = 14.sp, modifier = Modifier.padding(top=4.dp))
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
    val pulseScale by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 1.05f, animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse), label = "pulseScale")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Driver Console", fontWeight = FontWeight.Black, color = IgBlack, fontSize = 20.sp) },
                navigationIcon = { IconButton(onClick = { view.performHapticClick(); onBackToRoles() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = IgBlack) } },
                actions = { IconButton(onClick = { view.performHapticClick(); onLogout() }) { Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = StatusRed) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = IgWhite)
            )
        },
        content = { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize().background(IgBackground).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(32.dp))

                Box(modifier = Modifier.size(240.dp).scale(if (isBroadcasting) pulseScale else 1f).background(if (isBroadcasting) StatusGreen else IgLightGray, CircleShape).clickable {
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
                        Icon(if (isBroadcasting) Icons.Default.LocationOn else Icons.Default.Close, contentDescription = null, tint = if (isBroadcasting) IgWhite else IgGray, modifier = Modifier.size(72.dp))
                        Text(if (isBroadcasting) "LIVE" else "GO ONLINE", color = if (isBroadcasting) IgWhite else IgGray, fontWeight = FontWeight.Black, fontSize = 24.sp, modifier = Modifier.padding(top = 12.dp))
                    }
                }

                Spacer(modifier = Modifier.height(60.dp))

                Card(modifier = Modifier.fillMaxWidth().border(1.dp, IgDivider, RoundedCornerShape(16.dp)).animateContentSize(), colors = CardDefaults.cardColors(containerColor = IgWhite), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(32.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Current Speed", color = IgGray, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${currentSpeed.toInt()} km/h", fontSize = 56.sp, fontWeight = FontWeight.Black, color = IgBlack)
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
        bottomBar = {
            NavigationBar(containerColor = IgWhite, contentColor = IgBlack, tonalElevation = 0.dp, modifier = Modifier.border(1.dp, IgDivider)) {
                NavigationBarItem(selected = selectedTab == 0, onClick = { view.performHapticClick(); selectedTab = 0 }, icon = { Icon(Icons.Default.LocationOn, contentDescription = "Transit") }, label = { Text("Map", fontWeight = FontWeight.Bold, fontSize = 11.sp) }, colors = NavigationBarItemDefaults.colors(indicatorColor = IgWhite, selectedIconColor = IgBlack, unselectedIconColor = IgGray, selectedTextColor = IgBlack, unselectedTextColor = IgGray))
                NavigationBarItem(selected = selectedTab == 1, onClick = { view.performHapticClick(); selectedTab = 1 }, icon = { Icon(Icons.Default.Star, contentDescription = "Rewards") }, label = { Text("Rewards", fontWeight = FontWeight.Bold, fontSize = 11.sp) }, colors = NavigationBarItemDefaults.colors(indicatorColor = IgWhite, selectedIconColor = IgBlack, unselectedIconColor = IgGray, selectedTextColor = IgBlack, unselectedTextColor = IgGray))
                NavigationBarItem(selected = selectedTab == 2, onClick = { view.performHapticClick(); selectedTab = 2 }, icon = { Icon(Icons.Default.AccountCircle, contentDescription = "Profile") }, label = { Text("Profile", fontWeight = FontWeight.Bold, fontSize = 11.sp) }, colors = NavigationBarItemDefaults.colors(indicatorColor = IgWhite, selectedIconColor = IgBlack, unselectedIconColor = IgGray, selectedTextColor = IgBlack, unselectedTextColor = IgGray))
            }
        },
        content = { paddingValues ->
            Box(modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding()).fillMaxSize().background(IgBackground)) {
                Crossfade(targetState = selectedTab, label = "Tab Fade", animationSpec = tween(300), content = { tab ->
                    when (tab) {
                        0 -> BusDashboard(isWaitingMode = isWaitingMode, onSwitchMode = { isWaitingMode = it }, onBackToRoles = onBackToRoles, userReputation = userReputation, onReputationGain = { userReputation += it })
                        1 -> RewardsScreen(userReputation)
                        2 -> ProfileScreen(onLogout)
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
    val currentUsername = if (rawEmail.contains("@")) rawEmail.substringBefore("@").replaceFirstChar { it.uppercase() } else "Commuter"
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

    val animatedCrowdProgress by animateFloatAsState(targetValue = crowdLevel, animationSpec = tween(1000, easing = LinearOutSlowInEasing), label = "crowd")
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    var newChatMessage by remember { mutableStateOf("") }
    var showAutoDialog by remember { mutableStateOf(false) }
    var pendingCrowdUpdate by remember { mutableStateOf<Int?>(null) }

    var liveBusLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var myLiveLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var myLiveSpeed by remember { mutableFloatStateOf(0f) }

    var userLocationInput by remember { mutableStateOf("") }
    var isLocatingUser by remember { mutableStateOf(false) }
    var hudAlertMessage by remember { mutableStateOf("") }

    val quickReplies = listOf("🚦 Heavy Traffic", "🏃‍♂️ Fast", "⏳ Delayed", "🟢 Clear", "🚌 Full")

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
                        Toast.makeText(context, "Location Verified!", Toast.LENGTH_SHORT).show()
                    } else {
                        // Emulator Fallback
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
        database.child("routes_chat").child(currentRoute.id).push().setValue(ChatMessage(sender = currentUsername, text = newChatMessage.trim(), timestamp = System.currentTimeMillis(), badge = currentUserBadge))
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
            title = { Text("Confirm Broadcast", fontWeight = FontWeight.Black) },
            text = { Text("Set network status to '$statusText'?", fontSize = 15.sp) },
            confirmButton = { Button(onClick = { view.performHapticClick(); updateCrowd(pendingCrowdUpdate!!); pendingCrowdUpdate = null }, colors = ButtonDefaults.buttonColors(containerColor = IgBlue)) { Text("Broadcast", color = IgWhite, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { view.performHapticClick(); pendingCrowdUpdate = null }) { Text("Cancel", color = IgGray, fontWeight = FontWeight.Bold) } },
            shape = RoundedCornerShape(16.dp), containerColor = IgWhite
        )
    }

    if (showAutoDialog && selectedRoute != null) {
        AlertDialog(
            onDismissRequest = { showAutoDialog = false },
            title = { Text("Partnered Transit", fontWeight = FontWeight.Black) },
            text = {
                Column {
                    Text("Verified local drivers:", color = IgGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    if (selectedRoute?.contacts.isNullOrEmpty()) {
                        Text("No direct contacts found.", fontWeight = FontWeight.Bold, color = StatusRed)
                    } else {
                        selectedRoute?.contacts?.forEach { contact ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, IgDivider, RoundedCornerShape(8.dp)), colors = CardDefaults.cardColors(containerColor = IgWhite), shape = RoundedCornerShape(8.dp)) {
                                Text(contact, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, color = IgBlack)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(modifier = Modifier.fillMaxWidth().border(1.dp, StatusGreen, RoundedCornerShape(8.dp)), colors = CardDefaults.cardColors(containerColor = StatusGreen.copy(alpha=0.1f)), shape = RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Estimated Fare:", color = StatusGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text("₹45.00", color = IgBlack, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { view.performHapticClick(); showAutoDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = IgBlack)) { Text("Close", color = IgWhite) } },
            dismissButton = {
                OutlinedButton(onClick = {
                    view.performHapticClick()
                    val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode("auto rickshaw stand near ${selectedRoute!!.location.latitude}, ${selectedRoute!!.location.longitude}")}")
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }) } catch (e: Exception) { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                }) { Text("Search Maps", color = IgBlack, fontWeight = FontWeight.Bold) }
            }, containerColor = IgWhite, shape = RoundedCornerShape(16.dp)
        )
    }

    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded, skipHiddenState = true))

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = if (selectedRoute != null) 280.dp else 0.dp,
        sheetContainerColor = IgWhite,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetDragHandle = { BottomSheetDefaults.DragHandle() },
        content = { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {

                OpenMapScreen(modifier = Modifier.fillMaxSize(), selectedRoute = selectedRoute, liveBusLoc = liveBusLocation, myLoc = myLiveLocation, crowdLvl = crowdLevel)

                Box(modifier = Modifier.fillMaxWidth().height(140.dp).background(Brush.verticalGradient(colors = listOf(IgWhite.copy(alpha=0.95f), Color.Transparent))).zIndex(1.5f))

                AnimatedVisibility(
                    visible = hudAlertMessage.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { -it }),
                    exit = slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp).zIndex(5f)
                ) {
                    Card(modifier = Modifier.fillMaxWidth(0.9f).shadow(8.dp, RoundedCornerShape(12.dp)), colors = CardDefaults.cardColors(containerColor = StatusRed), shape = RoundedCornerShape(12.dp)) {
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = IgWhite, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(hudAlertMessage, color = IgWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp).zIndex(4f)) {
                    IconButton(onClick = {
                        view.performHapticClick()
                        val intent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:112") }
                        try { context.startActivity(intent) } catch (e: Exception) {}
                    }, modifier = Modifier.size(44.dp).background(IgWhite, CircleShape).border(1.dp, StatusRed, CircleShape)) {
                        Text("SOS", color = StatusRed, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }

                Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().zIndex(2f).padding(top = if(hudAlertMessage.isNotEmpty()) 70.dp else 16.dp, start = 16.dp, end = 16.dp).animateContentSize()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).padding(end = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { view.performHapticClick(); onBackToRoles() }, modifier = Modifier.background(IgWhite, CircleShape).border(1.dp, IgDivider, CircleShape).size(44.dp)) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = IgBlack)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Card(modifier = Modifier.weight(1f).height(44.dp).border(1.dp, IgDivider, RoundedCornerShape(12.dp)), colors = CardDefaults.cardColors(containerColor = IgWhite), shape = RoundedCornerShape(12.dp)) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(if (isWaitingMode) "🧍 Waiting Mode Active" else "🚌 Commuting Mode Active", color = IgBlack, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    if (selectedRoute == null) {
                        ExposedDropdownMenuBox(expanded = districtMenuExpanded, onExpandedChange = { districtMenuExpanded = !districtMenuExpanded }, content = {
                            OutlinedTextField(
                                value = selectedDistrict?.let { "${it.code} - ${it.name}" } ?: "Select Operational Zone...", onValueChange = {}, readOnly = true,
                                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = IgGray) },
                                modifier = Modifier.menuAnchor().fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = IgDivider, focusedBorderColor = IgBlue, focusedContainerColor = IgWhite, unfocusedContainerColor = IgWhite),
                                textStyle = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            )
                            ExposedDropdownMenu(expanded = districtMenuExpanded, onDismissRequest = { districtMenuExpanded = false }, modifier = Modifier.background(IgWhite)) {
                                districtList.forEach { district -> DropdownMenuItem(text = { Text("${district.code} - ${district.name}", fontWeight = FontWeight.Bold) }, onClick = { view.performHapticClick(); selectedDistrict = district; selectedCollege = null; selectedRoute = null; districtMenuExpanded = false }) }
                            }
                        })

                        if (selectedDistrict != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            ExposedDropdownMenuBox(expanded = collegeMenuExpanded, onExpandedChange = { collegeMenuExpanded = !collegeMenuExpanded }, content = {
                                OutlinedTextField(
                                    value = selectedCollege?.name ?: "Select Institution...", onValueChange = {}, readOnly = true,
                                    leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = IgGray) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = IgDivider, focusedBorderColor = IgBlue, focusedContainerColor = IgWhite, unfocusedContainerColor = IgWhite),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                )
                                ExposedDropdownMenu(expanded = collegeMenuExpanded, onDismissRequest = { collegeMenuExpanded = false }, modifier = Modifier.background(IgWhite)) {
                                    selectedDistrict!!.colleges.forEach { college -> DropdownMenuItem(text = { Text(college.name, fontWeight = FontWeight.Bold) }, onClick = { view.performHapticClick(); selectedCollege = college; selectedRoute = null; collegeMenuExpanded = false }) }
                                }
                            })
                        }

                        if (selectedCollege != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            ExposedDropdownMenuBox(expanded = routeMenuExpanded, onExpandedChange = { routeMenuExpanded = !routeMenuExpanded }, content = {
                                OutlinedTextField(
                                    value = selectedRoute?.name ?: "Select Target Route...", onValueChange = {}, readOnly = true,
                                    leadingIcon = { Icon(Icons.Default.List, contentDescription = null, tint = IgGray) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = IgDivider, focusedBorderColor = IgBlue, focusedContainerColor = IgWhite, unfocusedContainerColor = IgWhite),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                )
                                ExposedDropdownMenu(expanded = routeMenuExpanded, onDismissRequest = { routeMenuExpanded = false }, modifier = Modifier.background(IgWhite)) {
                                    selectedCollege!!.routes.forEach { route -> DropdownMenuItem(text = { Text(route.name, fontWeight = FontWeight.Bold) }, onClick = { view.performHapticClick(); selectedRoute = route; routeMenuExpanded = false }) }
                                }
                            })
                        }
                    } else {
                        Card(modifier = Modifier.fillMaxWidth().border(1.dp, IgDivider, RoundedCornerShape(16.dp)), colors = CardDefaults.cardColors(containerColor = IgWhite), shape = RoundedCornerShape(16.dp)) {
                            Column {
                                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text("Tracking Route:", color = IgGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(selectedRoute!!.name, color = IgBlack, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = {
                                            view.performHapticClick()
                                            val sendIntent: Intent = Intent().apply { action = Intent.ACTION_SEND; putExtra(Intent.EXTRA_TEXT, "I'm tracking the ${selectedRoute!!.name} bus on Vidyarthi!"); type = "text/plain" }
                                            context.startActivity(Intent.createChooser(sendIntent, null))
                                        }, modifier = Modifier.background(IgLightGray, CircleShape).size(40.dp)) { Icon(Icons.Default.Share, contentDescription = "Share", tint = IgBlack, modifier = Modifier.size(18.dp)) }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(onClick = { view.performHapticClick(); selectedRoute = null; userLocationInput = "" }, modifier = Modifier.background(IgLightGray, CircleShape).size(40.dp)) { Icon(Icons.Default.Close, contentDescription = "Clear", tint = IgBlack, modifier = Modifier.size(18.dp)) }
                                    }
                                }
                                Box(modifier = Modifier.fillMaxWidth().background(IgLightGray).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                    Text("👥 ${usersOnBusCount + 3} students looking at this route", color = IgGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (isWaitingMode) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = userLocationInput, onValueChange = { userLocationInput = it },
                                placeholder = { Text("Search location...", color = IgGray, fontSize = 14.sp) },
                                leadingIcon = { IconButton(onClick = { fetchHardwareGPS() }) { Icon(Icons.Default.LocationOn, contentDescription = "GPS", tint = IgBlue) } },
                                trailingIcon = {
                                    IconButton(onClick = { view.performHapticClick(); geocodeUserLocation() }) { if (isLocatingUser) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = IgBlue, strokeWidth = 2.dp) else Icon(Icons.Default.Search, tint = IgBlack, contentDescription = null) }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { geocodeUserLocation() }),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = IgDivider, focusedBorderColor = IgBlue, focusedContainerColor = IgWhite, unfocusedContainerColor = IgWhite)
                            )
                        }
                    }
                }
            }
        },
        sheetContent = {
            if (selectedRoute != null) {
                Column(modifier = Modifier.padding(horizontal = 20.dp).fillMaxHeight(0.85f).verticalScroll(rememberScrollState()).animateContentSize()) {

                    if (isWaitingMode) {
                        Text("ROUTE INTELLIGENCE", fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, color = IgGray, modifier = Modifier.padding(top=8.dp))
                        Spacer(modifier = Modifier.height(16.dp))

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

                        Card(modifier = Modifier.fillMaxWidth().border(1.dp, IgDivider, RoundedCornerShape(16.dp)), colors = CardDefaults.cardColors(containerColor = IgWhite), shape = RoundedCornerShape(16.dp)) {
                            Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Fleet Status", fontSize = 12.sp, color = IgGray, fontWeight = FontWeight.Bold)
                                    Text(travelStatus, fontSize = 18.sp, fontWeight = FontWeight.Black, color = if(liveBusLocation != null && distanceMeters in 1.0..500.0) StatusGreen else IgBlack)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Distance", fontSize = 12.sp, color = IgGray, fontWeight = FontWeight.Bold)
                                    val distStr = if (distanceMeters > 1000) String.format("%.1f km", distanceMeters/1000) else String.format("%.0f m", distanceMeters)
                                    Text(if (distanceMeters == 0.0) "Calc..." else distStr, fontSize = 20.sp, fontWeight = FontWeight.Black, color = IgBlue)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Text("PREDICTED CROWD TRENDS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IgGray, letterSpacing = 1.sp)
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
                                    Box(modifier = Modifier.size(14.dp).background(slot.second, CircleShape))
                                    Text(slot.first, fontSize = 11.sp, color = IgGray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top=6.dp))
                                }
                                if (index < timeSlots.size - 1) {
                                    Box(modifier = Modifier.height(2.dp).weight(1f).background(IgLightGray).align(Alignment.CenterVertically).offset(y = -8.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        if (crowdLevel >= 0.7f) {
                            Card(modifier = Modifier.fillMaxWidth().border(1.dp, IgDivider, RoundedCornerShape(16.dp)), colors = CardDefaults.cardColors(containerColor = IgWhite), shape = RoundedCornerShape(16.dp)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = StatusOrange, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Bus Heavily Crowded", fontWeight = FontWeight.Black, color = IgBlack, fontSize = 15.sp)
                                    }
                                    Text("Skip the wait. Book a ride directly from your location.", fontSize = 13.sp, color = IgGray, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                                    Button(onClick = { view.performHapticClick(); showAutoDialog = true }, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = IgLightGray), shape = RoundedCornerShape(8.dp)) {
                                        Text("Find Partnered Autos", color = IgBlack, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Text("Capacity: ${totalSeats - occupiedSeats} seats available", fontWeight = FontWeight.Bold, color = StatusGreen, modifier = Modifier.align(Alignment.CenterHorizontally), fontSize = 14.sp)
                        }

                    } else {
                        Text("SEAT TRACKING", fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, color = IgGray, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(modifier = Modifier.height(16.dp))

                        val barColor = if (crowdLevel < 0.3f) StatusGreen else if (crowdLevel < 0.7f) StatusOrange else StatusRed
                        LinearProgressIndicator(progress = animatedCrowdProgress, modifier = Modifier.fillMaxWidth().height(16.dp).clip(CircleShape), color = barColor, trackColor = IgLightGray)

                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Occupied: $occupiedSeats", fontWeight = FontWeight.Bold, color = IgGray, fontSize = 13.sp)
                            Text("Available: ${totalSeats - occupiedSeats}", fontWeight = FontWeight.Bold, color = IgGray, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                        Text("Update Status:", fontWeight = FontWeight.Black, fontSize = 15.sp, color = IgBlack)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { view.performHapticClick(); pendingCrowdUpdate = (totalSeats * 0.2).toInt() }, modifier = Modifier.weight(1f).height(48.dp), contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = StatusGreen), shape = RoundedCornerShape(8.dp)) { Text("Empty", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = IgWhite) }
                            Button(onClick = { view.performHapticClick(); pendingCrowdUpdate = (totalSeats * 0.6).toInt() }, modifier = Modifier.weight(1f).height(48.dp), contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = StatusOrange), shape = RoundedCornerShape(8.dp)) { Text("Moderate", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = IgWhite) }
                            Button(onClick = { view.performHapticClick(); pendingCrowdUpdate = (totalSeats * 1.0).toInt() }, modifier = Modifier.weight(1f).height(48.dp), contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = StatusRed), shape = RoundedCornerShape(8.dp)) { Text("Full", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = IgWhite) }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 24.dp), color = IgDivider, thickness = 1.dp)

                    Text("LIVE COMMUTER CHAT", fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, color = IgGray)

                    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp).animateContentSize()) {
                        if (chatMessages.isEmpty()) {
                            Text("No messages yet. Be the first!", color = IgGray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
                        } else {
                            chatMessages.forEach { msg ->
                                val isMe = msg.sender == currentUsername
                                val isAI = msg.sender == "AI Agent"

                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                                    if (!isMe) {
                                        Box(modifier = Modifier.size(36.dp).background(if (isAI) IgBlue else IgLightGray, CircleShape), contentAlignment = Alignment.Center) {
                                            Icon(if (isAI) Icons.Default.Star else Icons.Default.AccountCircle, contentDescription = null, tint = if (isAI) IgWhite else IgGray, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f), horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (!isMe) {
                                                Text(msg.sender, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = IgBlack)
                                                if (msg.badge.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(msg.badge, fontSize = 10.sp, color = IgGray, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            Text(getTimeAgo(msg.timestamp, ""), fontSize = 11.sp, color = IgGray)
                                        }

                                        val bubbleColor = if (isAI) IgLightGray else if (isMe) IgBlue else IgWhite
                                        val textColor = if (isMe) IgWhite else IgBlack
                                        val bubbleShape = if (isMe) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp) else RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)

                                        Box(modifier = Modifier.padding(top = 4.dp).background(bubbleColor, bubbleShape).border(if(bubbleColor == IgWhite) 1.dp else 0.dp, IgDivider, bubbleShape).padding(horizontal = 16.dp, vertical = 10.dp)) {
                                            Text(msg.text, fontSize = 15.sp, color = textColor)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(quickReplies) { reply ->
                            ElevatedAssistChip(
                                onClick = { view.performHapticClick(); newChatMessage = reply },
                                label = { Text(reply, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IgBlack) },
                                shape = RoundedCornerShape(16.dp),
                                colors = AssistChipDefaults.elevatedAssistChipColors(containerColor = IgWhite)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newChatMessage, onValueChange = { newChatMessage = it },
                            placeholder = { Text("Add a comment...", fontSize = 15.sp, color = IgGray) },
                            modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = IgDivider, unfocusedBorderColor = IgDivider, focusedContainerColor = IgBackground, unfocusedContainerColor = IgBackground)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

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
                                database.child("routes_chat").child(currentRouteSnap.id).push().setValue(ChatMessage(sender = "AI Agent", text = aiMessageText, timestamp = System.currentTimeMillis(), badge = "🤖 System"))
                            }
                        }, modifier = Modifier.background(IgLightGray, CircleShape).size(44.dp)) { Icon(Icons.Default.Star, contentDescription = "Ask AI", tint = IgBlack, modifier = Modifier.size(20.dp)) }

                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = {
                            view.performHapticClick()
                            val currentRoute = selectedRoute ?: return@IconButton
                            if (newChatMessage.isBlank()) return@IconButton
                            database.child("routes_chat").child(currentRoute.id).push().setValue(ChatMessage(sender = currentUsername, text = newChatMessage.trim(), timestamp = System.currentTimeMillis(), badge = currentUserBadge))
                            newChatMessage = ""
                        }, modifier = Modifier.background(IgBlue, CircleShape).size(44.dp)) { Icon(Icons.Default.Send, contentDescription = "Send", tint = IgWhite, modifier = Modifier.size(18.dp)) }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            } else { Box(modifier = Modifier.height(1.dp)) }
        }
    )
}

// REAL-TIME CACHED MAP RENDERER
@Composable
fun OpenMapScreen(modifier: Modifier = Modifier, selectedRoute: BusRoute?, liveBusLoc: GeoPoint?, myLoc: GeoPoint?, crowdLvl: Float) {
    val context = LocalContext.current
    Configuration.getInstance().userAgentValue = context.packageName

    // Prevent map from constantly re-rendering entirely, saving massive memory
    val mapView = remember { MapView(context).apply { setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true); minZoomLevel = 4.0 } }

    var lastRenderedBusLat by remember { mutableDoubleStateOf(0.0) }
    var lastRenderedBusLng by remember { mutableDoubleStateOf(0.0) }
    var lastRenderedMyLat by remember { mutableDoubleStateOf(0.0) }
    var lastRenderedMyLng by remember { mutableDoubleStateOf(0.0) }

    AndroidView(
        modifier = modifier,
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

            if (!needsUpdate) return@AndroidView

            if (targetLoc != null) {
                lastRenderedBusLat = targetLoc.latitude
                lastRenderedBusLng = targetLoc.longitude
            }
            if (myLoc != null) {
                lastRenderedMyLat = myLoc.latitude
                lastRenderedMyLng = myLoc.longitude
            }

            map.overlays.clear()

            myLoc?.let {
                val meMarker = Marker(map).apply { position = it; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); title = "🧍 You"; icon = ContextCompat.getDrawable(context, android.R.drawable.presence_online) }
                map.overlays.add(meMarker)
            }

            if (myLoc != null && targetLoc != null) {
                val heatColor = when {
                    crowdLvl < 0.4f -> android.graphics.Color.parseColor("#4CAF50")
                    crowdLvl < 0.8f -> android.graphics.Color.parseColor("#F39C12")
                    else -> android.graphics.Color.parseColor("#E74C3C")
                }

                val trailLoc = GeoPoint(targetLoc.latitude - 0.005, targetLoc.longitude - 0.005)
                val historicalLine = Polyline().apply { addPoint(trailLoc); addPoint(targetLoc); color = android.graphics.Color.parseColor("#DBDBDB"); width = 8f }
                map.overlays.add(historicalLine)

                val routeLine = Polyline().apply { addPoint(myLoc); addPoint(targetLoc); color = heatColor; width = 16f }
                map.overlays.add(routeLine)

                val north = max(myLoc.latitude, targetLoc.latitude) + 0.02
                val south = min(myLoc.latitude, targetLoc.latitude) - 0.02
                val east = max(myLoc.longitude, targetLoc.longitude) + 0.02
                val west = min(myLoc.longitude, targetLoc.longitude) - 0.02
                map.zoomToBoundingBox(BoundingBox(north, east, south, west), true)

            } else if (liveBusLoc != null) {
                map.controller.animateTo(liveBusLoc, 15.5, 1200)
            } else if (selectedRoute != null) {
                map.controller.animateTo(selectedRoute.location, 15.5, 1200)
                val circle = Polygon().apply { points = Polygon.pointsAsCircle(selectedRoute.location, 500.0); fillColor = android.graphics.Color.parseColor("#330095F6"); strokeColor = android.graphics.Color.parseColor("#0095F6"); strokeWidth = 3.0f }
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
    val animatedProgress by animateFloatAsState(targetValue = if(animationPlayed) progressRaw else 0f, animationSpec = tween(1500, easing = FastOutSlowInEasing), label = "progress")

    LaunchedEffect(Unit) { animationPlayed = true }

    Column(modifier = Modifier.fillMaxSize().background(IgBackground).padding(20.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(24.dp))
        Text("Rewards", fontSize = 28.sp, fontWeight = FontWeight.Black, color = IgBlack, modifier = Modifier.align(Alignment.Start).padding(bottom = 32.dp))

        Box(modifier = Modifier.size(160.dp).background(IgWhite, CircleShape).border(1.dp, IgDivider, CircleShape), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$userReputation", fontSize = 48.sp, fontWeight = FontWeight.Black, color = IgBlack)
                Text("PTS", fontSize = 14.sp, color = IgGray, fontWeight = FontWeight.Bold)
            }
        }
        Text("Current Tier: $currentBadge", fontWeight = FontWeight.Black, color = IgBlue, fontSize = 16.sp, modifier = Modifier.padding(top = 24.dp))

        Spacer(modifier = Modifier.height(32.dp))
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Progress to Next Tier", fontSize = 13.sp, color = IgGray, fontWeight = FontWeight.Bold)
                Text("$userReputation / $nextTierPoints", fontSize = 13.sp, color = IgBlack, fontWeight = FontWeight.Black)
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(progress = animatedProgress, modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape), color = IgBlue, trackColor = IgLightGray)
        }

        Spacer(modifier = Modifier.height(48.dp))
        Text("Leaderboard", fontWeight = FontWeight.Black, fontSize = 20.sp, color = IgBlack, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(16.dp))
        LeaderboardRow("1. Fleet Cmdr S.", "340 pts", IgWhite)
        LeaderboardRow("2. Monitor Priya.", "290 pts", IgWhite)
        LeaderboardRow("3. You (Current)", "$userReputation pts", IgLightGray)
        LeaderboardRow("4. Commuter Kiran", "110 pts", IgWhite)
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun LeaderboardRow(name: String, score: String, bgColor: Color) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).border(1.dp, IgDivider, RoundedCornerShape(16.dp)), colors = CardDefaults.cardColors(containerColor = bgColor), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, fontWeight = FontWeight.Bold, color = IgBlack, fontSize = 16.sp)
            Text(score, fontWeight = FontWeight.Black, color = IgBlack, fontSize = 16.sp)
        }
    }
}

@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    var notifsEnabled by remember { mutableStateOf(true) }
    var locationEnabled by remember { mutableStateOf(true) }
    var dataSaver by remember { mutableStateOf(false) }
    val view = LocalView.current

    var userName by remember { mutableStateOf("Akash N") }
    var userEmail by remember { mutableStateOf(auth.currentUser?.email ?: "akash.n@vtu.ac.in") }
    var isEditing by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(IgBackground).padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Profile", fontSize = 28.sp, fontWeight = FontWeight.Black, color = IgBlack)
            Text(if (isEditing) "Done" else "Edit", color = IgBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.clickable { view.performHapticClick(); isEditing = !isEditing }.padding(8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Box(contentAlignment = Alignment.BottomEnd, modifier = Modifier.animateContentSize()) {
            Box(modifier = Modifier.size(100.dp).background(IgLightGray, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(80.dp), tint = IgGray)
            }
            if (isEditing) {
                Box(modifier = Modifier.size(32.dp).background(IgBlue, CircleShape).border(2.dp, IgWhite, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = "Change Photo", tint = IgWhite, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (isEditing) {
            OutlinedTextField(
                value = userName, onValueChange = { userName = it },
                label = { Text("Full Name", color = IgGray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = IgBlue, unfocusedBorderColor = IgDivider)
            )
            OutlinedTextField(
                value = userEmail, onValueChange = { userEmail = it },
                label = { Text("Email Address", color = IgGray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = IgBlue, unfocusedBorderColor = IgDivider)
            )
        } else {
            Text(userName, fontSize = 24.sp, fontWeight = FontWeight.Black, color = IgBlack)
            Text(userEmail, fontSize = 15.sp, color = IgGray, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(modifier = Modifier.height(40.dp))

        Card(modifier = Modifier.fillMaxWidth().border(1.dp, IgDivider, RoundedCornerShape(16.dp)), colors = CardDefaults.cardColors(containerColor = IgWhite), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Metrics", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = IgBlack)
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔥", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Current Streak", color = IgBlack, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("14 Days", color = IgGray, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Divider(modifier = Modifier.padding(vertical = 16.dp), color = IgLightGray)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📊", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Contributions", color = IgBlack, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("47 Reports", color = IgGray, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text("Activity (Last 30 Days)", fontSize = 12.sp, color = IgGray, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for(week in 0 until 5) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (day in 0 until 6) {
                                val intensity = ((week * 7 + day) % 5) / 4f
                                val boxColor = if (intensity == 0f) IgLightGray else StatusGreen.copy(alpha = max(0.3f, intensity))
                                Box(modifier = Modifier.size(14.dp).background(boxColor, RoundedCornerShape(2.dp)))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth().border(1.dp, IgDivider, RoundedCornerShape(16.dp)), colors = CardDefaults.cardColors(containerColor = IgWhite), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = IgBlack)
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Push Notifications", fontWeight = FontWeight.Bold, color = IgBlack, fontSize = 15.sp)
                    Switch(checked = notifsEnabled, onCheckedChange = { view.performHapticClick(); notifsEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = IgWhite, checkedTrackColor = IgBlue, uncheckedThumbColor = IgGray, uncheckedTrackColor = IgLightGray))
                }
                Divider(modifier = Modifier.padding(vertical = 16.dp), color = IgLightGray)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Background Location", fontWeight = FontWeight.Bold, color = IgBlack, fontSize = 15.sp)
                    Switch(checked = locationEnabled, onCheckedChange = { view.performHapticClick(); locationEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = IgWhite, checkedTrackColor = IgBlue, uncheckedThumbColor = IgGray, uncheckedTrackColor = IgLightGray))
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text("Log Out", fontSize = 16.sp, fontWeight = FontWeight.Black, color = StatusRed, modifier = Modifier.clickable { view.performHapticClick(); onLogout() }.padding(16.dp))
        Spacer(modifier = Modifier.height(40.dp))
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