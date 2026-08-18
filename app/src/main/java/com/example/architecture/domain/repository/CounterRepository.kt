package com.example.architecture.domain.repository

import com.example.architecture.domain.model.Counter
import kotlinx.coroutines.flow.Flow

/**
 * 计数器仓库接口：由 Data 层实现，Domain 层定义契约
 */
interface CounterRepository {

    /** 观察计数器变化 */
    fun observeCounter(): Flow<Counter>

    /** 获取当前计数器值 */
    suspend fun getCounter(): Counter

    /** 更新计数器值 */
    suspend fun updateCounter(counter: Counter)
}
