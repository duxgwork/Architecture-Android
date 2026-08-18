package com.example.architecture.mvi

/**
 * MVI 状态：计数器页面的 UI 状态
 */
data class CounterState(
    val count: Int = 0
) {
    companion object {
        fun initial() = CounterState(count = 0)
    }
}
