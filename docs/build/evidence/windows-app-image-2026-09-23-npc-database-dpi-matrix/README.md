# NPC Database import and inspection checkpoint

Verified on 2026-09-23 from clean source commit `1ed21f4bc5118cc6d76464895acb702d1d0fcc03`.

- `./tools/java25/package-java25.ps1 -DpiMatrix`: the complete clean Java 25 gate compiled 101 production sources with `-Xlint:all -Werror` and passed 502 tests in 48 suites with no failures or skips. The same Windows x64 app-image archive passed all 26 packaged workflows at 100%, 125%, and 150% display scale. Each launcher exited with code 0, and the original 100% primary display scale was restored.
- `Invoke-Pester -Path tools/java25 -Output Normal`: 133 passed, with no failures or skips.
- Final Standards and Spec reviews found no remaining findings. The malformed-row policy chosen for this checkpoint rejects the entire source file while preserving other committed sources.

The packaged workflows cover source import order, deduplication, fallback names, Form IDs, filter and sort controls, stable selection, portraits and the viewer, source removal, visible clear, and reimport. They also exercise malformed, mixed, locked, retried, and cancelled sources; Activity and progress; keyboard focus, High Contrast, Light and Dark themes, narrow layout, and the 800×600 minimum client size.

Image: 235 files, SHA-256 `e16776f615b930bffcf66e5098dccfab0b683a89aa681f06f6b6cc0783357163`.
Archive: `BS2BG-1.1.2-windows-x64.zip`, SHA-256 `674028c48a90f3c7f3745c56d44ae8a7d77b7d3db24a1ecedd8dc714273f6970`.

The [matrix report](dpi-matrix/windows-app-image-dpi-matrix.json) and [package report](windows-app-image.json) record the source commit, archive identity, all three smoke results, and display restoration. Per-scale smoke reports, UIA trees, and screenshots are retained under `dpi-matrix/dpi-100/`, `dpi-matrix/dpi-125/`, and `dpi-matrix/dpi-150/`. Paths inside generated reports refer to machine-local build and temporary smoke directories; the archive remains generated output under `target/`.

The matrix covers the primary display. Mixed-monitor moves remain part of the later release audit described in the [packaging guide](../../windows-app-image.md).
