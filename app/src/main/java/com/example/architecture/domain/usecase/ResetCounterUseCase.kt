package com.example.architecture.domain.usecase

import com.example.architecture.domain.model.Counter
import com.example.architecture.domain.repository.CounterRepository

/**
 * 重置计数 Use Case
 */
class ResetCounterUseCase(
    private val repository: CounterRepository
) {
    suspend operator fun invoke(): Counter {
        val reset = Counter(value = 0)
        repository.updateCounter(reset)
        return reset
    }
}
