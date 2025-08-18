# 🌊 Ocean Isle Beach Tide Widget

An Android home screen widget that displays tide charts for Ocean Isle Beach, North Carolina in a beautiful wave format.

## Features

- **🌊 Wave-Style Visualization**: Beautiful tide chart displayed as ocean waves
- **📍 Ocean Isle Beach Specific**: Pre-configured for NOAA Station 8658163
- **⏰ Real-Time Updates**: Auto-refreshes every 30 minutes
- **🔄 24-Hour Forecast**: Shows tide predictions for the next 24 hours
- **🎯 High/Low Tide Markers**: Clearly highlights tide extremes
- **📱 Home Screen Widget**: Quick glance at tide conditions
- **📊 Current Status**: Shows current tide height and trend (rising/falling)

## Screenshots

### Widget Preview
The widget displays:
- Current tide height
- Next high/low tide time
- Tide trend (rising/falling)
- Beautiful wave chart with 24-hour forecast
- High and low tide markers

### Main App
- Full-screen tide chart
- Detailed wave visualization
- Refresh button for manual updates
- Instructions for adding widget

## Installation

1. **Download APK** (when available) or build from source
2. **Install** the app on your Android device
3. **Add Widget**: Long press on home screen → Widgets → "Ocean Isle Tide"
4. **Configure**: Follow setup instructions (pre-configured for Ocean Isle Beach)

## Building from Source

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK API 24+ (Android 7.0)
- Kotlin support

### Steps
1. Clone the repository
2. Open in Android Studio
3. Build and run on device/emulator
4. Install APK on target device

```bash
git clone <repository-url>
cd ocean-isle-tide-widget
./gradlew assembleDebug
```

## Data Source

Tide data is sourced from **NOAA Tides and Currents API**:
- Station: 8658163 (Ocean Isle Beach, NC)
- Datum: MLLW (Mean Lower Low Water)
- Units: Feet
- Timezone: Local Standard/Daylight Time

### API Endpoint
```
https://api.tidesandcurrents.noaa.gov/api/datagetter
```

## Widget Configuration

### Default Settings
- **Location**: Ocean Isle Beach, NC
- **Update Interval**: 30 minutes
- **Data Range**: 24 hours from current time
- **Size**: 4x2 grid cells (resizable)

### Customization Options
- Widget can be resized horizontally and vertically
- Automatic light/dark theme adaptation
- Tap to refresh functionality

## Technical Details

### Architecture
- **Language**: Kotlin
- **UI Framework**: Android Views with custom Canvas drawing
- **Networking**: Retrofit + OkHttp
- **Data Parsing**: Gson
- **Background Updates**: WorkManager

### Key Components
- `TideWaveView`: Custom view for wave visualization
- `TideWidgetProvider`: App widget provider
- `TideApiService`: NOAA API integration
- `TideData`: Data models and DTOs

### Performance
- Efficient canvas drawing with path caching
- Background data fetching with coroutines
- Minimal memory footprint for widget
- Graceful fallback to mock data if API unavailable

## Widget Behavior

### Update Schedule
- **Automatic**: Every 30 minutes via AppWidgetManager
- **Manual**: Tap widget to force refresh
- **App Launch**: Updates when main app is opened

### Error Handling
- Falls back to mock tide data if NOAA API unavailable
- Displays error state in widget if data cannot be loaded
- Retry mechanism for failed network requests

### Battery Optimization
- Minimal background processing
- Efficient network usage
- Respects system power management

## Ocean Isle Beach Information

**Ocean Isle Beach** is a barrier island town in Brunswick County, North Carolina. The area experiences semi-diurnal tides (two high and two low tides per day) with typical ranges of 3-6 feet.

### Tidal Characteristics
- **Tide Type**: Semi-diurnal
- **Average Range**: 4-5 feet
- **High Season**: Spring tides can reach 6+ feet
- **Low Season**: Neap tides around 3 feet

## Dependencies

```gradle
// Networking
implementation 'com.squareup.retrofit2:retrofit:2.9.0'
implementation 'com.squareup.retrofit2:converter-gson:2.9.0'

// Background Work
implementation 'androidx.work:work-runtime-ktx:2.9.0'

// UI Components
implementation 'androidx.core:core-ktx:1.12.0'
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'com.google.android.material:material:1.11.0'
```

## Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## Contributing

1. Fork the repository
2. Create feature branch
3. Make changes
4. Test on Android device
5. Submit pull request

## License

This project is licensed under the MIT License - see LICENSE file for details.

## Acknowledgments

- **NOAA Tides and Currents** for providing free tide data API
- **Ocean Isle Beach** community for inspiration
- **Android Open Source Project** for excellent documentation

## Support

For issues, questions, or feature requests:
1. Check existing issues
2. Create new issue with detailed description
3. Include device information and Android version

---

**Enjoy tracking the tides at Ocean Isle Beach! 🌊**