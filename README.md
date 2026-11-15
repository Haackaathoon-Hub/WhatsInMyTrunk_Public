# WHAT'S IN MY TRUNK? — Pleos Vehicle Context Intelligence

> **현대자동차그룹 차량용 앱 개발 공모전 (`Code your App into Pleos App Market`)** · **Team Players**

---

## 1. 앱 이름 및 한 줄 소개

* **앱 이름:** **WHAT'S IN MY TRUNK?** *(Pleos Vehicle Context Intelligence)*
* **한 줄 소개:** 사용자의 번거로운 수동 입력 없이 차량 센서(트렁크·도어·기어·시트·창문·공조)와 내비게이션 이동 맥락(목적지·경로·POI)을 결합해, **하차 직전 트렁크·뒷좌석 소지품 방치와 신선식품 변질을 선제적으로 예방하고 원터치 차량 제어까지 수행하는 차량 네이티브 컨텍스트 인텔리전스 앱**입니다.

### 주요 링크
| 항목 | 링크 / 위치 | 비고 |
|---|---|---|
| **GitHub Repository** | https://github.com/Haackaathoon-Hub/WhatsInMyTrunk_Public | 공개 저장소 (`main` · `gh-pages`) |
| **Release APK (GitHub Releases)** | https://github.com/Haackaathoon-Hub/WhatsInMyTrunk_Public/releases/tag/v1.0.0 | `Players_WhatsInMyTrunk_release.apk` (`v1.0.0`) |
| **웹 인터랙티브 데모 (GitHub Pages)** | https://haackaathoon-hub.github.io/WhatsInMyTrunk_Public/ | `gh-pages` 브랜치 기반 소개 페이지 |
| **시연 영상 (Demo Video)** | *(시연 영상 URL 입력 예정)* | 에뮬레이터 실구동 시연 영상 |
| **숏폼 영상 (YouTube Shorts)** | https://youtube.com/shorts/AY4PW7mkSdg | `#HMG차량용앱개발공모전` |

---

## 2. 앱 아이콘 및 시연 미디어

| 공식 앱 아이콘 (M-3) | Pleos Connect 에뮬레이터 실행 화면 |
|:---:|:---:|
| ![공식 앱 로고 M-3](homepage/assets/app_icon_final_m3.png) | ![Pleos Connect 실행 화면](homepage/assets/pleos_connect_running.png) |

### 주요 앱 화면 스크린샷 (`Pleos Connect Emulator v2.1.0` 실구동 · 주간/야간 테마)

| 화면 | 주간 테마 (Light) | 야간 테마 (Dark) |
|:---:|:---:|:---:|
| **대시보드 (`TRUNK CARGO STAGE`)** | ![대시보드 주간](homepage/assets/Screenshot_20261115_194327.png) | ![대시보드 야간 클러스터](homepage/assets/Screenshot_20261115_194759.png) |
| **트렁크 보관함 (인벤토리)** | ![트렁크 보관함 주간](homepage/assets/Screenshot_20261115_194355.png) | ![트렁크 보관함 야간](homepage/assets/Screenshot_20261115_194813.png) |
| **신선 케어 (골든타임 & 급속 냉방)** | ![신선 케어 주간](homepage/assets/Screenshot_20261115_194414.png) | ![신선 케어 야간](homepage/assets/Screenshot_20261115_194818.png) |
| **여정 & 인과관계 로그** | ![여정 기록 주간](homepage/assets/Screenshot_20261115_194426.png) | ![여정 기록 야간](homepage/assets/Screenshot_20261115_194824.png) |
| **트렁크 화물 직접 등록 다이얼로그** | ![화물 직접 등록 주간](homepage/assets/Screenshot_20261115_194506.png) | ![화물 직접 등록 야간](homepage/assets/Screenshot_20261115_194904.png) |
| **차량 연동 설정 패널** | ![차량 연동 설정 주간](homepage/assets/Screenshot_20261115_194434.png) | ![차량 연동 설정 야간](homepage/assets/Screenshot_20261115_194829.png) |
| **사용자 계정 프로필 패널** | ![사용자 계정 프로필 주간](homepage/assets/Screenshot_20261115_194452.png) | ![사용자 계정 프로필 야간](homepage/assets/Screenshot_20261115_194834.png) |
| **앱 버전 정보 모달 (`Team Players`)** | ![앱 정보 모달 주간](homepage/assets/Screenshot_20261115_194345.png) | ![앱 정보 모달 야간](homepage/assets/Screenshot_20261115_194806.png) |

