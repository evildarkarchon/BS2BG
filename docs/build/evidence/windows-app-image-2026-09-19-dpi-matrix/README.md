# Automated display-scale checkpoint

Verified on 2026-09-19 from clean source commit `f4281ac6fcb916f5f72eca5eb7498b8522ed4125`:

```powershell
.\tools\java25\package-java25.ps1 -DpiMatrix -DpiScalePercents 150,100,125
Invoke-Pester -Path tools/java25 -Output Detailed
```

The order starts with the scale that exposed the catalog-height regression. The default matrix remains 100, 125,
and 150; requested cases are configurable and validated against the monitor's observed Settings choices.

| Requested scale | Measured application DPI | Packaged workflows | Exit code |
| --- | --- | --- | --- |
| 100% | 96 | 20/20 passed | 0 |
| 125% | 120 | 20/20 passed | 0 |
| 150% | 144 | 20/20 passed | 0 |

The clean Java gate passed **464 tests in 44 suites**, with no failures, errors, or skips. PowerShell verification
passed **132 tests**, with no failures or skips. Standards and Spec reviews found no remaining findings.

One archive was built and used unchanged for all three fresh smoke processes. The runner discovered the primary
display as `\\.\DISPLAY2` / Settings **Display 2**, captured its original **100% / 96 DPI**, and restored and
verified that value after the matrix. The aggregate records no workflow or restoration error. A separate native
read after completion also reported 100% / 96 DPI.

Archive: `BS2BG-1.1.2-windows-x64.zip`, SHA-256
`c73d9414a15b133ea37a2b06841d004b8641cd1c73d95353732567554e513ef7`.
Image: 235 files, SHA-256 `8a53497faf438a5ee59892018bbec06a1dc1c482bfdee8006a3804671738d5bb`.

The [aggregate report](dpi-matrix/windows-app-image-dpi-matrix.json) links the individual runs. Each scale directory
retains its smoke report, UIA trees, launcher diagnostics, and application-window screenshots. Visual review of
the 150% Templates editor High Contrast capture confirmed the short catalog has no redundant inner scrollbar;
the 125% Morphs narrow capture also shows the expected layout. System accessibility tests run at the end of each case.

The matrix exposed and drove a fix for fixed logical catalog-border padding at fractional scales. Catalog heights
now track actual snapped CSS insets, scene attachment, and render-scale changes. Eight controller regression cases
cover both catalogs at fractional render scales and after scale changes; no production DPI-specific branch was added.

Development verification also exercised real 125%/150% transitions and intentionally terminated a child after a
125% change. The persisted pending record was then restored through `smoke-dpi-matrix.ps1 -RestoreOnly`, with native
DPI readback. Unit coverage additionally verifies configurable 125%/175% cases with an original 200% setting,
session-wide contention, failure restoration, and independent workflow/restoration errors.

These results cover the primary display using English Windows Settings. They do not claim a mixed-monitor,
hot-plug, custom-scaling, or broader hardware/OS matrix. Machine-local paths inside generated reports are retained
verbatim; the packaged archive remains generated output under `target/`.
