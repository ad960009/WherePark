# 🚗 WherePark (어디파킹)

> **안드로이드 16(SDK 36) 기반의 블루투스 비컨 주차 위치 자동 기록 시스템**

사용자가 주차장에 설치된 블루투스 장치(비컨)를 등록해두면, 앱이 백그라운드에서 신호를 감지하여 마지막 주차 위치를 별칭과 함께 기록해주는 애플리케이션입니다.

---

## 🌟 주요 기능

* **실시간 BLE 스캔:** 주변의 저전력 블루투스(BLE) 장치를 실시간으로 검색합니다.
* **신호 세기(RSSI) 실시간 표시:** 장치와의 거리를 직관적으로 파악할 수 있도록 dBm 단위의 신호 세기를 리스트에 표시합니다.
* **장치 별칭 등록:** 복잡한 MAC 주소 대신 "지하 1층 A-03"과 같이 사용자가 알아보기 쉬운 이름으로 저장(SharedPreferences)합니다.
* **최신 보안 가이드 준수:** 안드로이드 16의 새로운 권한 정책 및 보안 위험 자동 차단(Auto Blocker) 대응 설정을 포함합니다.

---

## 🛠 기술 스택

* **언어:** Kotlin
* **UI:** XML (ViewBinding, ConstraintLayout)
* **안드로이드 버전:** Compile SDK 35 / 36 (Android 16 대응)
* **주요 기술:**
    * `BluetoothLeScanner` API를 이용한 장치 검색
    * `Foreground Service`를 통한 백그라운드 상시 감지 구조
    * `SharedPreferences` 기반의 경량 데이터 관리
    * `Runtime Permission` (Android 12+ 대응 권한 로직)

---

## ⚙️ 개발 환경 및 설정

* **IDE:** Android Studio Ladybug 이상 권장
* **AGP 버전:** 8.6.0 ~ 8.7.2
* **Kotlin 버전:** 1.9.24
* **최소 지원 버전:** Android 8.0 (API 26)

### 필수 권한 (AndroidManifest.xml)
```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />