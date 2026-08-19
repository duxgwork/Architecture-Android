package com.example.architecture.presentation.counter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.architecture.domain.usecase.DecrementCounterUseCase
import com.example.architecture.domain.usecase.IncrementCounterUseCase
import com.example.architecture.domain.usecase.ResetCounterUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 计数器 ViewModel：调用 Use Case，管理 UI 状态，发出 Effect
 */
class CounterViewModel(
    private val incrementCounter: IncrementCounterUseCase,
    private val decrementCounter: DecrementCounterUseCase,
    private val resetCounter: ResetCounterUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(CounterUiState.initial())
    val state: StateFlow<CounterUiState> = _state.asStateFlow()

    private val _effect = Channel<CounterEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    fun increment() {
        viewModelScope.launch {
            // 以下两种写法完全等价：
//            val result =incrementCounter.invoke()
            val result =incrementCounter()   // ← 语法糖，编译器自动翻译为上一行
            _state.value = CounterUiState(count = result.value)
        }
    }

    fun decrement() {
        viewModelScope.launch {
            val result = decrementCounter()
            _state.value = CounterUiState(count = result.value)
        }
    }

    fun reset() {
        viewModelScope.launch {
            resetCounter()
            _state.value = CounterUiState(count = 0)
            _effect.send(CounterEffect.ResetSuccess)
        }
    }

    /**
     * ViewModel 工厂：组装依赖
     */
    class Factory(
        private val incrementCounter: IncrementCounterUseCase,
        private val decrementCounter: DecrementCounterUseCase,
        private val resetCounter: ResetCounterUseCase
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CounterViewModel(incrementCounter, decrementCounter, resetCounter) as T
        }
    }
}
