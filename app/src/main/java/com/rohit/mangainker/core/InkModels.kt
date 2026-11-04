package com.rohit.mangainker.core

data class InkPoint(val x: Float, val y: Float)

data class InkPath(
    val points: List<InkPoint>,
    val length: Float
)

data class ProcessingResult(
    val paths: List<InkPath>,
    val threshold: Int,
    val foregroundPixels: Int,
    val skeletonPixels: Int,
    val discardedComponents: Int
)

data class PageCalibration(
    val topLeft: InkPoint,
    val topRight: InkPoint,
    val bottomRight: InkPoint,
    val bottomLeft: InkPoint
)
