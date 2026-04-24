# BS2BG v1.0.0 Release Notes

BS2BG v1.0.0 is the first portable C#/Avalonia release of this fork/port.

## Highlights

- Imports BodySlide XML presets and preserves Java-compatible slider math,
  rounding, and export formatting.
- Generates BodyGen `templates.ini`, `morphs.ini`, and BoS JSON output.
- Supports project round-tripping with the existing `.jbs2bg` extension.
- Provides Templates and Morphs workspaces with keyboard shortcuts, command
  palette access, drag-and-drop import, undo/redo, and theme selection.
- Packages as an installer-less, self-contained Windows x64 zip.

## Known Limitations

- Fallout 4 CBBE support is experimental in v1.0.0. The profile is seeded from
  available CBBE slider names and defaults, but tuning is intentionally best
  effort until users provide known-good FO4 BodyGen calibration data.
- Windows is the shipped binary target. Other platforms may build and launch
  through Avalonia, but they are not packaged for this release.
- The release is unsigned. See `UNSIGNED-BUILD.md` for expected Windows warning
  behavior and checksum verification steps.
