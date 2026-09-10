# Video Player

An Android video player being built as the visual-media companion to Resonate and the future Momentum intelligence layer.

## Direction

The player starts as a strong local/offline media player. Its architecture is deliberately event-driven so playback behavior can later become a signal source for Momentum.

### Current foundation

- Native Android + Kotlin + Jetpack Compose
- Media3 / ExoPlayer playback
- Device-wide video discovery through Android MediaStore when permission is granted
- Incremental library reconciliation: new MediaStore videos are indexed and missing indexed videos are removed
- Manual video selection through the Android document picker as a fallback
- Persistent read permission for manually selected videos where supported
- Resume position and basic watch state stored as metadata only
- Standard playback controls supplied by Media3
- Stable `MomentumEvent` contract for playback behavior
- Bounded, asynchronous JSON Lines behavioral logging
- Local diagnostics export from the library screen
- Application ID: `com.innotrepid.videoplayer`

The app never copies the original video bytes into its private library. The library stores references and metadata, so a 10 GB movie does not become a 10 GB app.

## Diagnostics

Playback events are stored locally in the app's private storage as a bounded JSON Lines log. The recorder captures starts, resumes, pauses, seeks, completions, skips and playback errors without storing the original video bytes.

Use **Export Diagnostics** in the library to create a local diagnostics file and share it through Android's share sheet. This is intended to make bug reports reproducible without manually remembering what happened during a test session.

## Planned evolution

1. Reliable local playback and library
2. Resume position and watch history
3. Better browsing, metadata and collections
4. Cached thumbnails and richer MediaStore metadata
5. Scan/playback diagnostics and robust behavioral event capture
6. Local intelligence layer
7. Momentum bridge shared with Resonate
8. Anticipatory cross-media recommendations

## Status

**v0.4.0 — behavioral telemetry + local diagnostics**

The repository intentionally starts small. Playback reliability and a trustworthy local library come before intelligence features.
