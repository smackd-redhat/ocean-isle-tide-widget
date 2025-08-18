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
        
        // Scale to 1/3 of screen height
        val scaledHeight = height / 3f
        val padding = 30f
        val chartWidth = width - 2 * padding
        val chartHeight = scaledHeight - 2 * padding - 40f // Extra space for labels
        
        // Draw background only for the scaled area
        canvas.drawRect(0f, 0f, width, scaledHeight, Paint().apply {
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
        
        // Draw smooth sine wave path
        drawSmoothWave(canvas, padding, chartWidth, chartHeight, minHeight, heightRange, startTime, timeRange)
        
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
        
        // Vertical grid lines (time intervals)
        val timeSteps = 6 // Every 4 hours for 24 hours
        for (i in 0..timeSteps) {
            val x = padding + (i.toFloat() / timeSteps) * chartWidth
            canvas.drawLine(x, padding, x, padding + chartHeight, gridPaint)
        }
    }
    
    private fun drawSmoothWave(canvas: Canvas, padding: Float, chartWidth: Float, chartHeight: Float, 
                              minHeight: Float, heightRange: Float, startTime: Long, timeRange: Long) {
        
        wavePath.reset()
        
        if (tidePoints.isEmpty()) return
        
        // Use actual NOAA tide data points for accurate representation
        var isFirst = true
        for (point in tidePoints) {
            val x = padding + ((point.timeMillis - startTime).toFloat() / timeRange) * chartWidth
            val clampedHeight = point.height.coerceIn(-1f, 7f)
            val y = padding + chartHeight - ((clampedHeight - minHeight) / heightRange) * chartHeight
            
            if (isFirst) {
                wavePath.moveTo(x, y)
                isFirst = false
            } else {
                wavePath.lineTo(x, y)
            }
        }
        
        // Close the path to create a filled area
        val lastX = padding + chartWidth
        val lastY = padding + chartHeight
        wavePath.lineTo(lastX, lastY)
        wavePath.lineTo(padding, lastY)
        wavePath.close()
        
        canvas.drawPath(wavePath, wavePaint)
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
        canvas.drawLine(x, 0f, x, height.toFloat() / 3f, currentTimePaint)
        
        // Draw "NOW" label
        textPaint.textSize = 12f
        textPaint.color = Color.parseColor("#FF3B30")
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("NOW", x, padding - 10f, textPaint)
    }
    
    private fun drawLabels(canvas: Canvas, padding: Float, width: Float, height: Float, 
                          chartHeight: Float, minHeight: Float, maxHeight: Float) {
        
        textPaint.color = Color.parseColor("#333333")
        textPaint.typeface = Typeface.DEFAULT_BOLD
        
        // Title
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 22f
        canvas.drawText("Tide Chart", width / 2f, 30f, textPaint)
        
        // Height labels (left side) - 1-foot increments from 7 to -1
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 16f
        val heightSteps = 8 // Show 9 labels: 7, 6, 5, 4, 3, 2, 1, 0, -1
        for (i in 0..heightSteps) {
            val heightValue = 7f - i.toFloat()
            val y = padding + (i.toFloat() / heightSteps) * chartHeight + 6f
            canvas.drawText("${heightValue.format(0)}ft", padding - 8f, y, textPaint)
        }
        
        // Time labels (bottom) - fixed times: 00:00, 06:00, 12:00, 18:00
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 14f
        val timeLabels = arrayOf("00:00", "06:00", "12:00", "18:00")
        for (i in 0..3) {
            val x = padding + (i.toFloat() / 3f) * (width - 2 * padding)
            val y = height / 3f - 8f
            canvas.drawText(timeLabels[i], x, y, textPaint)
        }
    }
    
    private fun Float.format(digits: Int): String = if (digits == 0) "%.0f".format(this) else "%.${digits}f".format(this)
}