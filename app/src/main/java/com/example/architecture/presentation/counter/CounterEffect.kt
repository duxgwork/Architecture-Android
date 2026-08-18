package com.example.architecture.presentation.counter

/**
 * 一次性 Side Effect：如 Toast 提示
 */
sealed interface CounterEffect {
    data object ResetSuccess : CounterEffect
}
