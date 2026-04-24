# test-assertion-readability Specification

## Purpose
TBD - created by archiving change migrate-tests-to-fluentassertions. Update Purpose after archive.
## Requirements
### Requirement: Test project uses FluentAssertions for assertions
The C# test project SHALL reference and configure FluentAssertions so test files can express expectations with FluentAssertions assertion APIs.

#### Scenario: FluentAssertions dependency is available to tests
- **WHEN** the `BS2BG.Tests` project is restored and built
- **THEN** FluentAssertions assertion extension methods are available to test code
- **AND** the FluentAssertions package version is managed through the repo's centralized package-version file

### Requirement: Test assertions use readable FluentAssertions syntax
The C# test suite SHALL use FluentAssertions for assertions where an existing xUnit assertion is being converted, while preserving xUnit for test discovery, execution, and attributes.

#### Scenario: Converted assertions preserve existing intent
- **WHEN** an existing xUnit assertion is converted
- **THEN** the FluentAssertions expression preserves the original expected value, actual value, ordering requirement, nullability requirement, type requirement, string-comparison behavior, and exception-type requirement

#### Scenario: xUnit remains the test framework
- **WHEN** the converted tests are inspected
- **THEN** xUnit test attributes and runner dependencies remain in use
- **AND** xUnit assertion calls are not used for expectations that have been migrated to FluentAssertions

### Requirement: Assertion migration remains behavior-equivalent
The assertion migration SHALL keep existing test coverage and production behavior unchanged.

#### Scenario: Converted suite passes
- **WHEN** the repository test command is run after the migration
- **THEN** the converted test suite passes without requiring production behavior changes

#### Scenario: Runtime assemblies avoid assertion dependency
- **WHEN** production projects are inspected after the migration
- **THEN** FluentAssertions is not referenced by runtime app or core projects

