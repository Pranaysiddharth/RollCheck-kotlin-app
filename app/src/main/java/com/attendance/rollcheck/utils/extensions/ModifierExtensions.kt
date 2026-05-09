package com.attendance.rollcheck.utils.extensions

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Detects simultaneous multi-finger touches and fires [onMultiTouch].
 * Used on the Absent/Present button row to prevent accidental double-marks.
 */
fun Modifier.multiTouchGuard(onMultiTouch: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        var multiTouchDetected = false
        var hasActivePointer   = true
        while (hasActivePointer) {
            val event        = awaitPointerEvent(PointerEventPass.Initial)
            val pressedCount = event.changes.count { it.pressed }
            if (pressedCount > 1) {
                multiTouchDetected = true
                event.changes.forEach { it.consume() }
            }
            hasActivePointer = event.changes.any { it.pressed }
        }
        if (multiTouchDetected) onMultiTouch()
    }
}