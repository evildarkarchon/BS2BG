# Workbench parity Preview rollback checkpoint

Verified on 2026-09-27 from clean source commit `8f8b0bfee8bf81d23446b33495240952fc2490db` for issue #112.

- `pwsh -NoProfile -File .\tools\java25\package-java25.ps1 -DpiMatrix` compiled all 97 production sources with Java 25 `-Xlint:all -Werror`, passed 550 Java tests in 50 suites without failures or skips, and built the Windows x64 app-image with the pinned Temurin 25.0.4.1+1 and JavaFX 25.0.4 inputs. The bundled runtime has 13 modules.
- The same archive passed all 28 packaged Workbench workflows at each of 100%, 125%, and 150% primary-display scale. Each launcher exited with code 0. The primary display returned to its captured 100% setting.
- `smoke-mixed-monitor-dpi.ps1` exercised that exact archive at primary 150% ↔ secondary 100% and primary 125% ↔ secondary 100%. Each of the six landings verified native DPI, the 1200-pixel breakpoint, the 800×600 logical minimum, semantic focus, and bounds for Project diagnostics, Activity, disabled Retry, status, and Cancel. The original 100% primary scale was restored.
- `Invoke-Pester -Path tools/java25 -Output Minimal` passed 141 tests across seven PowerShell test files.
- Dependency convergence, runtime closure, the bundled `juniversalchardet` MPL 1.1 license, third-party notices, corresponding-source metadata, and isolated `%LOCALAPPDATA%\BS2BG Preview` profile state are recorded in the package and smoke reports.

The exact tested rollback archive is [BS2BG-1.1.2-windows-x64.zip](BS2BG-1.1.2-windows-x64.zip), SHA-256 `7039add51156da6e3a775327824e564dc6c09e979a39bd3fee8a741b315066c0`. [SHA256SUMS](SHA256SUMS) records the same hash. The [package report](windows-app-image.json), [primary DPI matrix](dpi-matrix/windows-app-image-dpi-matrix.json), and [mixed-monitor report](mixed-monitor/mixed-monitor.json) identify that archive; per-scale and per-landing UIA trees, screenshots, and launcher logs are retained beside them. Machine-local paths in generated JSON refer to the original verification environment.

This is the Preview rollback checkpoint before issue #93's stable 2.0 release-candidate qualification and publication.
