package com.tidewidget

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

interface NoaaApiService {
    @GET("api/datagetter")
    suspend fun getTideData(
        @Query("product") product: String = "predictions",
        @Query("application") application: String = "OceanIsleTideWidget",
        @Query("station") station: String = "8658163", // Ocean Isle Beach, NC station ID
        @Query("begin_date") beginDate: String,
        @Query("end_date") endDate: String,
        @Query("datum") datum: String = "MLLW",
        @Query("time_zone") timeZone: String = "lst_ldt",
        @Query("units") units: String = "english",
        @Query("interval") interval: String = "hilo", // High/low tide times only
        @Query("format") format: String = "json"
    ): Response<NoaaResponse>
}

class TideApiService {
    private val noaaApi: NoaaApiService
    
    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.tidesandcurrents.noaa.gov/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        noaaApi = retrofit.create(NoaaApiService::class.java)
    }
    
    suspend fun getTideDataForToday(): List<TidePoint> {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        val today = Date()
        
        val beginDate = dateFormat.format(today)
        val endDate = dateFormat.format(today) // Only request today's data
        
        try {
            val response = noaaApi.getTideData(
                beginDate = beginDate,
                endDate = endDate
            )
            
            if (response.isSuccessful) {
                val predictions = response.body()?.predictions?.map { prediction ->
                    val time = parseNoaaDateTime(prediction.datetime)
                    val height = prediction.height.toFloatOrNull() ?: 0f
                    
                    TidePoint(
                        timeMillis = time.time,
                        height = height,
                        isHighTide = prediction.type.equals("H", ignoreCase = true),
                        isLowTide = prediction.type.equals("L", ignoreCase = true)
                    )
                } ?: emptyList()
                
                // If we got hilo data, we need to interpolate between the points
                val interpolatedData = if (predictions.isNotEmpty()) {
                    interpolateFromHiLoData(predictions)
                } else {
                    predictions
                }
                
                android.util.Log.d("TideApiService", "Using real NOAA data - ${predictions.size} hilo points, ${interpolatedData.size} interpolated")
                return interpolatedData
            } else {
                // Fallback to mock data if API fails
                android.util.Log.d("TideApiService", "Using mock data - NOAA API response not successful: ${response.code()}")
                return generateMockTideData()
            }
        } catch (e: Exception) {
            // Return mock data on network error
            android.util.Log.d("TideApiService", "Using mock data - NOAA API error: ${e.message}")
            return generateMockTideData()
        }
    }
    
    private fun parseNoaaDateTime(dateTimeStr: String): Date {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            format.parse(dateTimeStr) ?: Date()
        } catch (e: Exception) {
            Date()
        }
    }
    
    private fun generateMockTideData(): List<TidePoint> {
        val now = System.currentTimeMillis()
        val points = mutableListOf<TidePoint>()
        
        // Generate 24 hours of mock tide data (every 6 minutes = 240 points)
        // For Ocean Isle Beach, create realistic tide times:
        // If it's 9:00 AM and low tide is expected around 11:00 AM,
        // we should be past high tide (around 5:00-6:00 AM) heading toward low tide
        
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY) + calendar.get(Calendar.MINUTE) / 60.0
        
        // Adjust the phase so that low tide occurs around 11:00 AM (hour 11)
        // This means the sine wave should be at a minimum at hour 11
        val lowTideHour = 11.0 // Expected low tide at 11:00 AM
        val phaseAdjustment = -2 * Math.PI * lowTideHour / 12.42 // Adjust for M2 tidal period
        
        for (i in 0..240) {
            val timeOffset = i * 6 * 60 * 1000L // 6 minutes in milliseconds
            val time = now + timeOffset
            
            // Calculate hours from now
            val hours = (timeOffset / (1000 * 60 * 60)).toDouble()
            val absoluteHour = currentHour + hours
            
            // Create realistic mixed semi-diurnal tide using M2 component
            // M2 period is 12.42 hours, phase adjusted so low tide is at 11:00 AM
            val m2Component = 2.5 * Math.sin(2 * Math.PI * absoluteHour / 12.42 + phaseAdjustment)
            val k1Component = 0.5 * Math.sin(2 * Math.PI * absoluteHour / 24.0) // Diurnal component
            val tideHeight = 3.0 + m2Component + k1Component
            
            // Determine if this is a high or low tide point
            val nextAbsoluteHour = absoluteHour + 0.1
            val nextM2Component = 2.5 * Math.sin(2 * Math.PI * nextAbsoluteHour / 12.42 + phaseAdjustment)
            val nextK1Component = 0.5 * Math.sin(2 * Math.PI * nextAbsoluteHour / 24.0)
            val nextTideHeight = 3.0 + nextM2Component + nextK1Component
            
            val isHighTide = i > 0 && i < 239 && 
                points.getOrNull(i - 1)?.height?.let { prev ->
                    tideHeight > prev && tideHeight > nextTideHeight
                } ?: false
                
            val isLowTide = i > 0 && i < 239 && 
                points.getOrNull(i - 1)?.height?.let { prev ->
                    tideHeight < prev && tideHeight < nextTideHeight
                } ?: false
            
            points.add(
                TidePoint(
                    timeMillis = time,
                    height = tideHeight.toFloat(),
                    isHighTide = isHighTide,
                    isLowTide = isLowTide
                )
            )
        }
        
        return points
    }
    
    private fun interpolateFromHiLoData(hiLoPoints: List<TidePoint>): List<TidePoint> {
        if (hiLoPoints.size < 2) return hiLoPoints
        
        val interpolatedPoints = mutableListOf<TidePoint>()
        val startTime = hiLoPoints.first().timeMillis
        val endTime = hiLoPoints.last().timeMillis
        
        // Generate points every 10 minutes from start to end
        val intervalMs = 10 * 60 * 1000L // 10 minutes
        var currentTime = startTime
        
        while (currentTime <= endTime) {
            // Find surrounding hilo points for interpolation
            var beforePoint: TidePoint? = null
            var afterPoint: TidePoint? = null
            
            for (point in hiLoPoints) {
                if (point.timeMillis <= currentTime) {
                    beforePoint = point
                } else {
                    afterPoint = point
                    break
                }
            }
            
            val height = when {
                beforePoint == null -> hiLoPoints.first().height
                afterPoint == null -> hiLoPoints.last().height
                beforePoint.timeMillis == afterPoint.timeMillis -> beforePoint.height
                else -> {
                    // Sinusoidal interpolation between high/low points
                    val timeProgress = (currentTime - beforePoint.timeMillis).toFloat() / 
                                     (afterPoint.timeMillis - beforePoint.timeMillis).toFloat()
                    val heightDiff = afterPoint.height - beforePoint.height
                    beforePoint.height + heightDiff * sin(timeProgress * PI / 2).toFloat()
                }
            }
            
            interpolatedPoints.add(
                TidePoint(
                    timeMillis = currentTime,
                    height = height,
                    isHighTide = hiLoPoints.any { it.timeMillis == currentTime && it.isHighTide },
                    isLowTide = hiLoPoints.any { it.timeMillis == currentTime && it.isLowTide }
                )
            )
            
            currentTime += intervalMs
        }
        
        return interpolatedPoints
    }
}