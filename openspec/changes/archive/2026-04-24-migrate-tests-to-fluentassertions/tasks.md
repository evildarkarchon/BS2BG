## 1. Dependency Setup

- [x] 1.1 Add a centrally managed `FluentAssertions` package version in `Directory.Packages.props`.
- [x] 1.2 Add a `FluentAssertions` package reference to `tests/BS2BG.Tests/BS2BG.Tests.csproj`.
- [x] 1.3 Add test-only FluentAssertions setup, including `global using FluentAssertions;` and any required package-version configuration.

## 2. Assertion Migration

- [x] 2.1 Inventory current `Assert.*` usages in `tests/BS2BG.Tests/` and group them by assertion pattern.
- [x] 2.2 Convert equality, boolean, nullability, and collection-count assertions to behavior-equivalent FluentAssertions expressions.
- [x] 2.3 Convert collection predicate, string containment, ordering, and equivalency assertions while preserving existing comparison semantics.
- [x] 2.4 Convert type-cast and exception assertions to FluentAssertions forms that preserve exact type expectations.
- [x] 2.5 Review converted tests for readability and remove obsolete xUnit assertion-only using patterns.

## 3. Validation

- [x] 3.1 Run a focused test build for `tests/BS2BG.Tests/BS2BG.Tests.csproj`.
- [x] 3.2 Run the full repository test suite with the existing `dotnet test` workflow.
- [x] 3.3 Verify production projects do not reference FluentAssertions.
- [x] 3.4 Verify remaining xUnit usage is limited to test framework constructs or intentionally unconverted edge cases documented during implementation.
