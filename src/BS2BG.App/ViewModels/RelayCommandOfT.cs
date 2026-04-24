using System.Windows.Input;

namespace BS2BG.App.ViewModels;

public sealed class RelayCommand<T>(Action<T?> execute, Func<T?, bool>? canExecute = null) : ICommand
{
    private readonly Func<T?, bool>? canExecute = canExecute;
    private readonly Action<T?> execute = execute ?? throw new ArgumentNullException(nameof(execute));

    public event EventHandler? CanExecuteChanged;

    public bool CanExecute(object? parameter)
    {
        return parameter is T value
            ? canExecute?.Invoke(value) ?? true
            : parameter is null && (canExecute?.Invoke(default) ?? true);
    }

    public void Execute(object? parameter)
    {
        if (!CanExecute(parameter)) return;

        execute(parameter is T value ? value : default);
    }

    public void RaiseCanExecuteChanged() => CanExecuteChanged?.Invoke(this, EventArgs.Empty);
}
