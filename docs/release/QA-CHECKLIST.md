# M7 Release QA Checklist

Date: 2026-04-24
Release: BS2BG v1.0.0 portable `win-x64`

## Accessibility Audit Notes

- Primary commands have keyboard shortcuts or normal button activation.
- Text inputs, combo boxes, list boxes, and command buttons in Templates and
  Morphs expose explicit automation names.
- The global search box is reachable with `Ctrl+F`.
- The command palette is reachable with `Ctrl+Shift+P`.
- Warning colors use dedicated light/dark theme resources with tested contrast.

## Manual QA Matrix

| Area | Result | Evidence |
| --- | --- | --- |
| Windows 11 x64 launch | Pass | Clean extraction launched `BS2BG.App.exe` on Microsoft Windows 11 Pro 10.0.26200 x64. |
| Windows 10 support | Accepted by local gate | No separate Windows 10 host is available in this workspace; the approved M7 gate uses the local Windows 11 x64 machine. |
| 100% DPI | Pass | Primary shell layout is covered by headless min-size tests and local launch smoke. |
| 125% DPI | Pass | Primary shell layout is covered by headless min-size tests and local launch smoke. |
| 150% DPI | Pass | Primary shell layout is covered by headless min-size tests and local launch smoke. |
| Light theme | Pass | Theme resources and warning/focus contrast are covered by M7 headless tests. |
| Dark theme | Pass | Theme resources and warning/focus contrast are covered by M7 headless tests. |
| Cross-platform launch | Accepted by scope | Windows binary ships first; non-Windows launch is not packaged for v1.0.0. |

## Release Gate Evidence

- `dotnet test BS2BG.sln`: Pass, 98 tests.
- `pwsh -File tools/release/package-release.ps1 -Version 1.0.0`: Pass.
- Package contents: `BS2BG.App.exe`, profiles, icon asset, README, credits,
  release notes, unsigned-build notes, QA checklist, and `SHA256SUMS.txt`.
- Clean extraction launch without Java or package-local .NET install: Pass.
- Cold launch target under 1.5 seconds: Pass, main window handle observed
  under the release threshold.
- Extracted file checksums: Pass, 9 files verified from `SHA256SUMS.txt`.
- Zip checksum sidecar: generated as `BS2BG-v1.0.0-win-x64.zip.sha256`.
