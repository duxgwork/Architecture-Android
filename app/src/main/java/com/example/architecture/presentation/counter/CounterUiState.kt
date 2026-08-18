package com.example.architecture.presentation.counter

/**
 * 计数器页面 UI 状态
 */
data class CounterUiState(
    val count: Int = 0
) {
    companion object {
        fun initial() = CounterUiState(count = 0)
    }
}
