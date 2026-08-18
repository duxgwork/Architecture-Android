package com.example.architecture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.architecture.mvi.CounterEffect
import com.example.architecture.mvi.CounterIntent
import com.example.architecture.mvi.CounterState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI ViewModel：处理 Intent，管理 State，发出 Effect
 */
class CounterViewModel : ViewModel() {

    private val _state = MutableStateFlow(CounterState.initial())
    val state: StateFlow<CounterState> = _state.asStateFlow()

    private val _effect = Channel<CounterEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    fun dispatch(intent: CounterIntent) {
        when (intent) {
            is CounterIntent.Increment -> _state.update { it.copy(count = it.count + 1) }
            is CounterIntent.Decrement -> _state.update { it.copy(count = it.count - 1) }
            is CounterIntent.Reset -> {
                _state.update { it.copy(count = 0) }
                viewModelScope.launch {
                    _effect.send(CounterEffect.ResetSuccess)
                }
            }
        }
    }
}