### 상황별 통합 팝업 오버레이 스크린샷 (`Pleos Connect Emulator` 실구동)

| 목적지 도착 · P단 주차 하차 전 알림 팝업 | 트렁크 적재 감지 · 닫힘 완료 품목 등록 팝업 |
|:---:|:---:|
| ![목적지 도착 P단 하차 알림 팝업](homepage/assets/Screenshot_20261115_200336.png) | ![트렁크 적재 감지 닫힘 팝업](homepage/assets/Screenshot_20261115_200402.png) |

### 시연 GIF · 동영상 *(추가 촬영 후 주석 해제하여 반영)*
> GIF 파일은 `homepage/assets/` 경로에 배치하고, 동영상(`.mp4`)은 GitHub 이슈/코멘트 업로드 URL을 붙여넣습니다.
>
> * **핵심 플로우 GIF (예정):** `<!-- ![하차 전 알림 E2E 시연](homepage/assets/demo_e2e_flow.gif) -->`
> * **시연 영상 임베드 (예정):** *(새 GitHub 저장소 이슈/코멘트에 `.mp4` 업로드 후 생성되는 URL 붙여넣기)*

---

## 3. APK 파일 위치 및 GitHub Release 안내

* **제출용 Release APK 파일명:** `apk/Players_WhatsInMyTrunk_release.apk` (약 149 MB)
* **배포 방식 (GitHub Releases):**
  * 오프라인 한국어 음성 인식 모델(`vosk-ko-0.22.zip`, 약 80.8 MB) 및 ABI별 네이티브 라이브러리 포함으로 인해 단일 APK 용량이 GitHub 일반 Git 업로드 제한(100 MB)을 초과합니다.
  * 공모전 공식 가이드(*용량 초과 시 GitHub Releases 등록*)에 따라 **GitHub Releases**에 `Players_WhatsInMyTrunk_release.apk`를 업로드하고, 상단 링크 표에 Release 주소를 연결합니다.
* **로컬 APK 재빌드 명령:**
  ```cmd
  .\build_submission_apk.bat
  ```
  *(단위 테스트 통과 후 `apk\Players_WhatsInMyTrunk_release.apk` 자동 생성)*

---

## 4. 개발에 사용한 Pleos Connect 에뮬레이터 버전 및 화면 규격

| 항목 | 사양 |
|---|---|
| **에뮬레이터 버전** | **Pleos Connect Emulator v2.1.0** |
| **시스템 이미지 / OS** | **Android 14 (API Level 34) · Android Automotive OS (`x86_64`)** |
| **화면 해상도 / 비율** | **2560 × 1440 px** (16:9 가로형 Landscape 차량 디스플레이) |
| **화면 밀도 / 주사율** | **160 dpi (`mdpi`)** · **60 Hz** (`VSync`) |
| **앱 Android SDK** | `minSdk = 28` · `targetSdk = 34` · `compileSdk = 34` |

---

## 5. 테스트 환경 및 필요한 설정

### 5.1 개발 · 빌드 환경
* **JDK / Gradle / AGP:** Microsoft OpenJDK 17 · Gradle `8.7` · Android Gradle Plugin `8.5.2` · Kotlin `1.9.24` · Jetpack Compose BOM `2024.06.00`
* **공식 Pleos Connect SDK 및 의존성 (`app/build.gradle.kts`):**
  * `ai.pleos.playground:Vehicle:2.3.2` (트렁크/도어·창문·공조 센서 조회 및 물리 액추에이터 제어)
  * `ai.pleos.playground:NaviHelper:2.0.3` (목적지 정보 및 도착 이벤트 연동)
  * `ai.pleos.playground:SpeechToText:3.3.1` (차량 음성 입력 연동)
  * `com.alphacephei:vosk-android:0.3.75` (오프라인 한국어 적재 음성 메모 보조 인식)

