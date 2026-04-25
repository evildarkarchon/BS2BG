## 1. XML Import Core

- [x] 1.1 Implement BodySlide XML parser using `XDocument`
- [x] 1.2 Preserve sparse slider values and ignore unsupported attributes/children
- [x] 1.3 Add parser tests for optional XML declarations, special names, sparse sliders, and negative values

## 2. Templates ViewModels

- [x] 2.1 Add preset list state, selected preset state, and import command with busy handling
- [x] 2.2 Add rename, duplicate, remove, clear, profile selection, and validation logic
- [x] 2.3 Add omit-redundant preference behavior that clears generated template text on toggle

## 3. Template Rendering

- [x] 3.1 Implement Core template preview and generate-all services through the M0 formatter
- [x] 3.2 Bind live preview updates to selected preset, profile, slider, and omit state
- [x] 3.3 Add generate templates and copy generated text commands

## 4. Validation

- [x] 4.1 Add ViewModel tests for preset management and preview invalidation
- [x] 4.2 Add integration tests for Flow A fixture imports and generated template text
- [x] 4.3 Run `dotnet test`
