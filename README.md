# Visonate

Visonate is a local Android video player built as the visual-media companion to Resonate and the Momentum intelligence layer.

## What it does

Visonate starts with reliable local playback and grows into an anticipatory viewing companion. It observes playback behavior locally, interprets useful signals, predicts likely next videos, presents those predictions and learns from what you accept or ignore.

### Implemented foundation

- Native Android + Kotlin + Jetpack Compose
- Media3 / ExoPlayer playback
- Play/pause, fast seek, ±10 second controls, previous/next and controlled retry
- Playback speed, volume, mute, portrait/landscape and fullscreen controls
- Folder/series-aware queues with natural episode ordering
- Resume position and persistent watch metadata
- Device-wide video discovery through Android MediaStore when permission is granted
- Manual video import through the Android document picker
- Duplicate-aware library reconciliation and missing-media cleanup
- Favorites / Saved videos, search, sorting, collections and Continue Watching
- Pulse with a pinned featured long-video preview and Momentum-powered predictions
- Lifecycle-aware muted previews with contextual preview moments and fallback thumbnails
- Local Momentum event recording, behavior interpretation, prediction feedback and diagnostic export
- Swipe navigation between Pulse, Library and Saved

## Architecture

Playback is isolated behind `PlaybackController` and exposes a `PlaybackUiState` flow to the player UI. Viewing telemetry crosses into intelligence through `VideoBehaviorAnalyzer`, keeping playback and prediction concerns separate.

The product relationship is:

- **Resonate** — music, feeling and sequence behavior
- **Visonate** — visual media, continuity, attention and viewing intent
- **Momentum** — the shared anticipatory intelligence layer

## Status

Visonate 0.4.1 — Momentum-enabled local video player. The `main` branch is the canonical source of truth.

## License

Copyright (c) 2026 Innotrepid. All rights reserved.
