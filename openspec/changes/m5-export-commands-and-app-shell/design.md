## Context

M5 closes v1 feature parity by wiring the app shell commands, prompts, keybinds, exports, and About dialog after core generation flows exist.

## Goals / Non-Goals

**Goals:**
- Implement File and Help menu commands with required shortcuts.
- Add dirty tracking and unsaved-change prompts.
- Export BodyGen INIs and BoS JSON files to selected destinations.
- Complete parity checklist coverage and smoke tests.

**Non-Goals:**
- No command palette, DnD, undo/redo, or advanced UX upgrades.
- No packaging/signing release work.

## Decisions

- Wrap Avalonia 12 storage-provider APIs behind `IFileDialogService` so ViewModels remain testable.
- Centralize dirty tracking in the root project ViewModel and mark save/export operations explicitly.
- Keep export writers in Core; App services only choose folders and report user-facing notifications.
- Route keybindings to ReactiveCommands so menu, toolbar, and keyboard paths share command logic.

## Risks / Trade-offs

- Avalonia 12 file picker APIs differ from older examples -> rely on `StorageProvider` and cover with app-level abstractions.
- Export filename sanitization can surprise users -> sanitize only filesystem paths and keep internal preset names untouched.
- Prompt flows are easy to bypass from keyboard shortcuts -> test menu and keybinding paths.

## Migration Plan

No schema migration. Projects saved in earlier milestones continue to load; exports are generated from current in-memory state.

## Open Questions

- Confirm final Save As shortcut, since the PRD table mentions `Ctrl+Alt+S` while the note says keep Shift from Java behavior.
