using BS2BG.Core.Diagnostics;

namespace BS2BG.App.Services;

public enum DiscardChangesAction
{
    NewProject,
    OpenProject
}

/// <summary>
/// Explicit decisions available when embedded project profile data conflicts with a local custom profile.
/// </summary>
public enum ProfileConflictResolution
{
    UseProjectCopy,
    ReplaceLocalProfile,
    RenameProjectCopy,
    KeepLocalProfile
}

/// <summary>
/// Describes one embedded/local custom-profile conflict to present before project-open mutation.
/// </summary>
/// <param name="ProfileName">Display identity shared by the conflicting profiles.</param>
/// <param name="LocalSummary">Concise local-profile source/difference summary for user review.</param>
/// <param name="EmbeddedSummary">Concise embedded-profile source/difference summary for user review.</param>
public sealed record ProfileConflictRequest(string ProfileName, string LocalSummary, string EmbeddedSummary);

/// <summary>
/// Result selected for a single profile conflict; a null dialog result means cancel with no mutation.
/// </summary>
/// <param name="Resolution">Chosen conflict-resolution strategy.</param>
/// <param name="RenamedProfileName">New project-profile name when <see cref="ProfileConflictResolution.RenameProjectCopy" /> is selected.</param>
public sealed record ProfileConflictDecision(ProfileConflictResolution Resolution, string? RenamedProfileName);

public interface IAppDialogService
{
    Task<bool> ConfirmDiscardChangesAsync(DiscardChangesAction action, CancellationToken cancellationToken);

    Task<bool> ConfirmBulkOperationAsync(string title, string message, CancellationToken cancellationToken);

    /// <summary>
    /// Asks the user to approve an export that may replace existing output files.
    /// </summary>
    /// <param name="preview">Read-only preview facts that list the target files and snippets.</param>
    /// <param name="cancellationToken">Cancels the dialog and treats the export as not approved.</param>
    /// <returns><see langword="true" /> when the export may proceed; otherwise <see langword="false" />.</returns>
    Task<bool> ConfirmExportOverwriteAsync(ExportPreviewResult preview, CancellationToken cancellationToken);

    /// <summary>
    /// Prompts for an explicit decision when project-embedded custom profile data conflicts with local profile data.
    /// </summary>
    /// <param name="request">Conflict facts to display before any project or local-store mutation occurs.</param>
    /// <param name="cancellationToken">Cancels the dialog and returns <see langword="null" />.</param>
    /// <returns>The selected decision, or <see langword="null" /> when the open should abort without mutation.</returns>
    Task<ProfileConflictDecision?> PromptProfileConflictAsync(ProfileConflictRequest request, CancellationToken cancellationToken);

    void ShowAbout();
}
