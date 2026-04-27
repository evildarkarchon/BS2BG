using BS2BG.App.Services;
using BS2BG.Core.Diagnostics;
using Xunit;

namespace BS2BG.Tests;

public sealed class MainWindowViewModelProfileRecoveryTests
{
    /// <summary>
    /// Verifies profile-conflict decisions can be faked for ViewModel tests without Avalonia UI coupling.
    /// </summary>
    [Fact]
    public async Task FakeDialogReturnsEveryProfileConflictDecisionAndCancel()
    {
        var decisions = new ProfileConflictDecision?[]
        {
            new(ProfileConflictResolution.UseProjectCopy, null),
            new(ProfileConflictResolution.ReplaceLocalProfile, null),
            new(ProfileConflictResolution.RenameProjectCopy, "Project Copy"),
            new(ProfileConflictResolution.KeepLocalProfile, null),
            null,
        };
        var dialog = new FakeAppDialogService(decisions);
        var request = new ProfileConflictRequest(
            "Shared Body",
            "Local custom profile from C:/profiles/shared.json",
            "Embedded project profile from shared project");

        foreach (var expected in decisions)
        {
            var actual = await dialog.PromptProfileConflictAsync(request, TestContext.Current.CancellationToken);

            actual.Should().Be(expected);
        }

        dialog.ProfileConflictRequests.Should().HaveCount(5);
        dialog.ProfileConflictRequests.Should().OnlyContain(item => item.ProfileName == "Shared Body");
    }

    private sealed class FakeAppDialogService(IEnumerable<ProfileConflictDecision?> conflictDecisions) : IAppDialogService
    {
        private readonly Queue<ProfileConflictDecision?> conflictDecisions = new(conflictDecisions);

        public List<ProfileConflictRequest> ProfileConflictRequests { get; } = [];

        public Task<bool> ConfirmDiscardChangesAsync(DiscardChangesAction action, CancellationToken cancellationToken) =>
            Task.FromResult(true);

        public Task<bool> ConfirmBulkOperationAsync(
            string title,
            string message,
            CancellationToken cancellationToken) =>
            Task.FromResult(true);

        public Task<bool> ConfirmExportOverwriteAsync(ExportPreviewResult preview, CancellationToken cancellationToken) =>
            Task.FromResult(true);

        public Task<ProfileConflictDecision?> PromptProfileConflictAsync(
            ProfileConflictRequest request,
            CancellationToken cancellationToken)
        {
            ProfileConflictRequests.Add(request);
            return Task.FromResult(conflictDecisions.Count == 0 ? null : conflictDecisions.Dequeue());
        }

        public void ShowAbout()
        {
        }
    }
}
