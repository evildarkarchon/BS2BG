using System.Windows.Input;

namespace BS2BG.App.ViewModels;

public sealed class AsyncRelayCommand : ICommand
{
    private readonly Func<CancellationToken, Task> executeAsync;
    private readonly Func<bool>? canExecute;

    public AsyncRelayCommand(Func<CancellationToken, Task> executeAsync, Func<bool>? canExecute = null)
    {
        this.executeAsync = executeAsync ?? throw new ArgumentNullException(nameof(executeAsync));
        this.canExecute = canExecute;
    }

    public event EventHandler? CanExecuteChanged;

    public bool CanExecute(object? parameter)
    {
        return canExecute?.Invoke() ?? true;
    }

    public async void Execute(object? parameter)
    {
        if (CanExecute(parameter))
        {
            await executeAsync(CancellationToken.None);
        }
    }

    public void RaiseCanExecuteChanged()
    {
        CanExecuteChanged?.Invoke(this, EventArgs.Empty);
    }
}
