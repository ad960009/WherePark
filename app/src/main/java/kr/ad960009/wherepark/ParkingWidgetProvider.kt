package kr.ad960009.wherepark

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

class ParkingWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_CLICK = "kr.ad960009.wherepark.ACTION_WIDGET_CLICK"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val location = prefs.getString(Constants.KEY_LAST_PARKING_LOCATION, Constants.MSG_READY)

            val views = RemoteViews(context.packageName, R.layout.widget_parking)
            views.setTextViewText(R.id.widgetLocation, location)

            val intent = Intent(context, ParkingWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_CLICK
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.mainLayout, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_CLICK) {
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val lat = prefs.getFloat(Constants.KEY_LAST_LATITUDE, 0f)
            val lng = prefs.getFloat(Constants.KEY_LAST_LONGITUDE, 0f)
            val locationName = prefs.getString(Constants.KEY_LAST_PARKING_LOCATION, "")

            if (locationName == Constants.MSG_OUTDOOR && lat != 0f && lng != 0f) {
                val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(내 차 위치)")
                val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(mapIntent)
                } catch (e: Exception) {
                    launchMainActivity(context)
                }
            } else {
                launchMainActivity(context)
            }
        }
    }

    private fun launchMainActivity(context: Context) {
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(mainIntent)
    }
}
