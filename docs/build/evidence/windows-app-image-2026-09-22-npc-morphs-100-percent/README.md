# NPC Morph Assignment authoring checkpoint

Verified on 2026-09-22 from clean source commit `6bfed46336717bfeffca91ec68335c1d3a6025eb`.

- `./tools/java25/package-java25.ps1`: full clean Java 25 verification, 474 tests in 45 suites with no failures or skips; all 21 packaged workflows passed; the launcher exited with code 0.
- `Invoke-Pester -Path tools/java25 -Output Detailed`: 132 passed with no failures or skips.
- The Spec review found no remaining issue 24 gap after output-safe plugin validation and shared accessibility names. The Standards review's documented terminology finding was corrected; its remaining repeated type-ahead loop is a low-risk judgement call with separate catalog state.
- Visual inspection confirmed the retained NPC narrow-mode screenshot shows the packaged Workbench. Capture waited for other windows and transition covers to clear.

The packaged NPC workflow covers keyboard and real-pointer creation, required and duplicate-identity validation,
same-display-name identities, mutual exclusion with Custom Morph Target selection, filtering, sorting, type-ahead,
hidden-selection removal, Slider Preset relationships, generated Morphs output, confirmed deletion and filtered
clearing, Save As, reopening, accessibility, and narrow mode. The final system accessibility phase checks theme,
High Contrast, and motion behavior and restores the original preferences.

This run proves 96 DPI / 100% scale. It does not claim execution at 125% or 150%; those belong to the wider
Workbench display-scale matrix documented in [the packaging guide](../../windows-app-image.md).

Image: 235 files, SHA-256 `4ab1b3c86a5693ddb5ca45205931b669bfc86139d8ae68a02f79fdf105fc08f5`.
Archive: `BS2BG-1.1.2-windows-x64.zip`, SHA-256 `cbe7dc8e6eca2db268b6bb1896f208539956b8f2599c68ef5e293cfa4b7abcb1`.

Reports, hashes, notices, UIA trees, and screenshots are retained beside this file. Paths inside generated reports
refer to the machine-local build and temporary smoke directories; the archive remains generated output under `target/`.