### 5.2 빌드 및 실행 시 필요한 사전 설정
1. **네트워크 요구사항 (최초 빌드 시):**
   * `https://nexus-playground.pleos.ai/repository/maven-releases/` Pleos Maven 저장소 접근 필요
   * Gradle `prepareCargoSpeechModel` 태스크가 최초 빌드 시 한국어 오프라인 음성 모델(`vosk-ko-0.22.zip`, SHA-256 검증)을 자동 내려받습니다.
2. **에뮬레이터 / 앱 권장 권한 설정:**
   * **다른 앱 위에 표시 (`SYSTEM_ALERT_WINDOW`):** 주행 후 목적지 도착 + P단 주차 시 내비/홈 화면 위에 반투명 하차 알림 모달(`PreExitPromptActivity`)을 띄우기 위해 허용 권장
   * **알림 (`POST_NOTIFICATIONS`) · 마이크 (`RECORD_AUDIO`) · 위치 (`ACCESS_FINE_LOCATION`):** 헤드업 알림(HUN), 음성 적재 메모, GPS/내비 맥락 연동용
3. **단위 및 E2E 시나리오 테스트 검증 (총 179건):**
   ```powershell
   .\gradlew.bat testDebugUnitTest
   ```
   * 코어 컨텍스트 엔진, 목적지 불일치 필터링, 다중 적재 인벤토리, 운전자 모드 5종 위젯, 개발자 콘솔 12종 패널, 교차 기능 및 실제 주행 여정 E2E 시나리오 전체 포함 (`179 tests, 0 failures`)

---

## 6. 실행 방법

### 방법 A. Pleos Connect 에뮬레이터 자동 실행 및 설치 (권장)
프로젝트 루트에서 아래 스크립트를 실행하면 모니터 해상도에 맞춰 에뮬레이터를 띄우고 앱 설치·실행 및 **Pleos Virtual Console(`tcp:8088`)** 포트 포워딩을 자동으로 구성합니다.
```cmd
.\run_pleos_emulator.bat
```

### 방법 B. Release APK 직접 설치
이미 실행 중인 `Pleos Connect Emulator v2.1.0`에 `Players_WhatsInMyTrunk_release.apk`를 바로 설치할 수 있습니다.
```cmd
adb install -r apk\Players_WhatsInMyTrunk_release.apk
adb shell am start -n com.pleos.vehiclecontext/.MainActivity
```

### 방법 C. 시연 방법 (Pleos Virtual Console & 인앱 콘솔)
1. **Pleos Virtual Console 연동 시연 (`http://127.0.0.1:8088/`):**
   1. **Convenience 탭:** `Tailgate(트렁크)`를 **Open → Close**로 전환 (출발지 적재 감지)
   2. **Motion 탭:** `Gear`를 **D (Drive)**로 변경 후 에뮬레이터 홈 화면으로 전환 (백그라운드 감시)
   3. **Motion 탭:** `Gear`를 **P (Park)**로 변경 (목적지 주차 완료 감지 → AAOS 헤드업 알림, TTS 안내, 하차 확인 모달 즉시 표시)
2. **인앱 실증 시뮬레이션 (별도 장치 설정 없이 1클릭 확인):**
   * 상단 **`개발자 콘솔`** 탭 → **`[▶ 실증 주행 시뮬레이션 재생]`** 클릭 시 적재 → 음성 메모 → 주행 → 도착 → P단 하차 브리핑 전 과정을 자동 재생하며, 차량 탑뷰 디지털 트윈과 고온 급속 냉방 제어(`28.5℃ → 20.0℃`)를 즉시 확인할 수 있습니다.

---

## 7. 프로젝트 구조 및 4-Layer 아키텍처

단일 기능(트렁크 알림)에만 고정된 구조가 아니라, **차량 내부의 모든 센서와 제어 자원을 플러그인(`ScenarioPlugin`)으로 조합할 수 있는 범용 컨텍스트 프레임워크**로 설계되었습니다. 상세 구조는 [`PROJECT.md`](PROJECT.md)에서 확인할 수 있습니다.

