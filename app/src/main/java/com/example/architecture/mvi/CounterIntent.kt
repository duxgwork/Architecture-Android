package com.example.architecture.mvi

/**
 * MVI Intent：用户触发的意图
 */
sealed interface CounterIntent {
    data object Increment : CounterIntent
    data object Decrement : CounterIntent
    data object Reset : CounterIntent
}
