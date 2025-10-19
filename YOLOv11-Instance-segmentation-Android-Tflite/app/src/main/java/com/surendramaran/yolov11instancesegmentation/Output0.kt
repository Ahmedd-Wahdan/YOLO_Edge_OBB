package com.surendramaran.yolov11instancesegmentation



data class OrientedBoxResult(
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val angle: Float, // Angle in radians
    val cnf: Float,
    val cls: Int,
    val clsName: String
)