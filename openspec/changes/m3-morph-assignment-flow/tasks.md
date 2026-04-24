## 1. Core Morph Services

- [x] 1.1 Implement custom morph target model validation and assignment helpers
- [x] 1.2 Implement NPC text import with BOM, UTF-8, and fallback decoding
- [x] 1.3 Implement case-insensitive NPC de-dupe by `(mod, editorId)`
- [x] 1.4 Implement morph output writer for NPC and custom target lines

## 2. Morphs ViewModels

- [x] 2.1 Add NPC table state, custom target state, selected target panel, and count badge state
- [x] 2.2 Add Add, Add All, Remove, Clear, Fill Empty, Clear Assignments, and Remove NPC commands
- [x] 2.3 Add search-only basic filtering and visible-row scope for bulk operations
- [x] 2.4 Add injectable random assignment provider for deterministic tests

## 3. UI

- [x] 3.1 Add Morphs workspace controls for custom targets, NPC table, and assignment panel
- [x] 3.2 Add import NPC popup flow and empty-target handling
- [x] 3.3 Add generated morphs text and copy command wiring

## 4. Validation

- [x] 4.1 Add Core tests for NPC parsing, encoding fallback, de-dupe, and morph lines
- [x] 4.2 Add ViewModel tests for filtered bulk operations and assignment commands
- [x] 4.3 Run `dotnet test`
