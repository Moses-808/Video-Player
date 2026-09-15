# Visonate v0.4.2

## What's New

### Bug Fixes
- **Fixed race condition in library import**: Concurrent calls to `add()` could create duplicate entries. Now re-queries within IO coroutine to detect existing records reliably.
- **Fixed cursor null safety**: `queryDisplayName()` now safely validates cursor columns and null values before accessing data, preventing crashes on document import.
- **Improved JSON robustness**: Library loading now gracefully skips malformed entries instead of crashing on missing `id` or `uri` fields. Corrupted library data no longer causes total failure.
- **Enhanced error diagnostics**: Added logging to `PlaybackController` to track retry attempts and error codes, making troubleshooting easier.
- **Fixed unbounded log growth**: `MomentumEventRecorder` now trims the event log on startup, preventing disk space issues after app crashes.
- **Added data corruption detection**: `VideoBehaviorSignalInterpreter` now warns when playback position exceeds duration, catching metadata anomalies early.

### Build Info
- Android SDK 35 (target), API 26+ (min)
- Kotlin 2.0.21
- Jetpack Compose with Material3
- Media3/ExoPlayer 1.6.0
