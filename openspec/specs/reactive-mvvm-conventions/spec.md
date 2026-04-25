# reactive-mvvm-conventions Specification

## Purpose

Define the ReactiveUI-based MVVM conventions that the BS2BG App layer (`src/BS2BG.App/`) follows. This capability codifies the contract restored by the `restore-reactiveui-patterns` change: ViewModels use `ReactiveObject` + `[Reactive]` for INPC, commands are `ReactiveCommand` instances with observable `canExecute` and cooperative cancellation, derived state flows through the reactive graph via `ToProperty`, background work uses ReactiveUI schedulers, and Windows remain plain `Avalonia.Controls.Window` instances. These conventions exist so that App-layer ViewModels stay testable, free of imperative thread marshalling, and consistent with the wider Avalonia 12 / ReactiveUI 23.x ecosystem.

## Requirements

### Requirement: App-layer commands are ReactiveCommands
All commands exposed by App-layer ViewModels SHALL be instances of `ReactiveUI.ReactiveCommand<TParam, TResult>` (or its non-generic alias) created via the `ReactiveCommand.Create*` static factories. Custom `ICommand` implementations SHALL NOT exist in `BS2BG.App` for the purpose of binding ViewModel actions to the View.

#### Scenario: No custom command types remain in the App ViewModels
- **WHEN** the source under `src/BS2BG.App/` is searched for `class RelayCommand`, `class AsyncRelayCommand`, or any other custom `ICommand` implementation
- **THEN** no matches are found

#### Scenario: ViewModel commands resolve to ReactiveCommand at runtime
- **WHEN** any public command property of `MainWindowViewModel`, `TemplatesViewModel`, `MorphsViewModel`, or `SetSliderInspectorRowViewModel` is inspected at runtime
- **THEN** its concrete type is assignable to `ReactiveUI.ReactiveCommandBase`

### Requirement: Async commands provide cooperative cancellation via CancellationToken
Long-running command bodies SHALL be created via `ReactiveCommand.CreateFromTask((CancellationToken) => Task)` (or a `CreateFromObservable` equivalent that propagates cancellation), so that disposing the command's invocation subscription cancels the in-flight task body.

#### Scenario: Disposing an in-flight async command invocation cancels its task
- **WHEN** an async `ReactiveCommand` is invoked and the returned subscription is disposed before the task completes
- **THEN** the `CancellationToken` passed to the task body is observed as cancelled and the task ends without producing a result

### Requirement: Command availability is derived from observable state
Each `ReactiveCommand`'s `canExecute` SHALL be supplied as an `IObservable<bool>` derived from `this.WhenAnyValue(...)` (or `Observable.CombineLatest(...)`), not as a `Func<bool>` callback that requires manual re-evaluation.

#### Scenario: Property change updates CanExecute without manual re-raise
- **GIVEN** a `ReactiveCommand` whose `canExecute` is `this.WhenAnyValue(x => x.SomeFlag, flag => flag)`
- **WHEN** `SomeFlag` is set to a new value
- **THEN** the command's `CanExecute` observable emits the new value without any explicit `RaiseCanExecuteChanged()` call

### Requirement: Notifying properties use the [Reactive] attribute
Notifying properties on App-layer ViewModels SHALL use the `[Reactive]` attribute provided by `ReactiveUI.SourceGenerators` for INPC backing. Hand-written `RaiseAndSetIfChanged` setters SHALL NOT be used for new properties, and existing ones SHALL be replaced as part of this change.

#### Scenario: ReactiveUI.SourceGenerators is referenced from BS2BG.App
- **WHEN** `Directory.Packages.props` and `BS2BG.App.csproj` are inspected
- **THEN** `ReactiveUI.SourceGenerators` appears as a centrally-versioned package and is referenced by `BS2BG.App`

#### Scenario: Assigning a [Reactive] property raises PropertyChanged
- **WHEN** a `[Reactive]` property on an App ViewModel is assigned a new value
- **THEN** the ViewModel raises a single `PropertyChanged` event whose `PropertyName` matches the property's name

### Requirement: Derived state flows through the reactive graph
Derived or aggregate properties (for example, an aggregate `IsBusy` over child ViewModels and async commands) SHALL be produced by `WhenAnyValue(...).ToProperty(...)` or `Observable.CombineLatest(...).ToProperty(...)` and exposed as read-only properties backed by `ObservableAsPropertyHelper<T>`. Imperative aggregation (subscribing to child events and assigning a backing field) SHALL NOT be used for derived state.

#### Scenario: Aggregate IsBusy reflects child busy state without imperative refresh
- **GIVEN** `MainWindowViewModel.IsBusy` is computed from `TemplatesViewModel.IsBusy`, `MorphsViewModel.IsBusy`, and any in-flight async commands
- **WHEN** any input becomes `true`
- **THEN** `MainWindowViewModel.IsBusy` becomes `true` and `PropertyChanged` is raised, with no imperative aggregation code in the setter

### Requirement: Background work uses ReactiveUI schedulers
Command bodies that perform long-running CPU- or I/O-bound work SHALL execute on `RxApp.TaskpoolScheduler` and SHALL marshal results to the UI via `RxApp.MainThreadScheduler`. ViewModels SHALL NOT call `Avalonia.Threading.Dispatcher.UIThread.InvokeAsync` (or equivalent) to push state changes onto the UI thread.

#### Scenario: ViewModels do not invoke Dispatcher.UIThread directly
- **WHEN** the source under `src/BS2BG.App/ViewModels/` is searched for `Dispatcher.UIThread.InvokeAsync` or `Dispatcher.UIThread.Post`
- **THEN** no matches are found

### Requirement: Application Windows remain plain Avalonia Windows
Despite the ReactiveUI patterns governing ViewModels, application Windows SHALL inherit from `Avalonia.Controls.Window` rather than `ReactiveUI.ReactiveWindow<TViewModel>`. This preserves the migration captured in commit `2a0fb46` and aligns with current Avalonia 12 guidance.

#### Scenario: MainWindow inherits from plain Avalonia Window
- **WHEN** `src/BS2BG.App/Views/MainWindow.axaml.cs` is inspected
- **THEN** `MainWindow` inherits from `Avalonia.Controls.Window` and not from `ReactiveUI.ReactiveWindow<...>`
