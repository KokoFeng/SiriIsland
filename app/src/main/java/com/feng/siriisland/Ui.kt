package com.feng.siriisland

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.glass(radius: Int = 24, a1: Int = 70, a2: Int = 28): GradientDrawable =
    GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.argb(a1, 255, 255, 255), Color.argb(a2, 255, 255, 255))).apply {
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), Color.argb(90, 255, 255, 255))
    }

fun Context.darkGlass(radius: Int = 24): GradientDrawable = GradientDrawable().apply {
    setColor(Color.argb(238, 30, 30, 36))
    cornerRadius = dp(radius).toFloat()
    setStroke(dp(1), Color.argb(100, 255, 255, 255))
}
