## Why

BS2BG needs a verified .NET/Avalonia foundation before any feature work can safely replace the JavaFX app. M0 retires the highest-risk parity area first by scaffolding the solution and proving slider math/formatting against Java-generated fixtures.

## What Changes

- Create the `BS2BG.sln` solution with `BS2BG.Core`, `BS2BG.App`, and `BS2BG.Tests` projects.
- Establish MVVM, DI, theme, and empty main-window infrastructure for the Avalonia app.
- Port the core slider math formatter, including Java text formatting and minimal-json number formatting differences.
- Commit the reference fixture corpus and make golden tests pass against Java output.
- Keep `BS2BG.Core` free of UI dependencies so the domain layer remains testable and future CLI-ready.

## Capabilities

### New Capabilities
- `project-scaffolding`: Defines the solution skeleton, app shell, dependency injection, theming baseline, and slider-math golden-test foundation.

### Modified Capabilities

## Impact

- Adds .NET 10/Avalonia 12 project files, package references, and app startup code.
- Adds Core formatting/domain infrastructure and xUnit v3 test infrastructure.
- Introduces committed test fixtures and golden outputs generated from the Java reference before C# implementation changes.
