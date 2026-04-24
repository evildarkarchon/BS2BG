using System.Diagnostics;
using System.Windows.Input;

namespace BS2BG.App.ViewModels;

public sealed class AsyncRelayCommand : ICommand
{
    private readonly Func<CancellationToken, Task> executeAsync;
    private readonly Func<bool>? canExecute;
    private readonly Action<Exception>? reportException;

    public AsyncRelayCommand(
        Func<CancellationToken, Task> executeAsync,
        Func<bool>? canExecute = null,
        Action<Exception>? reportException = null)
    {
        this.executeAsync = executeAsync ?? throw new ArgumentNullException(nameof(executeAsync));
        this.canExecute = canExecute;
        this.reportException = reportException;
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
            try
            {
                await executeAsync(CancellationToken.None);
            }
            catch (Exception ex)
            {
                ReportException(ex);
            }
        }
    }

    public void RaiseCanExecuteChanged()
    {
        CanExecuteChanged?.Invoke(this, EventArgs.Empty);
    }

    private void ReportException(Exception exception)
    {
        Trace.TraceError(exception.ToString());

        if (reportException is null)
        {
            return;
        }

        try
        {
            reportException(exception);
        }
        catch (Exception reportFailure)
        {
            Trace.TraceError(reportFailure.ToString());
        }
    }
}
