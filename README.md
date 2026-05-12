# 🚗 WherePark (어디파킹)

> **블루투스 비컨과 GNSS(위성항법)를 결합한 하이브리드 주차 위치 자동 기록 시스템**

사용자가 차량의 블루투스 연결 해제를 감지하면, 주변의 블루투스 비컨 신호 탐색과 GPS 위치 추적을 병행하여 최적의 주차 위치를 자동으로 기록하고 위젯으로 보여주는 애플리케이션입니다.

---

## 🌟 주요 기능

*   **하이브리드 주차 탐색:** 
    *   **실내 주차:** 등록된 블루투스 비컨(BLE) 신호를 탐색하여 층수나 구역명을 기록합니다.
    *   **야외 주차:** 비컨을 찾지 못할 경우, GNSS(GPS)를 통해 실시간 위치를 추적하여 기록합니다.
*   **정밀도 우선 기록:** 5분간의 탐색 기간 중 오차 범위(Accuracy)가 가장 작았던 최적의 좌표를 자동으로 선정합니다 (최대 오차 500m 제한).
*   **외부 지도 연동:** 야외 주차로 기록된 경우, 위젯이나 앱에서 버튼 하나로 구글 지도, 네이버 지도 등 외부 맵 서비스를 실행하여 위치를 확인합니다.
*   **자동 서비스 관리:** 차량 연결 해제 시에만 포그라운드 서비스를 가동하며, 기록 완료 후 자동으로 서비스를 종료하여 배터리 소모를 최소화합니다.
*   **홈 화면 위젯:** 앱을 열지 않아도 주차 위치("B2", "야외" 등)와 기록 시간을 실시간으로 확인하고 지도 앱을 즉시 실행할 수 있습니다.
*   **최신 안드로이드 대응:**
    *   Android 14/15/16의 포그라운드 서비스 유형(`connectedDevice`, `location`) 준수.
    *   백그라운드 위치 권한 및 알림 권한 대응.
    *   안드로이드 16의 신규 블루투스 보안 인텐트 대응.

---

## 🛠 기술 스택

*   **언어:** Kotlin
*   **UI:** XML (ViewBinding, LinearLayout, CardView)
*   **핵심 API:**
    *   `BluetoothLeScanner`: 백그라운드 BLE 비컨 스캔.
    *   `FusedLocationProviderClient`: 고정밀 하이브리드 위치 추적.
    *   `Foreground Service`: Android 14+ 정책을 준수하는 백그라운드 작업 수행.
    *   `AppWidgetProvider`: 실시간 상태 표시 및 딥링크 연동.

---

## ⚙️ 개발 환경 및 설정

*   **IDE:** Android Studio
*   **SDK 버전:** Compile SDK 37 / Target SDK 35
*   **최소 지원 버전:** Android 15 (API 35)

### 필수 권한 (AndroidManifest.xml)
```xml
<!-- 블루투스 -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- 위치 정보 -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

<!-- 포그라운드 서비스 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />

<!-- 기타 -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```
