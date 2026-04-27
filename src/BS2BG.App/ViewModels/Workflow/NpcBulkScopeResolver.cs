namespace BS2BG.App.ViewModels.Workflow;

/// <summary>
/// Identifies the materialized target set used by NPC bulk operations.
/// </summary>
public enum NpcBulkScope
{
    All,
    Visible,
    Selected,
    VisibleEmpty
}

/// <summary>
/// Provides user-facing labels for NPC bulk scopes while keeping the enum values stable for command logic.
/// </summary>
public static class NpcBulkScopeDisplayExtensions
{
    /// <summary>
    /// Converts a scope value into the exact UI label required by the workflow contract.
    /// </summary>
    /// <param name="scope">The scope value to display.</param>
    /// <returns>The label shown in the Morphs bulk scope selector.</returns>
    public static string ToDisplayName(this NpcBulkScope scope)
    {
        return scope switch
        {
            NpcBulkScope.All => "All",
            NpcBulkScope.Visible => "Visible",
            NpcBulkScope.Selected => "Selected",
            NpcBulkScope.VisibleEmpty => "Visible Empty",
            _ => scope.ToString()
        };
    }
}

/// <summary>
/// Resolves NPC bulk-operation scopes to immutable row ID snapshots before any mutation occurs.
/// Snapshotting IDs prevents filter or selection changes during a command from changing the affected targets.
/// </summary>
public static class NpcBulkScopeResolver
{
    /// <summary>
    /// Resolves the requested scope from current all, visible, and selected row projections.
    /// </summary>
    /// <param name="scope">The scope selected by the user for the bulk operation.</param>
    /// <param name="allRows">All current NPC rows in backing collection order.</param>
    /// <param name="visibleRows">The currently visible NPC rows after filters are applied.</param>
    /// <param name="selectedRows">The currently selected NPC rows, including hidden selections.</param>
    /// <returns>A materialized row ID array that remains stable even if source projections later change.</returns>
    /// <exception cref="ArgumentNullException">Thrown when a row projection is null.</exception>
    public static Guid[] Resolve(
        NpcBulkScope scope,
        IEnumerable<NpcRowViewModel> allRows,
        IEnumerable<NpcRowViewModel> visibleRows,
        IEnumerable<NpcRowViewModel> selectedRows)
    {
        ArgumentNullException.ThrowIfNull(allRows);
        ArgumentNullException.ThrowIfNull(visibleRows);
        ArgumentNullException.ThrowIfNull(selectedRows);

        return scope switch
        {
            NpcBulkScope.All => MaterializeRowIds(allRows),
            NpcBulkScope.Visible => MaterializeRowIds(visibleRows),
            NpcBulkScope.Selected => MaterializeRowIds(selectedRows),
            NpcBulkScope.VisibleEmpty => MaterializeRowIds(visibleRows.Where(row => !row.HasAssignments)),
            _ => Array.Empty<Guid>()
        };
    }

    private static Guid[] MaterializeRowIds(IEnumerable<NpcRowViewModel> rows) =>
        rows.Select(row => row.RowId).ToArray();
}
