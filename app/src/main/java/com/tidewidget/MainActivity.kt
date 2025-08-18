package com.tidewidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var tideWaveView: TideWaveView
    private lateinit var refreshButton: Button
    private lateinit var addWidgetButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initializeViews()
        setupClickListeners()
        loadTideData()
    }
    
    private fun initializeViews() {
        tideWaveView = findViewById(R.id.tide_wave_view)
        refreshButton = findViewById(R.id.refresh_button)
        addWidgetButton = findViewById(R.id.add_widget_button)
    }
    
    private fun setupClickListeners() {
        refreshButton.setOnClickListener {
            loadTideData()
        }
        
        addWidgetButton.setOnClickListener {
            showAddWidgetInstructions()
        }
    }
    
    private fun loadTideData() {
        refreshButton.isEnabled = false
        refreshButton.text = "Loading..."
        
        lifecycleScope.launch {
            try {
                val tideService = TideApiService()
                val tideData = tideService.getTideDataForToday()
                
                tideWaveView.setTideData(tideData)
                refreshButton.text = "Refresh"
                refreshButton.isEnabled = true
                
                // Update any existing widgets
                updateWidgets()
                
                Toast.makeText(this@MainActivity, "Tide data updated", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                refreshButton.text = "Refresh"
                refreshButton.isEnabled = true
                Toast.makeText(this@MainActivity, "Failed to load tide data", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun updateWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, TideWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        
        val intent = Intent(this, TideWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
        }
        sendBroadcast(intent)
    }
    
    private fun showAddWidgetInstructions() {
        Toast.makeText(
            this,
            "Long press on home screen → Widgets → Ocean Isle Tide Widget",
            Toast.LENGTH_LONG
        ).show()
    }
}