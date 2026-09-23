# Morphs bulk actions and portrait inspection checkpoint

Verified on 2026-09-22 from clean source commit `83e5a1c08417b3a0ae0b5880299ef0a9ecb26471`.

- `./tools/java25/package-java25.ps1 -DpiMatrix`: complete clean Java 25 verification passed with 481 tests in 45 suites, no failures or skips. The same extracted Windows x64 app-image archive passed all 23 packaged workflows at 100%, 125%, and 150% display scale; each launcher exited with code 0. The primary display was restored to 100%.
- `Invoke-Pester -Path tools/java25 -Output Normal`: 133 passed, no failures or skips.
- The Standards review found no documented-standard violations. Its two remaining maintainability observations concern the paired pending Fill Empty fields and the size of the existing Workbench controller. The Spec review's decorative-inspector and outside-click coverage findings were corrected and verified in the packaged matrix.

The packaged workflows capture only visible empty NPC Morph Assignments for Fill Empty, draw independently from the chosen eligible Slider Presets, leave hidden and assigned NPCs unchanged, and verify Escape, outside-click, Cancel, focus return, validation, feedback, generated output, Save As, and reopen. Portrait checks cover editor-ID-specific `.jpeg` priority, name-only fallback, missing-image state, stable selection, the dedicated viewer's accessible image/dimensions/zoom/Close controls, narrow layout, Light, Dark, and High Contrast. The unmounted legacy popup, controller, notification, image, and dark-style routes were removed.

Image: 235 files, SHA-256 `cf9242817183a86349757e399a99aee1d322876a701cae3f68ae2a7af2d6e9dc`.
Archive: `BS2BG-1.1.2-windows-x64.zip`, SHA-256 `eda011559fc64cb8c01bbb34e2032993c40e782f3d4ffc7daf500e67eaec3ded`.

The [matrix report](dpi-matrix/windows-app-image-dpi-matrix.json) and [package report](windows-app-image.json) record the source commit, archive identity, all three smoke results, and display restoration. Per-scale smoke reports, UIA trees, and screenshots are retained under `dpi-matrix/dpi-100/`, `dpi-matrix/dpi-125/`, and `dpi-matrix/dpi-150/`. Paths inside generated reports refer to the machine-local build and temporary smoke directories; the archive remains generated output under `target/`.
