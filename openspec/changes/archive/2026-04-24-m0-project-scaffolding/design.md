## Context

The repository currently contains the Java reference app, fixtures, PRD, and OpenSpec scaffolding, but no C# solution. M0 must establish the project layout and retire the slider-math risk before UI or workflow work begins.

## Goals / Non-Goals

**Goals:**
- Create the solution and project topology from PRD section 6.1.
- Wire Avalonia 12, ReactiveUI, DI, theme resources, and an empty main window.
- Implement `SliderMathFormatter` and dual float formatters in `BS2BG.Core`.
- Commit Java-generated fixture outputs and make golden tests pass.

**Non-Goals:**
- No full project-file round-trip, XML import UI, morph workflows, or export command shell.
- No optional v2 UX upgrades beyond the empty shell foundation.

## Decisions

- Keep `BS2BG.Core` on `netstandard2.1` with no Avalonia references so formatter behavior remains testable and future CLI work remains feasible.
- Target App and Tests at `net10.0`; use xUnit v3 because Avalonia.Headless v12 expects it.
- Put formatter logic behind explicit text and BoS JSON formatting methods so the two Java output paths cannot be accidentally collapsed into one helper.
- Generate expected fixture outputs from the Java implementation before changing C# behavior and treat those files as the oracle.

## Risks / Trade-offs

- Java fixture generation may require old JavaFX tooling -> document the command path and commit generated outputs so M0 tests do not depend on a live JavaFX install.
- `netstandard2.1` limits newer C# features in Core -> keep Core syntax conservative and use newer language features only in App/Tests.
- Formatter edge cases are easy to miss -> add tests for integer-valued floats, fractional values, negative values, inversion, multipliers, and half-up rounding.

## Migration Plan

This is the first C# milestone, so no user-data migration occurs. The only rollout step is adding the solution, fixture corpus, and test suite beside the Java reference.

## Open Questions

- Confirm the exact Java fixture regeneration command once the old Java build is exercised.
