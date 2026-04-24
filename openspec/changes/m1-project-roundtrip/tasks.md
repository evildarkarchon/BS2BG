## 1. Domain Model

- [ ] 1.1 Add Core models for project, slider presets, set sliders, profiles, custom targets, and NPCs
- [ ] 1.2 Add collection/reference helpers for preset lookup, stale-reference removal, and dirty-state triggers
- [ ] 1.3 Add legacy profile mapping for `isUUNP` and named `Profile`

## 2. Serialization

- [ ] 2.1 Implement DTOs and `System.Text.Json` options for v1 `.jbs2bg` root objects and property ordering
- [ ] 2.2 Implement project load with null set-slider handling and stale assignment dropping
- [ ] 2.3 Implement project save with missing-default omission and both `isUUNP` and `Profile` emitted

## 3. Tests

- [ ] 3.1 Add v1 fixture project files covering presets, targets, NPCs, missing defaults, and stale references
- [ ] 3.2 Add load assertions for every supported field and assignment list
- [ ] 3.3 Add immediate re-save round-trip tests against expected output
- [ ] 3.4 Run `dotnet test`
