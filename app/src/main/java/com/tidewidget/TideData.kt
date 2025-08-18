package com.tidewidget

import java.util.Date

data class TideData(
    val time: Date,
    val height: Double,
    val type: TideType
)

enum class TideType {
    HIGH,
    LOW,
    NORMAL
}

data class TidePrediction(
    val datetime: String,
    val height: String,
    val type: String
)

data class NoaaResponse(
    val predictions: List<TidePrediction>
)

data class TidePoint(
    val timeMillis: Long,
    val height: Float,
    val isHighTide: Boolean = false,
    val isLowTide: Boolean = false
)