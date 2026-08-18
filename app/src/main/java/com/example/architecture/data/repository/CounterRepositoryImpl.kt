package com.example.architecture.data.repository

import com.example.architecture.domain.model.Counter
import com.example.architecture.domain.repository.CounterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 计数器仓库实现：内存数据源
 */
class CounterRepositoryImpl : CounterRepository {

    private val counterFlow = MutableStateFlow(Counter(value = 0))

    override fun observeCounter(): Flow<Counter> = counterFlow.asStateFlow()

    override suspend fun getCounter(): Counter = counterFlow.value

    override suspend fun updateCounter(counter: Counter) {
        counterFlow.value = counter
    }
}
