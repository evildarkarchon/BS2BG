## 1. Dependency Setup

- [ ] 1.1 Add a centrally managed `FluentAssertions` package version in `Directory.Packages.props`.
- [ ] 1.2 Add a `FluentAssertions` package reference to `tests/BS2BG.Tests/BS2BG.Tests.csproj`.
- [ ] 1.3 Add test-only FluentAssertions setup, including `global using FluentAssertions;` and any required package-version configuration.

## 2. Assertion Migration

- [ ] 2.1 Inventory current `Assert.*` usages in `tests/BS2BG.Tests/` and group them by assertion pattern.
- [ ] 2.2 Convert equality, boolean, nullability, and collection-count assertions to behavior-equivalent FluentAssertions expressions.
- [ ] 2.3 Convert collection predicate, string containment, ordering, and equivalency assertions while preserving existing comparison semantics.
- [ ] 2.4 Convert type-cast and exception assertions to FluentAssertions forms that preserve exact type expectations.
- [ ] 2.5 Review converted tests for readability and remove obsolete xUnit assertion-only using patterns.

## 3. Validation

- [ ] 3.1 Run a focused test build for `tests/BS2BG.Tests/BS2BG.Tests.csproj`.
- [ ] 3.2 Run the full repository test suite with the existing `dotnet test` workflow.
- [ ] 3.3 Verify production projects do not reference FluentAssertions.
- [ ] 3.4 Verify remaining xUnit usage is limited to test framework constructs or intentionally unconverted edge cases documented during implementation.
