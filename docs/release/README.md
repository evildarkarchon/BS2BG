# BS2BG Portable Release

BS2BG is a C#/Avalonia port of the Java jBS2BG tool for converting BodySlide
preset XML data into BodyGen `templates.ini`, `morphs.ini`, and BoS JSON output.

## Package Layout

- `BS2BG.App.exe` - self-contained Windows x64 executable.
- `settings.json` - Skyrim CBBE profile data.
- `settings_UUNP.json` - Skyrim UUNP profile data.
- `settings_FO4_CBBE.json` - Fallout 4 CBBE profile data.
- `assets/res/icon.png` - source icon asset.
- `CREDITS.md` - original and port author credits.
- `RELEASE-NOTES.md` - v1.0.0 release notes and known limitations.
- `UNSIGNED-BUILD.md` - unsigned build warning and verification path.
- `QA-CHECKLIST.md` - release validation notes.
- `SHA256SUMS.txt` - package file checksums.

## Running

Extract the zip to a writable folder and launch `BS2BG.App.exe`. No installer,
Java runtime, or .NET runtime is required for the packaged Windows build.

Keep the profile JSON files next to the executable. They are loaded from the
application directory at startup.

## Fallout 4 CBBE profile

The bundled Fallout 4 CBBE profile uses a distinct FO4 slider seed with `1.0`
defaults, `1.0` multipliers, and no inverted sliders until authoritative
calibration data is validated. This release note keeps the profile-confidence
context outside the in-app main workflow.

## Verification

Use the external `BS2BG-v1.0.0-win-x64.zip.sha256` file to verify the downloaded
zip, and use the packaged `SHA256SUMS.txt` file to verify files after extraction.
