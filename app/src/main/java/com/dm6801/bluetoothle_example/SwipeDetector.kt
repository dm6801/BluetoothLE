package com.dm6801.bluetoothle_example

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.absoluteValue

abstract class SwipeDetector : View.OnTouchListener {

    private var detector: GestureDetector? = null

    abstract fun onSwipeRight()

    abstract fun onSwipeLeft()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        return getDetector((v ?: return false).context).onTouchEvent(event)
    }

    private fun getDetector(context: Context): GestureDetector {
        return detector ?: createDetector(context)
    }

    private fun createDetector(context: Context): GestureDetector {
        return GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent?): Boolean {
                return true
            }

            override fun onFling(
                e1: MotionEvent,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y
                if (deltaX.absoluteValue < deltaY.absoluteValue) return false
                if (deltaX.absoluteValue < 50 && velocityX.absoluteValue < 100) return false
                if (deltaX > 0) onSwipeRight() else onSwipeLeft()
                return true
            }
        }).also { detector = it }
    }

}