# BS2BG Portable Release

BS2BG is a C#/Avalonia port of the Java jBS2BG tool for converting BodySlide
preset XML data into BodyGen `templates.ini`, `morphs.ini`, and BoS JSON output.

## Package Layout

- `BS2BG.App.exe` - self-contained Windows x64 graphical executable.
- `BS2BG.Cli.exe` - self-contained Windows x64 automation executable for headless `generate` workflows.
- `settings.json` - Skyrim CBBE profile data.
- `settings_UUNP.json` - Skyrim UUNP profile data.
- `settings_FO4_CBBE.json` - Fallout 4 CBBE profile data.
- `assets/res/icon.png` - source icon asset.
- `CREDITS.md` - original and port author credits.
- `RELEASE-NOTES.md` - v1.0.0 release notes and known limitations.
- `UNSIGNED-BUILD.md` - unsigned build warning and verification path.
- `QA-CHECKLIST.md` - release validation notes.
- `BODYGEN-BODYSLIDE-BOS-SETUP.md` - source-of-truth packaged setup and troubleshooting guide for BodyGen, BodySlide, BodyTypes of Skyrim/BoS, and output-location checks.
- `SIGNING-INFO.txt` - signed/unsigned status, optional certificate identity, and verification command when signing was configured.
- `SHA256SUMS.txt` - package file checksums.

## Running

Extract the zip to a writable folder and launch `BS2BG.App.exe`. No installer,
Java runtime, or .NET runtime is required for the packaged Windows build.

Keep the profile JSON files next to the executables. They are loaded from the
application directory at startup by both the app and CLI.

For automation, run `BS2BG.Cli.exe --help` or `BS2BG.Cli.exe generate --help`
from the extracted folder. The CLI uses the same Core generation services as the
graphical app, including validation-first writes and explicit output selection.

## Fallout 4 CBBE profile

The bundled Fallout 4 CBBE profile uses a distinct FO4 slider seed with `1.0`
defaults, `1.0` multipliers, and no inverted sliders until authoritative
calibration data is validated. This release note keeps the profile-confidence
context outside the in-app main workflow.

## Verification

Use the external `BS2BG-v1.0.0-win-x64.zip.sha256` checksum sidecar to verify the
downloaded zip, and use the packaged `SHA256SUMS.txt` file to verify files after
extraction.

Read `SIGNING-INFO.txt` after extraction to see whether the build was signed. A
signed build records `Status: Signed` and a SignTool verification command. An
unsigned build is still a valid release artifact when the SHA-256 sidecar and
packaged `SHA256SUMS.txt` verification both succeed.

## Setup and troubleshooting guide

Read `BODYGEN-BODYSLIDE-BOS-SETUP.md` before copying generated files into a
mod-manager or manual layout. It is the packaged source of truth for BodySlide
preset inputs, BodyGen `templates.ini`/`morphs.ini` placement, BodyTypes of
Skyrim/BoS JSON output, common output-location mistakes, and the boundary that
BS2BG generates files and setup guidance; it does not edit game plugins.
