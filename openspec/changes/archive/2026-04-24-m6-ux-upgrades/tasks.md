## 1. Search, Drop, And Palette

- [x] 1.1 Add global search box and `Ctrl+F` focus behavior for active surfaces
- [x] 1.2 Add drag-and-drop handling for XML, `.jbs2bg`, and NPC text files through existing services
- [x] 1.3 Add command palette populated from command metadata and bound to `Ctrl+Shift+P`

## 2. Assignment Productivity

- [x] 2.1 Add NPC multiselect state and assignment commands
- [x] 2.2 Add selected-row clear assignment workflow
- [x] 2.3 Add actionable preset-count warning and trim-to-76 command

## 3. Undo Redo

- [x] 3.1 Add ViewModel-level undo/redo stack and command bindings
- [x] 3.2 Wrap add, remove, rename, and assignment mutations in undoable operations
- [x] 3.3 Add tests for undo and redo across references

## 4. Filters And Themes

- [x] 4.1 Upgrade NPC filtering to per-column checklist predicates with column search
- [x] 4.2 Add inline custom-target validation state and examples
- [x] 4.3 Add dark, light, and system theme selection persisted in user preferences

## 5. Validation

- [x] 5.1 Add ViewModel tests for search, DnD routing, palette commands, multiselect, filters, and themes
- [x] 5.2 Add UI smoke tests for the upgraded workflows
- [x] 5.3 Run `dotnet test`
