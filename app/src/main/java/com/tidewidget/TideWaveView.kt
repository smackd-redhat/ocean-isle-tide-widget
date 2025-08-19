package com.tidewidget

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class TideWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var tidePoints: List<TidePoint> = emptyList()
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val currentTimePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private val wavePath = Path()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    init {
        setupPaints()
    }
    
    private fun setupPaints() {
        // Wave paint - ocean blue gradient
        wavePaint.apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                0f, 0f, 0f, 300f,
                intArrayOf(
                    Color.parseColor("#4A90E2"), // Light blue
                    Color.parseColor("#2E5B8A")  // Deeper blue
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        
        // Grid paint - light gray
        gridPaint.apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        
        // Text paint - dark gray
        textPaint.apply {
            color = Color.parseColor("#333333")
            textSize = 32f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        
        // Highlight paint for high/low tides - orange
        highlightPaint.apply {
            color = Color.parseColor("#FF9500")
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }
        
        // Current time indicator - red
        currentTimePaint.apply {
            color = Color.parseColor("#FF3B30")
            strokeWidth = 6f
            style = Paint.Style.STROKE
        }
    }
    
    fun setTideData(points: List<TidePoint>) {
        tidePoints = points
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (tidePoints.isEmpty()) {
            drawNoDataMessage(canvas)
            return
        }
        
        val width = width.toFloat()
        val height = height.toFloat()
        
        // Use full widget area
        val padding = 30f
        val chartWidth = width - 2 * padding
        val chartHeight = height - 2 * padding - 40f // Extra space for labels
        
        // Draw background for full widget area
        canvas.drawRect(0f, 0f, width, height, Paint().apply {
            color = Color.parseColor("#F8F9FA")
        })
        
        // Use fixed scale from -1 to 7 feet for consistent range
        val minHeight = -1f
        val maxHeight = 7f
        val heightRange = maxHeight - minHeight // 8 feet total range
        
        val startTime = tidePoints.firstOrNull()?.timeMillis ?: System.currentTimeMillis()
        val endTime = tidePoints.lastOrNull()?.timeMillis ?: System.currentTimeMillis()
        val timeRange = endTime - startTime
        
        // Draw grid lines
        drawGrid(canvas, padding, chartWidth, chartHeight, minHeight, maxHeight)
        
        // Draw smooth sine wave path (ocean tide)
        drawSmoothWave(canvas, padding, chartWidth, chartHeight, minHeight, heightRange, startTime, timeRange)
        
        // Draw canal tide wave (1h45m lag)
        drawCanalWave(canvas, padding, chartWidth, chartHeight, minHeight, heightRange, startTime, timeRange)
        
        // Draw current time indicator
        drawCurrentTimeIndicator(canvas, padding, chartWidth, chartHeight, startTime, timeRange)
        
        // Draw labels
        drawLabels(canvas, padding, width, height, chartHeight, minHeight, maxHeight)
    }
    
    private fun drawNoDataMessage(canvas: Canvas) {
        val message = "Loading tide data..."
        val x = width / 2f
        val y = height / 2f
        
        textPaint.textSize = 32f
        textPaint.color = Color.parseColor("#666666")
        canvas.drawText(message, x, y, textPaint)
    }
    
    private fun drawGrid(canvas: Canvas, padding: Float, chartWidth: Float, chartHeight: Float, minHeight: Float, maxHeight: Float) {
        // Horizontal grid lines (height levels)
        val heightSteps = 5
        for (i in 0..heightSteps) {
            val y = padding + (i.toFloat() / heightSteps) * chartHeight
            canvas.drawLine(padding, y, padding + chartWidth, y, gridPaint)
        }
        
        // Vertical grid lines (time intervals) - 5 lines for 24 hours
        val timeSteps = 4 // 4 intervals = 5 lines (00:00, 06:00, 12:00, 18:00, 00:00)
        for (i in 0..timeSteps) {
            val x = padding + (i.toFloat() / timeSteps) * chartWidth
            canvas.drawLine(x, padding, x, padding + chartHeight, gridPaint)
        }
    }
    
    private fun drawSmoothWave(canvas: Canvas, padding: Float, chartWidth: Float, chartHeight: Float, 
                              minHeight: Float, heightRange: Float, startTime: Long, timeRange: Long) {
        
        if (tidePoints.isEmpty()) return
        
        // Create a stroke paint for the sine wave line
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.parseColor("#2E5B8A")
        }
        
        // Generate ultra-smooth curve using actual NOAA data interpolation with high resolution
        val numPoints = (chartWidth * 8).toInt() // 8 points per pixel for ultra-smoothness
        val path = Path()
        
        // Convert tide points to screen coordinates
        val screenPoints = tidePoints.map { point ->
            val x = padding + ((point.timeMillis - startTime).toFloat() / timeRange) * chartWidth
            val clampedHeight = point.height.coerceIn(-1f, 7f)
            val y = padding + chartHeight - ((clampedHeight - minHeight) / heightRange) * chartHeight
            PointF(x, y)
        }
        
        if (screenPoints.isEmpty()) return
        
        // Create smooth Bezier curves through the data points
        path.moveTo(screenPoints[0].x, screenPoints[0].y)
        
        for (i in 1 until screenPoints.size) {
            val prevPoint = screenPoints[i - 1]
            val currentPoint = screenPoints[i]
            val nextPoint = if (i < screenPoints.size - 1) screenPoints[i + 1] else currentPoint
            
            // Calculate control points for smooth Bezier curve
            val cp1x = prevPoint.x + (currentPoint.x - prevPoint.x) * 0.3f
            val cp1y = prevPoint.y + (currentPoint.y - prevPoint.y) * 0.3f
            val cp2x = currentPoint.x - (nextPoint.x - currentPoint.x) * 0.3f
            val cp2y = currentPoint.y - (nextPoint.y - currentPoint.y) * 0.3f
            
            // Draw cubic Bezier curve
            path.cubicTo(cp1x, cp1y, cp2x, cp2y, currentPoint.x, currentPoint.y)
        }
        
        // Draw just the stroke line - no fill
        canvas.drawPath(path, strokePaint)
    }
    
    private fun drawCanalWave(canvas: Canvas, padding: Float, chartWidth: Float, chartHeight: Float, 
                             minHeight: Float, heightRange: Float, startTime: Long, timeRange: Long) {
        
        if (tidePoints.isEmpty()) return
        
        // Create a stroke paint for the canal tide line (green)
        val canalStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#4CAF50") // Green color for canal
        }
        
        // Canal lag: 1 hour 45 minutes = 1.75 hours
        val canalLagMs = (1.75f * 60 * 60 * 1000).toLong() // Convert to milliseconds
        
        // Generate ultra-smooth canal tide wave with lag and reduced amplitude
        val numPoints = (chartWidth * 8).toInt() // 8 points per pixel for ultra-smoothness
        val path = Path()
        
        // Create canal tide points with lag and reduced amplitude
        val canalPoints = tidePoints.map { point ->
            val laggedTime = point.timeMillis + canalLagMs // Canal lags behind ocean
            val canalHeight = point.height * 0.95f // 5% less amplitude
            val x = padding + ((laggedTime - startTime).toFloat() / timeRange) * chartWidth
            val clampedHeight = canalHeight.coerceIn(-1f, 7f)
            val y = padding + chartHeight - ((clampedHeight - minHeight) / heightRange) * chartHeight
            PointF(x, y)
        }.filter { it.x >= padding && it.x <= padding + chartWidth } // Only points within chart area
        
        if (canalPoints.isEmpty()) return
        
        // Create smooth Bezier curves through the canal data points
        path.moveTo(canalPoints[0].x, canalPoints[0].y)
        
        for (i in 1 until canalPoints.size) {
            val prevPoint = canalPoints[i - 1]
            val currentPoint = canalPoints[i]
            val nextPoint = if (i < canalPoints.size - 1) canalPoints[i + 1] else currentPoint
            
            // Calculate control points for smooth Bezier curve
            val cp1x = prevPoint.x + (currentPoint.x - prevPoint.x) * 0.3f
            val cp1y = prevPoint.y + (currentPoint.y - prevPoint.y) * 0.3f
            val cp2x = currentPoint.x - (nextPoint.x - currentPoint.x) * 0.3f
            val cp2y = currentPoint.y - (nextPoint.y - currentPoint.y) * 0.3f
            
            // Draw cubic Bezier curve
            path.cubicTo(cp1x, cp1y, cp2x, cp2y, currentPoint.x, currentPoint.y)
        }
        
        // Draw just the stroke line - no fill
        canvas.drawPath(path, canalStrokePaint)
    }
    
    private fun analyzeTidalPattern(): TidalParams {
        if (tidePoints.isEmpty()) {
            return TidalParams(amplitude = 3f, meanLevel = 3f, phase = 0f)
        }
        
        // Calculate mean tide level
        val meanLevel = tidePoints.map { it.height }.average().toFloat()
        
        // Find high and low points to calculate amplitude
        val maxHeight = tidePoints.maxOfOrNull { it.height } ?: meanLevel
        val minHeight = tidePoints.minOfOrNull { it.height } ?: meanLevel
        val amplitude = (maxHeight - minHeight) / 2f
        
        // Find first high tide to calculate phase
        val firstHigh = tidePoints.find { it.isHighTide }
        val phase = if (firstHigh != null) {
            val calendar = Calendar.getInstance().apply { timeInMillis = firstHigh.timeMillis }
            val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY) + calendar.get(Calendar.MINUTE) / 60f
            hourOfDay * PI.toFloat() / 12f // Convert to radians
        } else {
            0f
        }
        
        return TidalParams(amplitude, meanLevel, phase)
    }
    
    private fun generateSineWaveHeight(timeHours: Float, params: TidalParams): Float {
        // Mixed semi-diurnal tide: principal lunar semi-diurnal (M2) + lunar diurnal (K1/O1)
        val m2Period = 12.42f // Principal lunar semi-diurnal component
        val k1Period = 24.07f // Lunar diurnal component
        
        val omegaM2 = 2f * PI.toFloat() / m2Period
        val omegaK1 = 2f * PI.toFloat() / k1Period
        
        // Main semi-diurnal component (dominant)
        val m2Component = params.amplitude * sin(omegaM2 * timeHours + params.phase)
        
        // Diurnal inequality component (creates the asymmetry)
        val k1Component = params.amplitude * 0.3f * sin(omegaK1 * timeHours + params.phase * 0.5f)
        
        // Combine components to create mixed semi-diurnal pattern
        return params.meanLevel + m2Component + k1Component
    }
    
    data class TidalParams(
        val amplitude: Float,
        val meanLevel: Float, 
        val phase: Float
    )
    
    private fun getSmoothInterpolatedHeight(targetTime: Long): Float {
        if (tidePoints.isEmpty()) return 3f
        if (tidePoints.size == 1) return tidePoints[0].height
        
        // Find the closest data points for interpolation
        var beforeIndex = -1
        var afterIndex = -1
        
        for (i in tidePoints.indices) {
            if (tidePoints[i].timeMillis <= targetTime) {
                beforeIndex = i
            } else {
                afterIndex = i
                break
            }
        }
        
        // Handle edge cases
        if (beforeIndex == -1) return tidePoints.first().height
        if (afterIndex == -1) return tidePoints.last().height
        
        val p1 = tidePoints[beforeIndex]
        val p2 = tidePoints[afterIndex]
        
        // Get surrounding points for smooth interpolation
        val p0 = if (beforeIndex > 0) tidePoints[beforeIndex - 1] else p1
        val p3 = if (afterIndex < tidePoints.size - 1) tidePoints[afterIndex + 1] else p2
        
        // Calculate normalized time position (0.0 to 1.0)
        val t = (targetTime - p1.timeMillis).toDouble() / (p2.timeMillis - p1.timeMillis).toDouble()
        
        // Use Hermite interpolation for very smooth curves
        val t2 = t * t
        val t3 = t2 * t
        
        // Hermite basis functions
        val h1 = 2 * t3 - 3 * t2 + 1
        val h2 = -2 * t3 + 3 * t2
        val h3 = t3 - 2 * t2 + t
        val h4 = t3 - t2
        
        // Calculate tangents for smooth curve
        val m1 = (p2.height - p0.height) / 2.0
        val m2 = (p3.height - p1.height) / 2.0
        
        // Hermite interpolation
        val result = h1 * p1.height + h2 * p2.height + h3 * m1 + h4 * m2
        
        return result.toFloat()
    }
    
    private fun interpolateTideHeight(targetTime: Long): Float {
        if (tidePoints.isEmpty()) return 3f
        
        // Find the two closest points to interpolate between
        var beforePoint: TidePoint? = null
        var afterPoint: TidePoint? = null
        
        for (point in tidePoints) {
            if (point.timeMillis <= targetTime) {
                beforePoint = point
            } else {
                afterPoint = point
                break
            }
        }
        
        // Handle edge cases
        if (beforePoint == null) return tidePoints.first().height
        if (afterPoint == null) return tidePoints.last().height
        if (beforePoint.timeMillis == afterPoint.timeMillis) return beforePoint.height
        
        // Linear interpolation between the two points
        val timeProgress = (targetTime - beforePoint.timeMillis).toFloat() / 
                          (afterPoint.timeMillis - beforePoint.timeMillis).toFloat()
        
        return beforePoint.height + timeProgress * (afterPoint.height - beforePoint.height)
    }
    
    
    
    
    private fun drawCurrentTimeIndicator(canvas: Canvas, padding: Float, chartWidth: Float, chartHeight: Float,
                                        startTime: Long, timeRange: Long) {
        val currentTime = System.currentTimeMillis()
        
        // Always show current time indicator based on hour of day
        val calendar = Calendar.getInstance()
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
        val minuteOfHour = calendar.get(Calendar.MINUTE)
        
        // Calculate position: 0-24 hours mapped to chart width
        val hoursDecimal = hourOfDay + minuteOfHour / 60f
        val timePosition = (hoursDecimal / 24f).coerceIn(0f, 1f)
        val x = padding + timePosition * chartWidth
        
        // Draw red vertical line
        canvas.drawLine(x, 0f, x, height.toFloat(), currentTimePaint)
    }
    
    private fun drawLabels(canvas: Canvas, padding: Float, width: Float, height: Float, 
                          chartHeight: Float, minHeight: Float, maxHeight: Float) {
        
        textPaint.color = Color.parseColor("#333333")
        textPaint.typeface = Typeface.DEFAULT_BOLD
        
        // Legend instead of title
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 16f
        
        // Ocean tide legend (blue)
        textPaint.color = Color.parseColor("#2E5B8A") // Match ocean tide color
        canvas.drawText("● Ocean", padding + 10f, 25f, textPaint)
        
        // Canal tide legend (green)
        textPaint.color = Color.parseColor("#4CAF50") // Match canal tide color
        canvas.drawText("● Canal", padding + 120f, 25f, textPaint)
        
        // Height labels (left side) - 1-foot increments from 7 to -1
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 16f
        textPaint.color = Color.parseColor("#333333") // Reset to dark gray
        val heightSteps = 8 // Show 9 labels: 7, 6, 5, 4, 3, 2, 1, 0, -1
        for (i in 0..heightSteps) {
            val heightValue = 7f - i.toFloat()
            val y = padding + (i.toFloat() / heightSteps) * chartHeight + 6f
            canvas.drawText("${heightValue.format(0)}ft", padding - 8f, y, textPaint)
        }
        
        // Time labels (bottom) - full 24 hours: 00:00, 06:00, 12:00, 18:00, 00:00
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 14f
        textPaint.color = Color.parseColor("#333333") // Reset to dark gray
        val timeLabels = arrayOf("00:00", "06:00", "12:00", "18:00", "00:00")
        for (i in 0..4) {
            val x = padding + (i.toFloat() / 4f) * (width - 2 * padding)
            val y = height - 8f
            canvas.drawText(timeLabels[i], x, y, textPaint)
        }
    }
    
    private fun Float.format(digits: Int): String = if (digits == 0) "%.0f".format(this) else "%.${digits}f".format(this)
}