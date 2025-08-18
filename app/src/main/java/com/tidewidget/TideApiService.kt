package com.tidewidget

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.*

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
        @Query("interval") interval: String = "6", // 6-minute intervals
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
        val tomorrow = Date(today.time + 24 * 60 * 60 * 1000)
        
        val beginDate = dateFormat.format(today)
        val endDate = dateFormat.format(tomorrow)
        
        try {
            val response = noaaApi.getTideData(
                beginDate = beginDate,
                endDate = endDate
            )
            
            if (response.isSuccessful) {
                return response.body()?.predictions?.map { prediction ->
                    val time = parseNoaaDateTime(prediction.datetime)
                    val height = prediction.height.toFloatOrNull() ?: 0f
                    
                    TidePoint(
                        timeMillis = time.time,
                        height = height,
                        isHighTide = prediction.type.equals("H", ignoreCase = true),
                        isLowTide = prediction.type.equals("L", ignoreCase = true)
                    )
                } ?: emptyList()
            } else {
                // Fallback to mock data if API fails
                return generateMockTideData()
            }
        } catch (e: Exception) {
            // Return mock data on network error
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
        for (i in 0..240) {
            val timeOffset = i * 6 * 60 * 1000L // 6 minutes in milliseconds
            val time = now + timeOffset
            
            // Create a sine wave pattern for tides (2 cycles per day)
            val hours = (timeOffset / (1000 * 60 * 60)).toDouble()
            val tideHeight = 3.0 + 2.5 * Math.sin(2 * Math.PI * hours / 12.0) // 12-hour cycle
            
            // Determine if this is a high or low tide point
            val isHighTide = i > 0 && i < 239 && 
                points.getOrNull(i - 1)?.height?.let { prev ->
                    val next = 3.0 + 2.5 * Math.sin(2 * Math.PI * (hours + 0.1) / 12.0)
                    tideHeight > prev && tideHeight > next
                } ?: false
                
            val isLowTide = i > 0 && i < 239 && 
                points.getOrNull(i - 1)?.height?.let { prev ->
                    val next = 3.0 + 2.5 * Math.sin(2 * Math.PI * (hours + 0.1) / 12.0)
                    tideHeight < prev && tideHeight < next
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
}