using BS2BG.App.ViewModels;
using Xunit;

namespace BS2BG.Tests;

public sealed class AsyncRelayCommandTests
{
    [Fact]
    public async Task ExecutePassesCancellableTokenThatCancelCancels()
    {
        var capturedToken = new TaskCompletionSource<CancellationToken>(TaskCreationOptions.RunContinuationsAsynchronously);
        var release = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var completed = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var command = new AsyncRelayCommand(async token =>
        {
            capturedToken.SetResult(token);
            await release.Task;
            completed.SetResult();
        });

        command.Execute(null);

        var token = await capturedToken.Task.WaitAsync(TimeSpan.FromSeconds(5), TestContext.Current.CancellationToken);
        token.CanBeCanceled.Should().BeTrue();

        command.Cancel();

        token.IsCancellationRequested.Should().BeTrue();
        release.SetResult();
        await completed.Task.WaitAsync(TimeSpan.FromSeconds(5), TestContext.Current.CancellationToken);
    }

    [Fact]
    public async Task ExecuteDoesNotReportExpectedCancellation()
    {
        var capturedToken = new TaskCompletionSource<CancellationToken>(TaskCreationOptions.RunContinuationsAsynchronously);
        var release = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var throwing = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var reported = new TaskCompletionSource<Exception>(TaskCreationOptions.RunContinuationsAsynchronously);
        var command = new AsyncRelayCommand(
            async token =>
            {
                capturedToken.SetResult(token);
                await release.Task;
                throwing.SetResult();
                throw new OperationCanceledException(token);
            },
            reportException: exception => reported.SetResult(exception));

        command.Execute(null);
        var token = await capturedToken.Task.WaitAsync(TimeSpan.FromSeconds(5), TestContext.Current.CancellationToken);

        command.Cancel();
        token.IsCancellationRequested.Should().BeTrue();
        release.SetResult();
        await throwing.Task.WaitAsync(TimeSpan.FromSeconds(5), TestContext.Current.CancellationToken);

        var reportWait = async () => await reported.Task.WaitAsync(
            TimeSpan.FromMilliseconds(100),
            TestContext.Current.CancellationToken);
        await reportWait.Should().ThrowAsync<TimeoutException>();
    }
}
