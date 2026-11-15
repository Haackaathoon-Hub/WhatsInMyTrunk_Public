# Project: HMG Pleos AAOS App 'What's in My Trunk' — Dual-Mode & General User Screen

## Architecture
- **Presentation Layer**:
  - `MainActivity.kt`: Hosts `MainScreen` container with shared `MainViewModel`.
  - `MainScreen.kt`: Root container rendering `AppNavigationBar` and dynamically crossfading between `GeneralUserScreen` and `DeveloperConsoleScreen`.
  - `AppNavigationBar.kt`: Top navigation bar with "🚗 운전자 모드 (General User Mode)" [Default] and "🛠️ 개발자 콘솔 (Developer Console)", plus vehicle status pills (VHAL Active, Gear, Autonomous mode).
  - `GeneralUserScreen.kt`: Automotive distraction-compliant driver view hosting 5 widgets in a 2-column landscape grid + bottom timeline shelf:
    - Widget 1 (`CargoLiveOverviewWidget.kt`): Tailgate status & cargo item inventory.
    - Widget 2 (`FreshnessCareWidget.kt`): Outdoor/cabin temperature & perishable cargo golden time visualizer.
    - Widget 3 (`PreExitAlertCardWidget.kt`): Destination & ETA, pre-exit smart alert toggle, arrival card preview.
    - Widget 4 (`QuickCargoActionsWidget.kt`): Mic STT assistant, 1-tap presets (Mart, Cake, Stroller, Golf bag), clear trunk action.
    - Widget 5 (`TripTimelineWidget.kt`): Vehicle trip and trunk access event timeline.
  - `DeveloperConsoleScreen.kt`: Houses all 12 testbed components (3D canvas, manual VHAL injection, 6 core demo scenarios, AI/AVP scenarios, HUD/modal simulation trigger, event log).
- **State & Domain Layer**:
  - `VehicleContextRuntime`: Process-wide singleton managing engine, VHAL listeners, and mock provider.
  - `TripContextEngine`: Processes signals, executes scenario plugins, evaluates arrival alerts, manages `session`.
  - `MainViewModel`: Shared bridge exposing `StateFlow<TripSession>`, presets, and helper actions.
- **Automotive Safety Standards**:
  - Single glance duration <= 2.0s, total glance <= 12.0s.
  - Touch targets >= 76x76 dp (primary) and >= 48x48 dp (secondary).
  - AAOS dark theme palette: `0xFF0E131F` background, `0xFF182032` cards, `0xFF38BDF8` accent blue, `0xFF10B981` green, `0xFFF59E0B` amber, `0xFFEF4444` red.
  - Motion gating: suppression of distracting modal cards while moving in manual drive.

