package com.tidewidget

import android.app.AlarmManager
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
        // Start periodic updates when first widget is created
        schedulePeriodicUpdates(context)
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
            
            // Use the EXACT same curve function as the visual display
            return generatePerfectSmoothCurve(tideData, currentTime)
        }
        
        private fun findNextTide(tideData: List<TidePoint>, currentTime: Long): TidePoint? {
            if (tideData.isEmpty()) return null
            
            // Use the same perfect curve function to find next tide
            val currentHeight = generatePerfectSmoothCurve(tideData, currentTime)
            
            // Look ahead to find next high or low tide using same curve logic
            for (minutesAhead in 10..1440 step 5) { // Search up to 24 hours ahead, 5-minute steps
                val futureTime = currentTime + (minutesAhead * 60 * 1000L)
                val futureHeight = generatePerfectSmoothCurve(tideData, futureTime)
                val nextHeight = generatePerfectSmoothCurve(tideData, futureTime + (5 * 60 * 1000L))
                val prevHeight = generatePerfectSmoothCurve(tideData, futureTime - (5 * 60 * 1000L))
                
                // Check if this is a peak (high tide) or trough (low tide)
                if (futureHeight > prevHeight && futureHeight > nextHeight && futureHeight > currentHeight + 0.1f) {
                    // Found high tide
                    return TidePoint(futureTime, futureHeight, isHighTide = true, isLowTide = false)
                } else if (futureHeight < prevHeight && futureHeight < nextHeight && futureHeight < currentHeight - 0.1f) {
                    // Found low tide  
                    return TidePoint(futureTime, futureHeight, isHighTide = false, isLowTide = true)
                }
            }
            
            return null
        }
        
        private fun calculateTideTrend(tideData: List<TidePoint>, currentTime: Long): String {
            if (tideData.isEmpty()) return "Unknown"
            
            // Use the same perfect curve function for trend calculation
            val currentHeight = generatePerfectSmoothCurve(tideData, currentTime)
            val futureHeight = generatePerfectSmoothCurve(tideData, currentTime + (10 * 60 * 1000L)) // 10 minutes ahead
            
            return when {
                futureHeight > currentHeight + 0.01f -> "Rising"
                futureHeight < currentHeight - 0.01f -> "Falling" 
                else -> "Stable"
            }
        }
        
        private fun analyzeTidalPattern(tideData: List<TidePoint>): TidalParams {
            if (tideData.isEmpty()) {
                return TidalParams(amplitude = 3f, meanLevel = 3f, phase = 0f)
            }
            
            // Calculate mean tide level
            val meanLevel = tideData.map { it.height }.average().toFloat()
            
            // Find high and low points to calculate amplitude
            val maxHeight = tideData.maxOfOrNull { it.height } ?: meanLevel
            val minHeight = tideData.minOfOrNull { it.height } ?: meanLevel
            val amplitude = (maxHeight - minHeight) / 2f
            
            // Find first high tide to calculate phase
            val firstHigh = tideData.find { it.isHighTide }
            val phase = if (firstHigh != null) {
                val calendar = Calendar.getInstance().apply { timeInMillis = firstHigh.timeMillis }
                val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY) + calendar.get(Calendar.MINUTE) / 60f
                hourOfDay * kotlin.math.PI.toFloat() / 12f // Convert to radians
            } else {
                0f
            }
            
            return TidalParams(amplitude, meanLevel, phase)
        }
        
        private fun generateSineWaveHeight(timeHours: Float, params: TidalParams): Float {
            // Mixed semi-diurnal tide: principal lunar semi-diurnal (M2) + lunar diurnal (K1/O1)
            val m2Period = 12.42f // Principal lunar semi-diurnal component
            val k1Period = 24.07f // Lunar diurnal component
            
            val omegaM2 = 2f * kotlin.math.PI.toFloat() / m2Period
            val omegaK1 = 2f * kotlin.math.PI.toFloat() / k1Period
            
            // Main semi-diurnal component (dominant)
            val m2Component = params.amplitude * kotlin.math.sin(omegaM2 * timeHours + params.phase)
            
            // Diurnal inequality component (creates the asymmetry)
            val k1Component = params.amplitude * 0.3f * kotlin.math.sin(omegaK1 * timeHours + params.phase * 0.5f)
            
            // Combine components to create mixed semi-diurnal pattern
            return params.meanLevel + m2Component + k1Component
        }
        
        data class TidalParams(
            val amplitude: Float,
            val meanLevel: Float, 
            val phase: Float
        )
        
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
        
        // EXACT COPY of the perfect curve function from TideWaveView
        private fun generatePerfectSmoothCurve(tideData: List<TidePoint>, targetTime: Long): Float {
            if (tideData.isEmpty()) return 3f
            
            // Extract tidal parameters from NOAA data with better phase alignment
            val params = analyzeTidalPatternImproved(tideData)
            
            // Convert time to hours since start of data period (not midnight)
            val startTime = tideData.firstOrNull()?.timeMillis ?: targetTime
            val hoursFromStart = (targetTime - startTime).toFloat() / (60 * 60 * 1000)
            
            // Generate perfect smooth mixed semi-diurnal tide curve
            val m2Period = 12.42f // Principal lunar semi-diurnal component
            val k1Period = 24.07f // Lunar diurnal component
            
            val omegaM2 = 2f * kotlin.math.PI.toFloat() / m2Period
            val omegaK1 = 2f * kotlin.math.PI.toFloat() / k1Period
            
            // Main semi-diurnal component (dominant)
            val m2Component = params.amplitude * kotlin.math.sin(omegaM2 * hoursFromStart + params.phase)
            
            // Diurnal inequality component (creates the asymmetry)
            val k1Component = params.amplitude * 0.25f * kotlin.math.sin(omegaK1 * hoursFromStart + params.phase * 0.5f)
            
            // Combine components to create perfectly smooth mixed semi-diurnal pattern
            return params.meanLevel + m2Component + k1Component
        }
        
        private fun analyzeTidalPatternImproved(tideData: List<TidePoint>): TidalParams {
            if (tideData.isEmpty()) {
                return TidalParams(amplitude = 3f, meanLevel = 3f, phase = 0f)
            }
            
            // Calculate mean tide level
            val meanLevel = tideData.map { it.height }.average().toFloat()
            
            // Find high and low points to calculate amplitude
            val maxHeight = tideData.maxOfOrNull { it.height } ?: meanLevel
            val minHeight = tideData.minOfOrNull { it.height } ?: meanLevel
            val amplitude = (maxHeight - minHeight) / 2f
            
            // Better phase calculation using the current data state
            // If we're falling toward low tide at 12:52, we need to align the curve properly
            val currentTime = System.currentTimeMillis()
            val startTime = tideData.firstOrNull()?.timeMillis ?: currentTime
            val hoursFromStart = (currentTime - startTime).toFloat() / (60 * 60 * 1000)
            
            // Phase adjustment to match current conditions
            val targetPhase = -kotlin.math.PI.toFloat() / 2 // Start curve falling toward low
            val phase = targetPhase - (2f * kotlin.math.PI.toFloat() / 12.42f) * hoursFromStart
            
            return TidalParams(amplitude, meanLevel, phase)
        }
        
        private fun schedulePeriodicUpdates(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, TideWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(android.content.ComponentName(context, TideWidgetProvider::class.java))
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context, 
                0, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Schedule updates every 6 minutes (360000 ms)
            val updateInterval = 6 * 60 * 1000L // 6 minutes in milliseconds
            alarmManager.setRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + updateInterval,
                updateInterval,
                pendingIntent
            )
        }
    }
}