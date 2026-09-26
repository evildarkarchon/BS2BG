# Custom Morph Target authoring checkpoint

Verified on 2026-09-19 from clean source commit `544953ae105f81c8f018f3b3b54c47c1f2271a5e`.

- `./tools/java25/package-java25.ps1 -ExpectedDpiPercent 100`: full clean Java 25 verification, 456 tests in 44 suites, no failures or skips; all 20 packaged workflows passed; launcher exited with code 0.
- `Invoke-Pester -Path tools/java25 -Output Detailed`: 104 passed, no failures or skips.
- Standards and Spec reviews found no remaining findings after the corrections.
- Visual inspection confirmed the retained Morphs High Contrast, Morphs narrow, and Templates editor High Contrast screenshots show the actual Workbench. Required captures wait for transition covers to clear and include only the application window.

The packaged workflow verifies Custom Morph Target creation, condition/name validation, filtering, sorting,
identity-stable selection, initial and individual Slider Preset assignment, relationship removal/clearing,
confirmed target removal/visible clearing, generated output continuity, save/reopen, accessibility, and narrow mode.
System theme, High Contrast, and motion changes run in a final accessibility phase after authoring and file workflows;
the user's original preferences are restored.

This run proves 96 DPI / 100% scale. It does not claim execution at 125% or 150%; those remain part of the wider
Workbench display-scale matrix documented in [the packaging guide](../../windows-app-image.md).

Image: 235 files, SHA-256 `18880efa905d1265007a7c91a14e2d48bba9c1eda574cc670dbb351d693addb4`.
Archive: `BS2BG-1.1.2-windows-x64.zip`, SHA-256 `39644788943405f5e6d9f07c2f24798cf3dc08515bf695d9a8b1eca418a6429f`.

Reports, transcripts, notices, and screenshots are retained verbatim beside this file. Paths inside generated reports
refer to the machine-local build and temporary smoke directories; the archive remains generated output under `target/`.
