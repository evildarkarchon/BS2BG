## Purpose

Define the M0 C# / Avalonia foundation for BS2BG: solution topology, app shell, Core slider formatter parity, and fixture-gated validation.

## Requirements

### Requirement: Solution projects are scaffolded
The system SHALL provide a buildable `BS2BG.sln` containing `BS2BG.Core`, `BS2BG.App`, and `BS2BG.Tests` with the target frameworks and package families defined by the PRD.

#### Scenario: Build solution after scaffold
- **WHEN** a developer restores and builds the solution
- **THEN** Core targets `netstandard2.1`, App and Tests target `net10.0`, and the solution builds without placeholder compile errors

### Requirement: Avalonia app shell starts
The system SHALL include an Avalonia 12 app shell with dependency injection, ReactiveUI wiring, Fluent theme baseline, and an empty main window titled for Bodyslide to Bodygen.

#### Scenario: Launch empty app shell
- **WHEN** the app starts
- **THEN** the main window opens at the PRD startup size with the configured theme and resolves its ViewModel through DI

### Requirement: Core slider formatting matches Java
The system SHALL implement slider math with invert, multiplier, percent interpolation, half-up rounding, and the two Java-compatible output formatters described by the PRD.

#### Scenario: Format text and BoS numbers differently
- **WHEN** a rounded slider value is an integer-valued float
- **THEN** the text formatter keeps `.0` while the BoS JSON number formatter omits `.0`

### Requirement: Golden fixture corpus gates implementation
The system SHALL include Java-generated fixture inputs and expected outputs before C# implementation work is considered complete for M0.

#### Scenario: Run golden tests
- **WHEN** `dotnet test` runs after M0
- **THEN** slider math and formatter tests compare against the committed Java reference outputs and pass
