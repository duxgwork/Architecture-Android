package com.example.architecture

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.architecture.databinding.ActivityMainBinding
import com.example.architecture.mvi.CounterIntent
import kotlinx.coroutines.launch

/**
 * MVI View：渲染 State，分发 Intent
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: CounterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        collectState()
        collectEffects()
    }

    /** 将用户操作转化为 Intent 分发给 ViewModel */
    private fun setupListeners() {
        binding.btnIncrement.setOnClickListener {
            viewModel.dispatch(CounterIntent.Increment)
        }
        binding.btnDecrement.setOnClickListener {
            viewModel.dispatch(CounterIntent.Decrement)
        }
        binding.btnReset.setOnClickListener {
            viewModel.dispatch(CounterIntent.Reset)
        }
    }

    /** 收集 State，渲染 UI */
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
                        is com.example.architecture.mvi.CounterEffect.ResetSuccess ->
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
