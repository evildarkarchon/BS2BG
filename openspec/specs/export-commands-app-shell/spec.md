# export-commands-app-shell Specification

## Purpose
TBD - created by archiving change m5-export-commands-and-app-shell. Update Purpose after archive.
## Requirements
### Requirement: App shell exposes v1 menu commands
The system SHALL expose New, Open, Save, Save As, Export BoS JSON, Export BodyGen INIs, and About commands with the PRD keybindings and availability rules.

#### Scenario: Save command without current path
- **WHEN** Save is invoked for a project without a current file path
- **THEN** the app prompts for a save path and writes the project through `ProjectFileService`

### Requirement: Dirty projects prompt before destructive actions
The system SHALL confirm before New or Open replaces a project with unsaved changes.

#### Scenario: Cancel open with dirty project
- **WHEN** the current project is dirty and the user cancels the Open confirmation
- **THEN** the current project remains loaded and unchanged

### Requirement: BodyGen INI export writes compatible files
The system SHALL export `templates.ini` and `morphs.ini` to a chosen folder using Java-compatible content and CRLF INI line endings.

#### Scenario: Export BodyGen INIs
- **WHEN** the user chooses a destination folder and confirms export
- **THEN** both INI files are written with the expected generated template and morph content

### Requirement: BoS JSON export writes one file per preset
The system SHALL export one BoS JSON file per preset to a chosen folder and SHALL sanitize Windows-reserved filename characters while preserving in-memory preset names.

#### Scenario: Export preset with reserved filename characters
- **WHEN** a preset name contains a Windows-reserved filename character
- **THEN** the exported JSON filename is sanitized and the JSON body keeps the original preset name

### Requirement: Preview and commit share one authoritative artifact plan
The system SHALL freeze ordered, commit-group-relative paths and exact output bytes once per preview-confirm-commit operation. Preview, direct export, headless automation, and portable bundles SHALL consume those frozen artifacts without regenerating names or content.

#### Scenario: Project state changes after BoS planning
- **WHEN** a BoS export plan is created and the live project changes before filesystem commit
- **THEN** the committed JSON bytes match the already-previewed plan
- **AND** a later export operation creates a fresh plan from the newer project state

#### Scenario: All-output automation fails during BoS commit
- **WHEN** the BodyGen commit group succeeds and the later BoS commit group fails
- **THEN** the BodyGen pair remains committed
- **AND** the BoS batch rolls back independently

### Requirement: About dialog credits authors
The system SHALL credit Totiman / asdasfa as original jBS2BG author and evildarkarchon as port author in the About dialog.

#### Scenario: Open About dialog
- **WHEN** the user invokes Help About
- **THEN** the dialog shows the required credits and app name
