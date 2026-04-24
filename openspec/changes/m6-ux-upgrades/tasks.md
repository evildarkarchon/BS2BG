## 1. Search, Drop, And Palette

- [ ] 1.1 Add global search box and `Ctrl+F` focus behavior for active surfaces
- [ ] 1.2 Add drag-and-drop handling for XML, `.jbs2bg`, and NPC text files through existing services
- [ ] 1.3 Add command palette populated from command metadata and bound to `Ctrl+Shift+P`

## 2. Assignment Productivity

- [ ] 2.1 Add NPC multiselect state and assignment commands
- [ ] 2.2 Add selected-row clear assignment workflow
- [ ] 2.3 Add actionable preset-count warning and trim-to-76 command

## 3. Undo Redo

- [ ] 3.1 Add ViewModel-level undo/redo stack and command bindings
- [ ] 3.2 Wrap add, remove, rename, and assignment mutations in undoable operations
- [ ] 3.3 Add tests for undo and redo across references

## 4. Filters And Themes

- [ ] 4.1 Upgrade NPC filtering to per-column checklist predicates with column search
- [ ] 4.2 Add inline custom-target validation state and examples
- [ ] 4.3 Add dark, light, and system theme selection persisted in user preferences

## 5. Validation

- [ ] 5.1 Add ViewModel tests for search, DnD routing, palette commands, multiselect, filters, and themes
- [ ] 5.2 Add UI smoke tests for the upgraded workflows
- [ ] 5.3 Run `dotnet test`
