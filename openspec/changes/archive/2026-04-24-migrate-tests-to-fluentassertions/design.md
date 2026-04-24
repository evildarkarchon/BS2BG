## Context

`BS2BG.Tests` is an xUnit v3 test project targeting `net10.0`. It currently uses centralized package versions in `Directory.Packages.props` and contains many direct `Assert.*` calls across unit, service, view-model, and Avalonia headless tests. This change is cross-cutting because it touches most test files and introduces a new test-only dependency, but it should not affect production assemblies or app behavior.

## Goals / Non-Goals

**Goals:**
- Make test expectations easier to read by using FluentAssertions assertion chains.
- Add FluentAssertions through the existing centralized package-management pattern.
- Keep xUnit v3 as the test framework, runner, and source of test attributes.
- Preserve current test semantics, fixtures, and behavior coverage.
- Validate the converted suite with the existing `dotnet test` workflow.

**Non-Goals:**
- No production code changes except where a test conversion exposes an existing compile issue.
- No new behavior coverage beyond narrowly necessary migration safety checks.
- No test framework migration away from xUnit.
- No broad fixture, naming, or test-organization refactor.

## Decisions

- Use FluentAssertions only in the test project.
  - Rationale: The readability problem is limited to tests, and FluentAssertions should not become a production dependency.
  - Alternative considered: Add FluentAssertions at solution scope. Rejected because only `BS2BG.Tests` needs it.

- Manage the FluentAssertions version centrally.
  - Rationale: The repo already uses `ManagePackageVersionsCentrally`, so `Directory.Packages.props` should remain the single package-version source.
  - Alternative considered: Put a version directly on the test project package reference. Rejected because it would break the local dependency-management pattern.

- Prefer a small test setup file for shared FluentAssertions configuration.
  - Rationale: A single setup file can hold `global using FluentAssertions;` and any version-required license/configuration call without repeating it throughout tests.
  - Alternative considered: Add `using FluentAssertions;` in every test file. Rejected because this adds churn and makes future setup harder to maintain.

- Convert assertions by preserving assertion intent, not by doing a blind token rewrite.
  - Rationale: xUnit and FluentAssertions APIs differ for type checks, exception checks, collection predicates, and string comparisons. The migration should choose equivalent FluentAssertions forms such as `.Be(...)`, `.BeEquivalentTo(...)`, `.Contain(...)`, `.ContainEquivalentOf(...)`, `.BeOfType<T>().Which`, `.ThrowExactly<T>()`, and `.ThrowExactlyAsync<T>()` as appropriate.
  - Alternative considered: Apply a mechanical regex replacement. Rejected because it risks weakening assertion semantics or producing non-compiling code.

## Risks / Trade-offs

- [Risk] Broad assertion conversion can accidentally change equality semantics. -> Mitigation: Preserve exact ordering, type, nullability, case sensitivity, and exception-type expectations while converting each assertion family.
- [Risk] FluentAssertions package versions may require explicit license acceptance or setup. -> Mitigation: Keep any required setup in test-only configuration and verify with a clean `dotnet test`.
- [Risk] Existing dirty test files can conflict with migration edits. -> Mitigation: Apply changes on top of the current worktree without reverting unrelated edits.
- [Risk] A full-suite run may surface an existing flaky or timing-sensitive test. -> Mitigation: Reproduce focused failures, rerun the failing test, then rerun the suite before treating it as a migration regression.
