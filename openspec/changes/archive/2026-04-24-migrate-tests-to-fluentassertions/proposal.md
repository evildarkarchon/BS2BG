## Why

The test suite currently relies on xUnit assertion calls throughout the C# tests, which makes many expectations harder to scan when they mix values, predicates, and exception checks. Migrating assertions to FluentAssertions will make test intent read more naturally while preserving the existing behavior coverage.

## What Changes

- Add FluentAssertions as a centrally versioned test dependency for `BS2BG.Tests`.
- Configure FluentAssertions for the test project so the suite can use its assertion extension methods consistently.
- Convert existing xUnit assertions in the test suite to FluentAssertions equivalents where doing so improves readability.
- Keep xUnit as the test runner and test framework; only assertion style changes.
- Preserve current test behavior, fixtures, and coverage while validating the converted suite with the existing `dotnet test` workflow.

## Capabilities

### New Capabilities
- `test-assertion-readability`: Defines the expected assertion style for the C# test suite so tests express expectations with FluentAssertions while remaining behavior-equivalent.

### Modified Capabilities

## Impact

- Affected project: `tests/BS2BG.Tests/BS2BG.Tests.csproj`.
- Affected dependency management: `Directory.Packages.props`.
- Affected code: C# test files under `tests/BS2BG.Tests/`.
- No runtime app API, serialization, export format, UI behavior, or user-facing behavior changes are intended.
