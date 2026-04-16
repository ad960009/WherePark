package kr.ad960009.wherepark

object Constants {
    // SharedPreferences 이름
    const val PREFS_NAME = "WhereParkPrefs"

    // SharedPreferences 키값
    const val KEY_MY_CAR_NAME = "MY_CAR_NAME"
    const val KEY_MY_CAR_ADDRESS = "MY_CAR_ADDRESS"
    const val KEY_LAST_PARKING_LOCATION = "LAST_PARKING_LOCATION"
    const val KEY_LAST_PARKING_TIME = "LAST_PARKING_TIME"

    // 알림 관련
    const val CHANNEL_ID = "PARKING_CHANNEL"
    const val CHANNEL_NAME = "주차 서비스 기록"
    const val NOTIFICATION_ID = 1

    // 스캔 관련
    const val SCAN_DURATION_MS = 10000L // 메인 화면 10초
    const val PARKING_SCAN_DURATION_MS = 300000L // 서비스 5분 (5 * 60 * 1000)
    const val MIN_RSSI_THRESHOLD = -85
}