## Code Layout
```
app/src/main/java/com/pleos/vehiclecontext/
├── MainActivity.kt
├── ui/
│   ├── MainScreen.kt
│   ├── MainViewModel.kt
│   ├── navigation/
│   │   └── AppNavigationBar.kt
│   ├── dev/
│   │   └── DeveloperConsoleScreen.kt
│   ├── user/
│   │   ├── GeneralUserScreen.kt
│   │   ├── CargoLiveOverviewWidget.kt
│   │   ├── FreshnessCareWidget.kt
│   │   ├── PreExitAlertCardWidget.kt
│   │   ├── QuickCargoActionsWidget.kt
│   │   └── TripTimelineWidget.kt
│   └── components/
│       └── VehicleDigitalTwinCanvas.kt
app/src/test/java/com/pleos/vehiclecontext/
├── TripContextEngineTest.kt
└── GeneralUserViewModelTest.kt
```

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Dual-Mode Top Navigation Bar | Top bar switching between General User Mode and Developer Console | M2 | ORIGINAL_REQUEST R1 |
| 2 | Singleton State Continuity | Shared VehicleContextEngine and VHAL runtime state across views | M1, M2 | ORIGINAL_REQUEST R1 |
| 3 | Widget 1: Tailgate Status Indicator | Real-time open/close indicator with badge | M3 | ORIGINAL_REQUEST R2 |
| 4 | Widget 1: Cargo Live Overview List | List of loaded items with icons, category tags, load times | M3 | ORIGINAL_REQUEST R2 |
| 5 | Widget 2: Ambient Temp Sync | Display outdoor/cabin temp sensor data | M3 | ORIGINAL_REQUEST R2 |
| 6 | Widget 2: Freshness Golden Time | Countdown timer and progress bar for perishable goods | M1, M3 | ORIGINAL_REQUEST R2 |
| 7 | Widget 3: Destination & ETA | Destination name and ETA display | M3 | ORIGINAL_REQUEST R2 |
| 8 | Widget 3: Pre-Exit Smart Alert Toggle | On/Off toggle for arrival alert prompt and cluster/voice | M1, M3 | ORIGINAL_REQUEST R2 |
| 9 | Widget 3: Cabin Integrity Sub-Status | Quick indicator for window closure status | M3 | ORIGINAL_REQUEST R2 |
| 10 | Widget 4: Voice Cargo Assistant Button | Single-tap mic button for hands-free cargo tagging | M3 | ORIGINAL_REQUEST R2 |
| 11 | Widget 4: One-Tap Cargo Presets | 1-tap presets: Mart, Cake, Stroller, Golf bag | M1, M3 | ORIGINAL_REQUEST R2 |
| 12 | Widget 4: Clear Trunk Action | 1-tap action to clear loaded cargo | M1, M3 | ORIGINAL_REQUEST R2 |
| 13 | Widget 5: Today's Trip & Cargo Timeline | Visual timeline of trip and trunk access events | M3 | ORIGINAL_REQUEST R2 |
| 14 | Dev Console: 3D Vehicle Canvas | Top-view interactive digital twin canvas | M2 | ORIGINAL_REQUEST R3 |
| 15 | Dev Console: Empirical Journey Replayer | 5-stage automated journey simulation runner | M2 | ORIGINAL_REQUEST R3 |
| 16 | Dev Console: Manual VHAL Injection Panel | Manual controls for trunk, doors, gear, speed, weather | M2 | ORIGINAL_REQUEST R3 |
| 17 | Dev Console: Core 6 Demo Scenarios Panel | One-touch test buttons for 6 core demo scenarios | M2 | ORIGINAL_REQUEST R3 |
| 18 | Dev Console: Autonomous & AI Agent Panel | AI voice, Lv.3 Comfort, AVP Valet drop-off buttons | M2 | ORIGINAL_REQUEST R3 |
| 19 | Dev Console: Event Log Viewer | Real-time scrolling VHAL and ActionDispatcher event log | M2 | ORIGINAL_REQUEST R3 |
| 20 | Dev Console: Background HUD/Modal Trigger | AAOS background HUD and modal simulation trigger | M2 | ORIGINAL_REQUEST R3 |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Shared Models & ViewModel Extensions | Data models (CargoItemPresentation, FreshnessCareStatus, FreshnessRiskLevel), MainViewModel preset methods, arrival alert toggle, freshness calculation helper | none | PLANNED |
| M2 | Navigation Architecture & Developer Console Relocation | AppNavigationBar.kt, DeveloperConsoleScreen.kt with 12 relocated panels, refactoring MainScreen.kt to dual-mode host | none | PLANNED |
| M3 | General User Screen & 5 Automotive Widgets | GeneralUserScreen.kt and 5 modular widgets (CargoLiveOverview, FreshnessCare, PreExitAlertCard, QuickCargoActions, TripTimeline) conforming to distraction standards | M1, M2 | PLANNED |
| M4 | Final Integration, Test Pass & Verification | 100% E2E test pass, unit tests passing, emulator & virtual console (port 8088) check, git co-author audit | M3, TEST_READY | PLANNED |

## Interface Contracts
### MainViewModel ↔ GeneralUserScreen Widgets
```kotlin
// Cargo Presentation Model
data class CargoItemPresentation(
    val id: String,
    val name: String,
    val categoryBadge: String,
    val icon: String,
    val locationNote: String,
    val loadTimeFormatted: String,
    val isPerishable: Boolean
)

// Freshness Status Model
data class FreshnessCareStatus(
    val hasPerishableCargo: Boolean,
    val itemName: String,
    val currentTempCelsius: Float,
    val elapsedMinutes: Int,
    val limitMinutes: Int,
    val remainingMinutes: Int,
    val progress: Float,
    val riskLevel: FreshnessRiskLevel
)
enum class FreshnessRiskLevel { SAFE, WARNING, CRITICAL }

// Methods exposed by MainViewModel
fun applyCargoPresetMartGroceries()
fun applyCargoPresetCake()
fun applyCargoPresetStroller()
fun applyCargoPresetGolfBag()
fun clearTrunkCargo()
fun setArrivalAlertEnabled(enabled: Boolean)
val isArrivalAlertEnabled: StateFlow<Boolean>
val trunkCargoItems: StateFlow<List<CargoItemPresentation>>
val freshnessStatus: StateFlow<FreshnessCareStatus>
```

### AppNavigationBar ↔ MainScreen
```kotlin
enum class NavigationTab(val label: String, val icon: String) {
    GENERAL_USER("운전자 모드", "🚗"),
    DEVELOPER_CONSOLE("개발자 콘솔", "🛠️")
}
```
