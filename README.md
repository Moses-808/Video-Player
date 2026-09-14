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
- Video thumbnails and resilient browsing previews
- Pulse home experience with contextual predictive previews
- Momentum prediction, explainable confidence and behavioral feedback learning
- Guarded predictive automatic next-video selection with deterministic fallback
- Local bounded JSON Lines behavioral diagnostics and explicit export
- Settings for intelligence, learning, auto-advance, appearance, library and diagnostics
- Horizontal screen navigation with animated transitions

The app never copies original video bytes into its private library. The library stores references and metadata, so a 10 GB movie does not become a 10 GB app.

## Momentum

Momentum is Visonate's anticipatory intelligence layer:

**Observe → Understand → Predict → Present → Learn → Decide**

Prediction and learned preferences are local. The player remains in control: Momentum only overrides deterministic continuation when its evidence is strong enough and the candidate belongs to the current playback session.

## Diagnostics

Playback events are stored locally in the app's private storage as a bounded JSON Lines log. The recorder captures starts, resumes, pauses, seeks, completions, skips and playback errors without storing the original video bytes.

Use **Settings → Diagnostics → Export** to create a local diagnostics file. Nothing is exported automatically.

## Privacy

Visonate is designed around local media and local intelligence. Video files are not copied into the app's library. Library metadata, playback history, behavioral events and learned prediction preferences remain on the device unless you explicitly export diagnostics.

## License

Visonate is proprietary software. © 2026 Innotrepid. All rights reserved. See `LICENSE` for the full terms.

## Status

**Visonate 0.4.0 — Momentum-enabled local video player**

The foundation is intentionally stable. Future development will focus on deeper anticipation and product refinement rather than replacing the playback core.
