package com.myapp.core

/** Wall-clock time in epoch millis, injected so caches and countdowns are testable. */
fun interface Clock {
    fun nowMillis(): Long
}

object WallClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
