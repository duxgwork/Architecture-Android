package com.example.architecture

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.architecture.databinding.ActivityMainBinding
import com.example.architecture.demo.WebSocketDemoActivity

/**
 * MVVM View：直接观察 ViewModel 的 LiveData，调用其暴露的方法
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: CounterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeViewModel()
    }

    /** 将按钮点击直接绑定到 ViewModel 的方法 */
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
        // 跳转到 WebSocket Topic 路由演示
        binding.btnWebSocketDemo.setOnClickListener {
            startActivity(Intent(this, WebSocketDemoActivity::class.java))
        }
    }

    /** 观察 ViewModel 暴露的 LiveData */
    private fun observeViewModel() {
        viewModel.count.observe(this) { count ->
            binding.tvCounter.text = getString(R.string.counter_label, count)
        }
        viewModel.resetEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let {
                Toast.makeText(
                    this@MainActivity,
                    R.string.reset_success,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
