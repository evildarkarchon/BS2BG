## ADDED Requirements

### Requirement: SetSliders editor is available in the inspector
The system SHALL provide enabled state, min/max percent sliders, live preview, and 0/50/100 batch operations for the selected preset's sliders.

#### Scenario: Batch set maximum percentages
- **WHEN** the user applies the `100 Max` batch action
- **THEN** every editable set slider has its maximum percentage set to 100 and the preview updates

### Requirement: BoS JSON view matches Core output
The system SHALL show the selected preset's BoS JSON using the minimal-json-compatible number formatter and SHALL support copying it.

#### Scenario: View selected preset BoS JSON
- **WHEN** a preset is selected and the BoS view is opened
- **THEN** the JSON text matches the Core BoS writer output for that preset

### Requirement: Image view uses v1 lookup convention
The system SHALL look for images under `images/` by `Name (EditorId)` first and by `Name` second using supported image extensions.

#### Scenario: NPC-specific image exists
- **WHEN** `images/<Name> (<EditorId>).png` exists for the selected NPC
- **THEN** the image view displays that file before considering the name-only fallback

### Requirement: No-preset notifier reports empty outputs
The system SHALL show an always-on-top no-preset notifier after morph generation when any generated target lacks preset assignments.

#### Scenario: Generate morphs with empty target
- **WHEN** morph generation finishes and at least one target has no assigned presets
- **THEN** the no-preset notifier lists the affected targets
