package com.harrison.counters.data

data class Counter(val id: Int, val startTime: Long? = null) {
    fun elapsedSecondsAt(now: Long): Int =
        startTime?.let { ((now - it) / 1000).toInt() } ?: 0

    fun millisUntilNextTickAt(now: Long): Long? =
        startTime?.let { 1000 - (now - it) % 1000 }
}
