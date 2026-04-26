---
phase: 01-profile-correctness-and-trust
reviewed: 2026-04-26T14:06:00Z
depth: standard
files_reviewed: 7
files_reviewed_list:
  - src/BS2BG.App/ViewModels/TemplatesViewModel.cs
  - tests/BS2BG.Tests/TemplatesViewModelTests.cs
  - tests/BS2BG.Tests/SliderMathFormatterTests.cs
  - tests/BS2BG.Tests/ExportWriterTests.cs
  - tests/BS2BG.Tests/MorphCoreTests.cs
  - .planning/ROADMAP.md
  - .planning/REQUIREMENTS.md
findings:
  critical: 0
  warning: 1
  info: 0
  total: 1
status: issues_found
---

# Phase 01: Code Review Report

**Reviewed:** 2026-04-26T14:06:00Z  
**Depth:** standard  
**Files Reviewed:** 7  
**Status:** issues_found

## Summary

Reviewed the Phase 01 gap-closure plans 01-05, 01-06, and 01-07 plus the changed ViewModel, tests, and planning contract documents. The prior review warning is resolved: unresolved saved profiles now leave the selector blank, preview/BoS/inspector math uses the fallback calculation profile, and choosing the displayed fallback bundled profile explicitly adopts it. The remaining issue is advisory test coverage: the new UUNP BoS JSON assertion is too weak to catch a regression where the UUNP path accidentally uses the Skyrim CBBE output.

## Warnings

### WR-01: UUNP BoS JSON coverage does not prove the UUNP profile table is used

**File:** `tests/BS2BG.Tests/SliderMathFormatterTests.cs:192-198`

**Issue:** `BosJsonUsesBundledProfileSpecificSliderTables` adds strong FO4-only assertions and exact Skyrim CBBE `Breasts` values, but the UUNP branch only asserts that the UUNP JSON does not contain `"slidername1": "Breasts"`. That assertion would still pass if the UUNP variable accidentally received the Skyrim CBBE JSON, because Skyrim CBBE emits `Breasts` as `slidername2`, not `slidername1`. This leaves part of the prior verification gap insufficiently protected: the test does not fail for a plausible UUNP profile-regression path.

**Fix:** Add exact UUNP assertions that would fail if the output matched Skyrim CBBE or FO4. For example, assert the UUNP default-only `Breasts` preset produces no non-redundant sliders and differs from the CBBE output:

```csharp
skyrimUunpJson.Should().Contain("\"slidersnumber\": 0");
skyrimUunpJson.Should().NotContain("\"slidername");
skyrimUunpJson.Should().NotBe(skyrimCbbeJson);
skyrimUunpJson.Should().NotBe(fallout4CbbeJson);
```

If the intent is to prove UUNP inversion with an explicit slider, add a second UUNP-specific preset/assertion with explicit values and exact `highvalue`/`lowvalue` expectations.

---

_Reviewed: 2026-04-26T14:06:00Z_  
_Reviewer: the agent (gsd-code-reviewer)_  
_Depth: standard_
