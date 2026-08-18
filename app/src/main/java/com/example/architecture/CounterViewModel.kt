package com.example.architecture

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * MVVM ViewModel：直接暴露可观察的数据，提供操作方法
 */
class CounterViewModel : ViewModel() {

    /** 计数器当前值 */
    private val _count = MutableLiveData(0)
    val count: LiveData<Int> = _count

    /** 一次性事件：重置成功提示 */
    private val _resetEvent = MutableLiveData<Event<Unit>>()
    val resetEvent: LiveData<Event<Unit>> = _resetEvent

    /** 增加计数 */
    fun increment() {
        _count.value = (_count.value ?: 0) + 1
    }

    /** 减少计数 */
    fun decrement() {
        _count.value = (_count.value ?: 0) - 1
    }

    /** 重置计数 */
    fun reset() {
        _count.value = 0
        _resetEvent.value = Event(Unit)
    }
}

/**
 * 事件包装器：确保一次性事件只被消费一次
 */
class Event<out T>(private val content: T) {

    private var hasBeenHandled = false

    /** 消费事件，返回内容（仅首次调用有效） */
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }
}
