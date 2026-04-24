## 1. Core Morph Services

- [ ] 1.1 Implement custom morph target model validation and assignment helpers
- [ ] 1.2 Implement NPC text import with BOM, UTF-8, and fallback decoding
- [ ] 1.3 Implement case-insensitive NPC de-dupe by `(mod, editorId)`
- [ ] 1.4 Implement morph output writer for NPC and custom target lines

## 2. Morphs ViewModels

- [ ] 2.1 Add NPC table state, custom target state, selected target panel, and count badge state
- [ ] 2.2 Add Add, Add All, Remove, Clear, Fill Empty, Clear Assignments, and Remove NPC commands
- [ ] 2.3 Add search-only basic filtering and visible-row scope for bulk operations
- [ ] 2.4 Add injectable random assignment provider for deterministic tests

## 3. UI

- [ ] 3.1 Add Morphs workspace controls for custom targets, NPC table, and assignment panel
- [ ] 3.2 Add import NPC popup flow and empty-target handling
- [ ] 3.3 Add generated morphs text and copy command wiring

## 4. Validation

- [ ] 4.1 Add Core tests for NPC parsing, encoding fallback, de-dupe, and morph lines
- [ ] 4.2 Add ViewModel tests for filtered bulk operations and assignment commands
- [ ] 4.3 Run `dotnet test`
