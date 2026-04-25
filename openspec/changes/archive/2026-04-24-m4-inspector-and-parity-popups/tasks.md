## 1. SetSliders Inspector

- [x] 1.1 Add inspector ViewModel for selected preset set-slider rows
- [x] 1.2 Add enabled, min percent, max percent, and live preview editing
- [x] 1.3 Add 0/50/100 batch actions for all, min, and max values
- [x] 1.4 Verify inspector layout at the PRD minimum window size and add secondary-window fallback if needed

## 2. BoS JSON View

- [x] 2.1 Implement selected-preset BoS JSON ViewModel state using Core writer output
- [x] 2.2 Add resizable view surface and copy command
- [x] 2.3 Add snapshot tests for BoS JSON formatter edge cases

## 3. Image And Notifier Views

- [x] 3.1 Implement `images/` lookup by `Name (EditorId)` and name-only fallback
- [x] 3.2 Add image view window with always-on-top parity behavior
- [x] 3.3 Add no-preset notifier after morph generation for empty targets

## 4. Validation

- [x] 4.1 Add ViewModel tests for batch slider actions and preview updates
- [x] 4.2 Add UI smoke coverage for inspector editing, BoS view, image view, and notifier behavior
- [x] 4.3 Run `dotnet test`
