package kr.ad960009.wherepark

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class ParkingWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val location = prefs.getString(Constants.KEY_LAST_PARKING_LOCATION, Constants.MSG_READY)

            val views = RemoteViews(context.packageName, R.layout.widget_parking)
            views.setTextViewText(R.id.widgetLocation, location)

            // 클릭 시 앱 실행
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.mainLayout, pendingIntent) // widget_parking의 최상위 ID가 mainLayout일 때

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}