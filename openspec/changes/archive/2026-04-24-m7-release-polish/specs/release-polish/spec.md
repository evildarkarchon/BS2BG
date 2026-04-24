## ADDED Requirements

### Requirement: Accessibility audit is complete
The system SHALL provide keyboard navigation, focus order, automation names, and sufficient contrast for all primary workflows.

#### Scenario: Navigate without mouse
- **WHEN** a tester completes Flow A using keyboard input
- **THEN** every required command and field is reachable and has an understandable accessible name

### Requirement: Windows portable package is produced
The system SHALL publish a self-contained single-file Windows executable packaged in a portable zip layout without requiring an installer or JRE.

#### Scenario: Run published package
- **WHEN** the release zip is extracted on a clean supported Windows machine
- **THEN** the executable launches without installing additional runtimes

### Requirement: Release signing is handled
The system SHALL either sign release artifacts or document the expected unsigned-build warning and verification path.

#### Scenario: Build release artifact
- **WHEN** the release pipeline produces the Windows package
- **THEN** the artifact signing state and verification instructions are included with the release

### Requirement: Release notes and credits are shipped
The system SHALL include release notes, source credits, parity notes, and known limitations for v1.0.0 users.

#### Scenario: Review release package docs
- **WHEN** a user opens the packaged documentation
- **THEN** the original author credit, port author credit, and v1.0.0 known limitations are visible

### Requirement: Launch performance and QA gates pass
The system SHALL meet the PRD launch target and complete the Windows/DPI/theme manual QA matrix before release.

#### Scenario: Cold launch on target machine
- **WHEN** the packaged app starts on a mid-range Windows 11 laptop
- **THEN** the main window is usable in under 1.5 seconds
