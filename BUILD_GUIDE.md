# 📱 Hermes Mobile APK Build Guide

## 🛠️ Prerequisites (Any Computer)

### 1. Install Android Studio (Free)
- Download: https://developer.android.com/studio
- Install on Windows/macOS/Linux
- **Or** install just the Command-line tools:
  ```bash
  # macOS:
  brew install --cask android-studio
  
  # Or SDK-only:
  sdkmanager --install "cmdline-tools;latest"
  ```

### 2. Create AVD (Android Virtual Device)
- Open Android Studio → **More Actions** → **Virtual Device Manager**
- Click **Create Device**
- Select: **API 35 (Android 14)** → **ARM64** or **x86_64**
- Recommended: ARM64 (matches your app's `arm64-v8a` native libs)
- Download the system image when prompted

### 3. Enable Shizuku on Emulator/Device
```bash
# Method A: Via ADB (requires root or specific setup)
adb shell "setenforce 0"  # Sometimes needed

# Method B: Using Shizuku F-Droid
# 1. Install F-Droid app store on the emulator/device
# 2. Install Shizuku from F-Droid
# 3. Grant permission: adb shell "sh -c 'setenforce 0; su -c \"chmod 666 /dev/stune\"'"
# 4. Start Shizuku service from the Shizuku app

# Method C: User-space (no root required)
# 1. Install Shizuku app from F-Droid or Google Play
# 2. Enable "Developer options" → "USB debugging" 
# 3. Connect via USB or use same WiFi network
# 4. Tap "Start Service" in the Shizuku app
```

## 📦 Build the APK

### Option A: Using Android Studio (Recommended)
1. **Download project files** - I'll provide a ZIP below
2. Open Android Studio → **Open** → select `/path/to/apps/android/`
3. Wait for Gradle sync (it will download dependencies)
4. **Build → Build APK(s)** → `assembleDebug`
5. Find the APK at: `apps/android/build/outputs/apk/debug/app-debug.apk`

### Option B: Using Gradle CLI
```bash
# 1. Navigate to the project
cd /path/to/apps/android

# 2. Build with Gradle wrapper (if available)
#    (If no gradlew, use Option A)
./gradlew assembleDebug

# 3. Find the APK
ls -la build/outputs/apk/debug/
# → app-debug.apk
```

## 📱 Install & Run on Emulator/Device

```bash
# 1. Install the APK
adb install -r apps/android/build/outputs/apk/debug/app-debug.apk

# 2. Launch the app
adb shell am start -n com.example.hermesmobile/.ui.MainActivity

# 3. Or from Android Studio: press the "Run" ▶️ button
```

## 🧪 Test Flow

| Step | Action | Expected |
|------|--------|----------|
| 1 | **App launches** | Rootfs bootstrap starts (async download/verify/extract of Debian ARM64) |
| 2 | **Tap "Connect"** | Foreground service starts `python3 -m gateway.run` |
| 3 | **Tap "Terminal"** | Terminal mode activates (PTY↔gateway bridge) |
| 4 | **Tap "Shizuku"** | Permission dialog → configure Shizuku via https://shizuku.rikka.app |
| 5 | **Send chat messages** | Goes through gateway API → agent turn loop |
| 6 | **Type in terminal** | Commands fed to agent via `/v1/chat/completions` |
| 7 | **Agent tool output** | Appears in terminal PTY display |

## 🔧 Troubleshooting

### "Shizuku not available"
- Ensure Shizuku app is running on the device/emulator
- Check: `adb shell pidof rish` → should return a PID
- Verify: `adb shell content query --uri content://com.rikka.shizuku.provider`

### "Proot not found"
- The app looks for `libproot.so` in `jniLibs/arm64-v8a/`
- Without the binary, rootfs bootstrap will fail gracefully
- **Workaround**: The app falls back to direct terminal without container

### "Gateway connection failed"
- Ensure the foreground service has started
- Check Android Studio Logcat for errors
- Verify internet permission is granted

### AVD has no sound/inputs
- In AVD Manager → select your device → **Edit** → **Show Advanced Settings**
- Adjust hardware graphics, memory, and device fram

## 📁 What's Included in the Build

Your Android project has **13 files** (1404 lines of Kotlin):

```
apps/android/
├─ build.gradle.kts              # 82 lines: 14 exact-pinned Maven Central deps
├─ settings.gradle.kts           # 1 line
├─ AndroidManifest.xml           # 46 lines: Internet + ShizukuProvider + AgentService
├─ res/values/strings.xml        # 12 lines: Material strings
├─ res/values/themes.xml         # 27 lines: Material Dark theme
├─ java/
│   ├─ runtime/AgentService.kt           # 120 lines: FS + TCP bridge
│   ├─ ui/ChatRepository.kt              # 91 lines: OkHttp gateway client
│   ├─ ui/MainActivity.kt                # 330 lines: Full Compose UI + TerminalIOScheduler
│   ├─ ui/TerminalManager.kt             # 135 lines: terminal-view + proot session
│   ├─ ui/RootfsBootstrap.kt             # 212 lines: async bootstrap flow
│   ├─ ui/TerminalIOScheduler.kt         # 119 lines: PTY↔gateway bridge **(new)**
│   └─ shizuku/ShizukuManager.kt       # 397 lines: 6 device methods + runShellCommand
└─ jniLibs/arm64-v8a/README.txt  # 24 lines: proot binary guidance
```

## 📦 Download Source Package

I can generate a **ZIP** containing all 13 Android source files. Would you like me to:
- **A)** Create and offer a ZIP download of all source files
- **B)** Provide just the build guide (above)
- **C)** Focus on the Python/hermes-agent Shizuku tools instead

**Choose A, B, or C** and I'll proceed accordingly.