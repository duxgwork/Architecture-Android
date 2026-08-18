package com.example.architecture

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.architecture.data.repository.CounterRepositoryImpl
import com.example.architecture.databinding.ActivityMainBinding
import com.example.architecture.domain.usecase.DecrementCounterUseCase
import com.example.architecture.domain.usecase.IncrementCounterUseCase
import com.example.architecture.domain.usecase.ResetCounterUseCase
import com.example.architecture.presentation.counter.CounterEffect
import com.example.architecture.presentation.counter.CounterViewModel
import kotlinx.coroutines.launch

/**
 * Clean Architecture - 展示层：渲染 State，触发 ViewModel 方法
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val viewModel: CounterViewModel by lazy {
        val repository = CounterRepositoryImpl()
        CounterViewModel.Factory(
            incrementCounter = IncrementCounterUseCase(repository),
            decrementCounter = DecrementCounterUseCase(repository),
            resetCounter = ResetCounterUseCase(repository)
        ).create(CounterViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        collectState()
        collectEffects()
    }

    /** 将用户操作委托给 ViewModel */
    private fun setupListeners() {
        binding.btnIncrement.setOnClickListener {
            viewModel.increment()
        }
        binding.btnDecrement.setOnClickListener {
            viewModel.decrement()
        }
        binding.btnReset.setOnClickListener {
            viewModel.reset()
        }
    }

    /** 收集 UI 状态，渲染界面 */
    private fun collectState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.tvCounter.text = getString(R.string.counter_label, state.count)
                }
            }
        }
    }

    /** 收集一次性 Side Effect */
    private fun collectEffects() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effect.collect { effect ->
                    when (effect) {
                        is CounterEffect.ResetSuccess ->
                            Toast.makeText(
                                this@MainActivity,
                                R.string.reset_success,
                                Toast.LENGTH_SHORT
                            ).show()
                    }
                }
            }
        }
    }
}
