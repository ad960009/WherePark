package kr.ad960009.wherepark

object Constants {
    const val PREFS_NAME = "WhereParkPrefs"

    const val KEY_MY_CAR_NAME = "MY_CAR_NAME"
    const val KEY_MY_CAR_ADDRESS = "MY_CAR_ADDRESS"
    const val KEY_LAST_PARKING_LOCATION = "LAST_PARKING_LOCATION"
    const val KEY_LAST_PARKING_TIME = "LAST_PARKING_TIME"
    const val KEY_LAST_LATITUDE = "LAST_LATITUDE"
    const val KEY_LAST_LONGITUDE = "LAST_LONGITUDE"
    const val KEY_LAST_ACCURACY = "LAST_ACCURACY"

    const val CHANNEL_ID = "PARKING_CHANNEL"
    const val CHANNEL_NAME = "주차 서비스 기록"
    const val NOTIFICATION_ID = 1

    const val SCAN_DURATION_MS = 10000L
    const val PARKING_SCAN_DURATION_MS = 300000L
    const val MIN_RSSI_THRESHOLD = -85
    const val MAX_LOCATION_ACCURACY_METERS = 500f

    const val MSG_SCANNING = "탐색 중 🎯"
    const val MSG_NOT_FOUND = "알 수 없음"
    const val MSG_DRIVING = "주행 중 🚗"
    const val MSG_OUTDOOR = "야외"
}