* **Layer 1. Signal Collector (`core/signal`):** [`SignalProvider`](app/src/main/java/com/pleos/vehiclecontext/core/signal/SignalProvider.kt), [`PleosSignalProvider`](app/src/main/java/com/pleos/vehiclecontext/core/signal/PleosSignalProvider.kt) (`Vehicle:2.3.2`, `NaviHelper:2.0.3`, AAOS `CarPropertyManager`), [`MockSignalProvider`](app/src/main/java/com/pleos/vehiclecontext/core/signal/MockSignalProvider.kt)
* **Layer 2. Trip & Context Engine (`core/engine` · `core/model`):** [`TripSession`](app/src/main/java/com/pleos/vehiclecontext/core/model/TripSession.kt), [`TripContextEngine`](app/src/main/java/com/pleos/vehiclecontext/core/engine/TripContextEngine.kt), [`SessionPersistence`](app/src/main/java/com/pleos/vehiclecontext/core/engine/SessionPersistence.kt)
* **Layer 3. Scenario Plugins (`scenarios/` 8종):**
  1. [`TrunkReminderScenario`](app/src/main/java/com/pleos/vehiclecontext/scenarios/trunk/TrunkReminderScenario.kt) — 트렁크 개폐 이력 + 도착·P단 하차 전 미수거 안내
  2. [`DestinationReverseReminderScenario`](app/src/main/java/com/pleos/vehiclecontext/scenarios/trunk/DestinationReverseReminderScenario.kt) — 마트·레저 도착 시 상시 보관 물품(장바구니 등) 역방향 챙김 안내
  3. [`CargoTemperatureGuardScenario`](app/src/main/java/com/pleos/vehiclecontext/scenarios/cabin/CargoTemperatureGuardScenario.kt) — 고온 신선식품 변질 경고 및 실내 급속 냉방(`ACTIVATE_CABIN_COOLING`) 제어
  4. [`RearSeatItemScenario`](app/src/main/java/com/pleos/vehiclecontext/scenarios/cabin/RearSeatItemScenario.kt) — 2열 도어 개폐 + 시트 미착석(짐 적재 추론) 하차 안내
  5. [`CargoSafeDrivingScenario`](app/src/main/java/com/pleos/vehiclecontext/scenarios/driving/CargoSafeDrivingScenario.kt) — 적재 상태 주행 중 급가감속 감지 TTS 안전 코칭
  6. [`ExitIntegrityScenario`](app/src/main/java/com/pleos/vehiclecontext/scenarios/exit/ExitIntegrityScenario.kt) — 주차 시 열린 창문 감지 및 원터치 전체 창문 닫기
  7. [`AutonomousCargoComfortScenario`](app/src/main/java/com/pleos/vehiclecontext/scenarios/autonomous/AutonomousCargoComfortScenario.kt) — Lv.3+ 자율주행 시 적재물 보호 완만 주행 모드 제안
  8. [`AutonomousValetDropoffScenario`](app/src/main/java/com/pleos/vehiclecontext/scenarios/autonomous/AutonomousValetDropoffScenario.kt) — 자율 발렛 파킹(AVP) 하차 존 진입 시 트렁크 경고 및 출발 홀드
* **Layer 4. Action & HMI Presentation (`core/actuator` · `ui/`):** [`VehicleController`](app/src/main/java/com/pleos/vehiclecontext/core/actuator/VehicleController.kt), [`ActionDispatcher`](app/src/main/java/com/pleos/vehiclecontext/core/actuator/ActionDispatcher.kt), [`GeneralUserScreen`](app/src/main/java/com/pleos/vehiclecontext/ui/user/GeneralUserScreen.kt), [`DeveloperConsoleScreen`](app/src/main/java/com/pleos/vehiclecontext/ui/dev/DeveloperConsoleScreen.kt), [`PreExitPromptActivity`](app/src/main/java/com/pleos/vehiclecontext/ui/overlay/PreExitPromptActivity.kt)
