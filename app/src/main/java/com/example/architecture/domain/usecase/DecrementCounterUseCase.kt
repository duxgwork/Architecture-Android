package com.example.architecture.domain.usecase

import com.example.architecture.domain.model.Counter
import com.example.architecture.domain.repository.CounterRepository

/**
 * 减少计数 Use Case
 */
class DecrementCounterUseCase(
    private val repository: CounterRepository
) {
    suspend operator fun invoke(): Counter {
        val current = repository.getCounter()
        val updated = current.copy(value = current.value - 1)
        repository.updateCounter(updated)
        return updated
    }
}
