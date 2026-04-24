## 1. Solution Scaffold

- [x] 1.1 Create `BS2BG.sln` with Core, App, and Tests projects using the PRD target frameworks
- [x] 1.2 Add initial package references for Avalonia 12, ReactiveUI, DI, Splat integration, and xUnit v3
- [x] 1.3 Configure shared build settings, nullable annotations, analyzers, and language versions per project

## 2. App Shell

- [x] 2.1 Wire Avalonia app startup, DI service registration, and ReactiveUI view resolution
- [x] 2.2 Add an empty main window with PRD title, size, minimum size, and Fluent theme baseline
- [x] 2.3 Add placeholder root ViewModel and theme/resource structure without business logic

## 3. Core Formatter

- [x] 3.1 Port slider percent, invert, multiplier, interpolation, and half-up rounding behavior
- [x] 3.2 Implement separate Java text and minimal-json number formatting helpers
- [x] 3.3 Add unit tests for rounding, integer-valued floats, fractional values, inversion, multipliers, and negatives

## 4. Fixture Gate

- [x] 4.1 Generate or import Java reference fixture outputs before completing C# formatter work
- [x] 4.2 Add golden tests that compare formatter output against the fixture corpus
- [x] 4.3 Run `dotnet test` and confirm M0 tests pass
