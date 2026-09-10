# Video Player

An Android video player being built as the visual-media companion to Resonate and the future Momentum intelligence layer.

## Direction

The player starts as a strong local/offline media player. Its architecture is deliberately event-driven so playback behavior can later become a signal source for Momentum.

### Current foundation

- Native Android + Kotlin + Jetpack Compose
- Media3 / ExoPlayer playback
- Local video selection through the Android document picker
- Persistent read permission where supported
- Standard playback controls supplied by Media3
- A stable `MomentumEvent` contract for future intelligence integration
- Application ID: `com.innotrepid.videoplayer`

## Planned evolution

1. Reliable local playback and library
2. Resume position and watch history
3. Better browsing, metadata and collections
4. Rich behavioral event capture and diagnostics
5. Local intelligence layer
6. Momentum bridge shared with Resonate
7. Anticipatory cross-media recommendations

## Status

**v0.2.0 — package identity + foundation**

The repository intentionally starts small. Playback reliability comes before intelligence features.
