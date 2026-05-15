# VidhyarthiBus - Statewide Transit Network

VidhyarthiBus is a premium Android application designed to simplify and smarten the daily commute for students across Karnataka. It provides a real-time, community-driven transit tracking system that connects students with their institutional bus networks.

## 🚀 Key Features

### 1. Smart Transit Tracking
- **Statewide Coverage**: Supports multiple operational zones including Bengaluru, Mysuru, Mangaluru, Hubballi, and more.
- **Institutional Integration**: Comprehensive database of colleges (e.g., MVJCE, PESU, RVCE) and their specific bus routes.
- **Live Map Visualization**: Integrated OpenStreetMap (osmdroid) for tracking live fleet locations and student proximity.

### 2. Community-Driven Crowdsourcing
- **Live Fleet Status**: Students on board can broadcast the bus crowd level (Empty, Nearing Capacity, or Full).
- **Verified Telemetry**: Time-stamped updates ensure that commuters have the most fresh and reliable information.
- **Commuter Chat**: Real-time community feed for sharing updates, delays, or general transit info.

### 3. Smart Alternatives
- **Shared Auto Integration**: When buses are full, the app suggests verified local shared-auto contacts and stands nearby.
- **AI-Powered Insights**: Automated status messages based on crowd levels and reporting frequency.

### 4. Gamified Rewards System
- **Reputation Points**: Earn points for every status update and report submitted.
- **Leaderboards**: Compete with other commuters to become a "Fleet Commander" or "Transit Monitor."
- **Activity Metrics**: Track your contribution streaks and total reports through a sleek profile dashboard.

## 🛠 Tech Stack

- **UI Framework**: Jetpack Compose (Modern Declarative UI)
- **Language**: Kotlin
- **Backend**: Firebase (Authentication & Realtime Database)
- **Maps**: osmdroid (OpenStreetMap for Android)
- **Location Services**: Google Play Services Location
- **Architecture**: Clean, component-based design with state management using `remember` and `mutableStateOf`.

## 📦 Installation & Setup

1. **Clone the repository**:
   ```bash
   git clone https://github.com/your-username/VidhyarthiBus.git
   ```
2. **Firebase Setup**:
   - Add your `google-services.json` to the `app/` directory.
   - Enable Email/Password authentication in the Firebase Console.
   - Set up a Realtime Database instance.
3. **Build & Run**:
   - Open the project in Android Studio.
   - Sync Gradle and run on a physical device or emulator.

## 🛡 Permissions Required
- `ACCESS_FINE_LOCATION`: For accurate route tracking and distance calculations.
- `INTERNET`: For real-time database updates and map tile loading.

---
*Developed for a smarter, safer, and more connected student transit experience.*
