package com.example.architecture.mvi

/**
 * MVI Side Effect：一次性副作用事件（如 Toast 提示）
 */
sealed interface CounterEffect {
    data object ResetSuccess : CounterEffect
}
