## ADDED Requirements

### Requirement: Custom morph targets are managed
The system SHALL allow users to add, remove, clear, and assign presets to custom morph targets named with the v1 `Context|Gender[|Race[Variant]]` format.

#### Scenario: Add custom target with presets available
- **WHEN** a valid custom target is added while presets exist
- **THEN** the target is created and receives a random preset assignment matching v1 behavior

### Requirement: NPC imports parse v1 text dumps
The system SHALL import NPC text files containing `Mod|Name|EditorID|Race|FormID`, detect BOM/UTF-8/fallback encodings, and de-dupe by `(mod, editorId)` case-insensitively.

#### Scenario: Import duplicate NPC rows
- **WHEN** an NPC import file contains two rows with the same mod and editor ID using different casing
- **THEN** only one NPC record is retained

### Requirement: Morph assignment flows match v1
The system SHALL support Add, Add All, Remove, Clear, Fill Empty, Clear Assignments, and Remove NPC behaviors for visible targets and NPCs.

#### Scenario: Fill visible empty NPCs
- **WHEN** at least one visible filtered NPC has no preset assignments and Fill Empty is confirmed
- **THEN** each visible empty NPC receives the requested random preset assignments

### Requirement: Morph generation emits BodyGen lines
The system SHALL generate morph output for NPCs and custom targets using Java-compatible line formats, sort behavior, and preset separators.

#### Scenario: Generate morphs for assigned NPCs and targets
- **WHEN** NPCs and custom targets have assigned presets
- **THEN** morph text contains `mod|formId=...` lines for NPCs and `name=...` lines for custom targets

### Requirement: Basic filter scopes visible NPC operations
The system SHALL provide a search-only basic filter for M3 and SHALL apply visible-row scope to Fill Empty and Clear Assignments.

#### Scenario: Clear assignments with filter active
- **WHEN** a filter hides some NPC rows and Clear Assignments is confirmed
- **THEN** assignments are cleared only from visible NPCs
