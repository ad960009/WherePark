# 🚗 WherePark (어디파킹)

> **안드로이드 16(API 37) 기반의 블루투스 비컨 주차 위치 자동 기록 시스템**

사용자가 차량의 블루투스 연결 해제를 감지하면, 주변에 등록된 블루투스 비컨 신호를 탐색하여 마지막 주차 위치를 자동으로 기록하고 위젯으로 보여주는 애플리케이션입니다.

---

## 🌟 주요 기능

*   **자동 주차 기록:** 차량 블루투스(페어링된 기기) 연결이 끊기면 자동으로 5분간 주변 비컨을 스캔합니다.
*   **최적 위치 선정:** 스캔된 비컨 중 신호 세기(RSSI)가 가장 강한 장소를 주차 위치로 확정합니다.
*   **Edge-to-Edge 디자인:** 안드로이드 15/16의 전체 화면 레이아웃 표준을 준수합니다.
*   **홈 화면 위젯:** 앱을 열지 않아도 홈 화면에서 마지막 주차 위치와 시간을 즉시 확인할 수 있습니다.
*   **안드로이드 16(API 37) 완벽 대응:**
    *   `Edge-to-Edge` 강제 적용 대응 (WindowInsets 처리).
    *   `Predictive Back` (예측형 뒤로 가기) 지원.
    *   신규 블루투스 인텐트(`KEY_MISSING`, `ENCRYPTION_CHANGE`) 처리로 연결 해제 감지 신뢰성 향상.
    *   프라이버시 강화를 위한 위치 권한 최소화 (`neverForLocation` 플래그 활용).

---

## 🛠 기술 스택

*   **언어:** Kotlin 2.3.20
*   **UI:** XML (ViewBinding, LinearLayout, CardView)
*   **안드로이드 SDK:** Compile/Target SDK 37 (Android 16)
*   **주요 기술:**
    *   `BluetoothLeScanner`를 이용한 백그라운드 BLE 스캔.
    *   `Foreground Service` 및 `BroadcastReceiver` (Non-exported) 기반의 상시 모니터링.
    *   `AppWidgetProvider`를 통한 실시간 정보 업데이트.
    *   `ViewCompat` 기반의 시스템 윈도우 인셋 핸들링.

---

## ⚙️ 개발 환경 및 설정

*   **IDE:** Android Studio Meerkat 이상 권장
*   **AGP 버전:** 9.1.1
*   **Kotlin 버전:** 2.3.20
*   **최소 지원 버전:** Android 15 (API 35)

### 필수 권한 (AndroidManifest.xml)
```xml
<!-- 블루투스 스캔 및 연결 -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- 포그라운드 서비스 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />

<!-- 알림 권한 (Android 13+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```
