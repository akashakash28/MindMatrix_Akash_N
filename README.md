# 🚌 VidhyarthiBus: The Digital Heartbeat of Campus Transit

![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue.svg?style=for-the-badge&logo=kotlin)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Modern%20UI-green.svg?style=for-the-badge&logo=jetpack-compose)
![Firebase](https://img.shields.io/badge/Firebase-Realtime%20Backend-orange.svg?style=for-the-badge&logo=firebase)

### **Track Easy. Manage Smart. Travel Together.**

**VidhyarthiBus** isn't just an app; it's a movement. It was born out of a simple, relatable frustration: the morning anxiety of standing at a bus stop, staring down the road, and wondering if you've already missed your ride or if it's too full to board. 

We’ve built a high-performance, community-driven ecosystem that connects students across Karnataka, transforming a stressful commute into a smart, shared experience.

---

## 🌈 The Problem We’re Solving
For most students, the college bus is the only affordable way to reach campus. But traditional transit systems are "black boxes"—you never know where the bus is.
*   **The Waiting Game**: Standing in the sun/rain for a bus that might be 20 minutes late.
*   **The Crowd Shock**: Seeing your bus arrive, only to realize it's so packed you can't even get on.
*   **The Plan B Panic**: Scrambling to find an auto-rickshaw when you're already running late for an internal exam.

**VidhyarthiBus makes these problems disappear.**

---

## 🚀 Deep Dive: The "Pro" Experience

### 1. 📍 The Living Map (osmdroid)
We don't use static schedules. Our map is a living entity. 
*   **For the Waiting Student**: See the bus moving in real-time. We calculate the exact distance and provide an ETA based on live telemetry.
*   **Visual Proximity**: A sleek pulse animation surrounds the bus, giving you a visual "radar" of its location.

### 2. 👥 The Power of the "On-Bus" Community
The app's intelligence comes from its users. Students already on the bus can broadcast the "Crowd Status" with a single tap.
*   **Empty**: Plenty of seats—relax and board!
*   **Moderate**: You'll find a place to stand.
*   **Full**: Don't wait—look for alternatives.
*   *Anti-Fraud Logic*: The app uses GPS geofencing to ensure only students actually near the bus can report its status.

### 3. 🤖 AI Transit Intelligence
We’ve integrated a "System Agent" that acts as your personal transit consultant.
*   **Predictive Trends**: The AI analyzes reporting data to warn you if a route is getting congested earlier than usual.
*   **Smart Suggestions**: If the bus is full, the AI automatically prompts the "Alternative Transport" module.

### 4. 🛺 The "Safety Net" (Shared Auto Integration)
We know that sometimes things don't go to plan. When the bus is full, VidhyarthiBus becomes a ride-hailing assistant.
*   **Verified Contacts**: A curated list of local shared-auto drivers.
*   **One-Tap Search**: Instantly find the nearest auto stands on Google Maps if no direct contacts are available.

### 5. 🏆 Gamified Reputation System
Helpfulness is our currency. We’ve built a full-fledged rewards engine to keep the data fresh.
*   **Earn Points**: Get +15 points for every verified crowd report.
*   **Badges & Tiers**: Move from a "Commuter" to a "Monitor" and finally to a "Fleet Commander" (🛡️).
*   **Activity Heatmaps**: A GitHub-style contribution grid lets you track your "Helpfulness Streak."

---

## 🎨 The "Vidyarthi Pro" Design Language
We moved away from "boring utility" and toward "premium startup."
*   **Neo-Dark Aesthetics**: High-contrast Slate and Navy backgrounds (NeoDark) mixed with vibrant Indigo (NeoAccento) and Gold (NeoYellow).
*   **Bouncy UI**: Custom `BouncyCard` components and spring animations make every interaction feel tactile and responsive.
*   **Glassmorphism**: A floating, blurred navigation bar and translucent bottom sheets for a modern, airy feel.

---

## 🛠 The Tech Stack: Built for Scale
*   **UI Framework**: Jetpack Compose (Modern, Declarative, Fast).
*   **State Management**: Complex state lifting using `mutableStateOf`, `remember`, and `LaunchedEffect`.
*   **Realtime Backend**: Firebase Realtime Database with sub-100ms synchronization.
*   **Maps**: osmdroid for a robust, developer-friendly OpenStreetMap integration.
*   **Haptics**: Integrated tactile feedback for a professional "hardware-like" feel.

---

## 🗺️ Coverage: Growing with You
Currently operational in major hubs:
*   **Bengaluru**: MVJ, PESU, RVCE, MSRIT, BIT, BMS, REVA.
*   **Mysuru**: VVCE, SJCE, NIE.
*   **And beyond**: Hubballi, Davanagere, Mangaluru.

---

## 🤝 Join the Community
We are a project built **by students, for students**. 
> "Transit shouldn't be a source of stress. It should be the time you catch up with friends, listen to a podcast, or prepare for your day."

**VidyarthiBus: Track Easy. Manage Smart. Travel Together.**

---
*Developed with ❤️ by the Akash N Team.*
