## Why

The App layer drifted away from the ReactiveUI MVVM patterns that PRD §6 explicitly mandates. ViewModels currently use a custom `RelayCommand`/`AsyncRelayCommand` pair instead of `ReactiveCommand`, properties are hand-written instead of `[Reactive]`, and `canExecute` / derived state is wired imperatively rather than through `WhenAnyValue` observables. This breaks the reactive contract the PRD assumed for undo/redo (§5), busy-state aggregation (§6), and command availability gating, and leaves new ViewModel work without a clear pattern to follow. Restoring these patterns now — before more ViewModels accumulate — keeps the architecture honest and unblocks the reactive features still on the roadmap.

## What Changes

- **BREAKING (internal API)**: Replace `BS2BG.App/ViewModels/RelayCommand.cs`, `RelayCommandOfT.cs`, and `AsyncRelayCommand.cs` with `ReactiveCommand<TParam, TResult>` (sync) and `ReactiveCommand.CreateFromTask(...)` (async). Delete the custom command classes once no callers remain.
- Convert ViewModel properties (`MainWindowViewModel`, `TemplatesViewModel`, `MorphsViewModel`, `SetSliderInspectorRowViewModel`) from manual `RaiseAndSetIfChanged` setters to `[Reactive]` auto-properties (via `ReactiveUI.SourceGenerators` — see Decision 3 in `design.md` for why this superseded `ReactiveUI.Fody`).
- Express `canExecute` declaratively with `this.WhenAnyValue(...)` observables instead of `Func<bool>` callbacks; route the result into `ReactiveCommand`'s `canExecute` parameter.
- Express derived state (e.g. aggregate `IsBusy`, command-availability flags, validation states) through `WhenAnyValue(...).ToProperty(...)` / `Observable.CombineLatest(...).ToProperty(...)` so the reactive graph drives UI state instead of imperative property setters.
- Move long-running command bodies onto `RxApp.TaskpoolScheduler` and marshal results back via `RxApp.MainThreadScheduler`; remove any ad-hoc threading shortcuts that bypass the scheduler.
- Aggregate child-VM busy state and command cancellation (introduced in commits `f6b7d17` and `7ae8005`) through observable composition rather than the current imperative aggregation, preserving behavior.
- Add `ReactiveUI.SourceGenerators` to `Directory.Packages.props` and reference it from `BS2BG.App` (the PRD names `ReactiveUI.Fody`, but Fody never tracked past ReactiveUI 19.x and the Avalonia integration we already use depends on ReactiveUI 23.x; the source generator is the modern, version-compatible substitute and exposes the same `[Reactive]` attribute surface).
- Update `CLAUDE.md` "Target stack — actual usage" section to re-align documented usage with the restored patterns.
- **Out of scope**: Window base class. `MainWindow` stays as a plain Avalonia `Window` — moving back to `ReactiveWindow<T>` is explicitly excluded per the user's direction and current Avalonia guidance.

## Capabilities

### New Capabilities
- `reactive-mvvm-conventions`: Formalizes the ReactiveUI command, property, and scheduling patterns that App-layer ViewModels must follow. Captures the contract previously implied only by PRD §6 so future ViewModel work has a verifiable spec rather than drifting again.

### Modified Capabilities
<!-- None. No user-visible behavior changes; existing capability specs (template-generation-flow, morph-assignment-flow, inspector-parity-views, export-commands-app-shell, ux-upgrades) keep their current requirements unchanged. -->

## Impact

- **Affected projects**: `src/BS2BG.App` (ViewModels, command wiring, possibly Views if any code-behind relied on the custom command shape).
- **Affected code**: `MainWindowViewModel`, `TemplatesViewModel`, `MorphsViewModel`, `SetSliderInspectorRowViewModel`; deletes `RelayCommand.cs`, `RelayCommandOfT.cs`, `AsyncRelayCommand.cs`.
- **Affected tests**: `MainWindowViewModelTests`, `TemplatesViewModelTests`, `MorphsViewModelTests`, `SetSliderInspectorViewModelTests` — call sites that exercised commands directly will need to adapt to `ReactiveCommand.Execute().Subscribe()` / `await command.Execute()` style. Behavior coverage is unchanged.
- **Dependencies**: Adds `ReactiveUI.SourceGenerators` to centralized package versions. `ReactiveUI.Avalonia` (which transitively brings in `ReactiveUI` 23.2.1) is already referenced.
- **Docs**: Updates `CLAUDE.md` to retire the "ReactiveUI usage is minimal in practice" note and document the restored patterns.
- **No changes to**: Core (no ReactiveUI dependency), AXAML files (compiled bindings already in place), golden-file outputs, Window base class, or any user-visible behavior.
