package com.tidewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.RemoteViews
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class TideWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.tide_widget)
            
            // Set up click intent to refresh
            val intent = Intent(context, TideWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.tide_wave_view, pendingIntent)
            
            // Update widget with tide data
            updateTideWidget(context, views, appWidgetManager, appWidgetId)
        }
        
        private fun updateTideWidget(
            context: Context, 
            views: RemoteViews, 
            appWidgetManager: AppWidgetManager, 
            appWidgetId: Int
        ) {
            // Show loading state
            views.setViewVisibility(R.id.loading_overlay, android.view.View.VISIBLE)
            appWidgetManager.updateAppWidget(appWidgetId, views)
            
            // Fetch tide data asynchronously
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val tideService = TideApiService()
                    val tideData = tideService.getTideDataForToday()
                    
                    withContext(Dispatchers.Main) {
                        updateWidgetWithData(context, views, tideData, appWidgetManager, appWidgetId)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        updateWidgetWithError(context, views, appWidgetManager, appWidgetId)
                    }
                }
            }
        }
        
        private fun updateWidgetWithData(
            context: Context,
            views: RemoteViews,
            tideData: List<TidePoint>,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            // Hide loading overlay
            views.setViewVisibility(R.id.loading_overlay, android.view.View.GONE)
            
            // Update timestamp
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val currentTimeString = timeFormat.format(Date())
            views.setTextViewText(R.id.update_time, "Updated: $currentTimeString")
            
            // Calculate current tide info
            val currentTime = System.currentTimeMillis()
            val currentTide = findCurrentTideHeight(tideData, currentTime)
            val nextTide = findNextTide(tideData, currentTime)
            val trend = calculateTideTrend(tideData, currentTime)
            
            // Update current height
            views.setTextViewText(R.id.current_height, "${currentTide.format(1)} ft")
            
            // Update next tide
            if (nextTide != null) {
                val nextTideTime = timeFormat.format(Date(nextTide.timeMillis))
                val nextTideType = if (nextTide.isHighTide) "Next High" else "Next Low"
                views.setTextViewText(R.id.next_tide_label, nextTideType)
                views.setTextViewText(R.id.next_tide_time, nextTideTime)
            }
            
            // Update trend
            views.setTextViewText(R.id.tide_trend, trend)
            val trendColor = when (trend) {
                "Rising" -> android.graphics.Color.parseColor("#4CAF50")
                "Falling" -> android.graphics.Color.parseColor("#FF5722") 
                else -> android.graphics.Color.parseColor("#FF9500")
            }
            views.setTextColor(R.id.tide_trend, trendColor)
            
            // Create bitmap for tide chart
            val chartBitmap = createTideChartBitmap(context, tideData)
            if (chartBitmap != null) {
                views.setImageViewBitmap(R.id.tide_wave_view, chartBitmap)
            }
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        
        private fun updateWidgetWithError(
            context: Context,
            views: RemoteViews,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            views.setViewVisibility(R.id.loading_overlay, android.view.View.GONE)
            views.setTextViewText(R.id.current_height, "Error")
            views.setTextViewText(R.id.next_tide_time, "Retry")
            views.setTextViewText(R.id.tide_trend, "N/A")
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        
        private fun findCurrentTideHeight(tideData: List<TidePoint>, currentTime: Long): Float {
            if (tideData.isEmpty()) return 0f
            
            // Find the closest tide point to current time
            return tideData.minByOrNull { kotlin.math.abs(it.timeMillis - currentTime) }?.height ?: 0f
        }
        
        private fun findNextTide(tideData: List<TidePoint>, currentTime: Long): TidePoint? {
            return tideData.firstOrNull { 
                (it.isHighTide || it.isLowTide) && it.timeMillis > currentTime 
            }
        }
        
        private fun calculateTideTrend(tideData: List<TidePoint>, currentTime: Long): String {
            if (tideData.size < 2) return "Unknown"
            
            val currentIndex = tideData.indexOfFirst { it.timeMillis >= currentTime }
            if (currentIndex <= 0 || currentIndex >= tideData.size - 1) return "Unknown"
            
            val previous = tideData[currentIndex - 1]
            val next = tideData[currentIndex + 1]
            
            return when {
                next.height > previous.height -> "Rising"
                next.height < previous.height -> "Falling"
                else -> "Stable"
            }
        }
        
        private fun createTideChartBitmap(context: Context, tideData: List<TidePoint>): Bitmap? {
            return try {
                val width = 800
                val height = 400
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                
                val tideView = TideWaveView(context)
                tideView.setTideData(tideData)
                tideView.layout(0, 0, width, height)
                tideView.draw(canvas)
                
                bitmap
            } catch (e: Exception) {
                null
            }
        }
        
        private fun Float.format(digits: Int): String = "%.${digits}f".format(this)
    }
}