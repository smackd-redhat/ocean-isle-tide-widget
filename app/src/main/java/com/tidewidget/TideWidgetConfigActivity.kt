package com.tidewidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TideWidgetConfigActivity : AppCompatActivity() {
    
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_config)
        
        // Set the result to CANCELED initially
        setResult(Activity.RESULT_CANCELED)
        
        // Find the widget id from the intent
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }
        
        // If they gave us an intent without the widget id, just bail
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        
        setupViews()
    }
    
    private fun setupViews() {
        val titleText = findViewById<TextView>(R.id.config_title)
        val locationText = findViewById<TextView>(R.id.config_location)
        val addButton = findViewById<Button>(R.id.config_add_button)
        val cancelButton = findViewById<Button>(R.id.config_cancel_button)
        
        titleText.text = "Configure Tide Widget"
        locationText.text = "📍 Ocean Isle Beach, NC\n🌊 NOAA Station: 8658163"
        
        addButton.setOnClickListener {
            createWidget()
        }
        
        cancelButton.setOnClickListener {
            finish()
        }
    }
    
    private fun createWidget() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        
        // Update the widget
        TideWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
        
        // Return success
        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }
}