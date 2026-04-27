namespace BS2BG.App.ViewModels.Workflow;

/// <summary>
/// Identifies the NPC row dimensions that can provide checklist filter values.
/// </summary>
public enum NpcFilterColumn
{
    Mod,
    Name,
    EditorId,
    FormId,
    Race,
    AssignmentState,
    Preset
}

/// <summary>
/// Stores session-only NPC filter values and creates pure predicates over stable NPC row wrappers.
/// Text search is split into pending and applied values so ViewModels can debounce text entry while applying checklist changes immediately.
/// </summary>
public sealed class NpcFilterState
{
    private readonly StringComparer valueComparer = StringComparer.OrdinalIgnoreCase;
    private readonly Dictionary<NpcFilterColumn, HashSet<string>> allowedValues = new();
    private string appliedGlobalSearchText = string.Empty;
    private string pendingGlobalSearchText = string.Empty;

    /// <summary>
    /// Gets the checklist label used for rows with at least one preset assignment.
    /// </summary>
    public const string AssignedValue = "Assigned";

    /// <summary>
    /// Gets the checklist label used for rows without preset assignments.
    /// </summary>
    public const string EmptyValue = "Empty";

    /// <summary>
    /// Gets or sets the latest typed global search text that has not necessarily been applied to predicates yet.
    /// </summary>
    public string PendingGlobalSearchText
    {
        get => pendingGlobalSearchText;
        set => pendingGlobalSearchText = value ?? string.Empty;
    }

    /// <summary>
    /// Gets the search text currently used by predicates.
    /// </summary>
    public string AppliedGlobalSearchText => appliedGlobalSearchText;

    /// <summary>
    /// Copies the pending search text into the applied value used by newly created predicates.
    /// </summary>
    public void ApplyPendingGlobalSearchText() => appliedGlobalSearchText = pendingGlobalSearchText;

    /// <summary>
    /// Replaces the allowed checklist values for a column; an empty value set clears the restriction.
    /// </summary>
    /// <param name="column">The filter column to update.</param>
    /// <param name="values">The allowed values for this column.</param>
    /// <exception cref="ArgumentNullException">Thrown when <paramref name="values" /> is null.</exception>
    public void SetAllowedValues(NpcFilterColumn column, IEnumerable<string> values)
    {
        ArgumentNullException.ThrowIfNull(values);

        var normalizedValues = values
            .Select(NormalizeFilterValue)
            .Where(value => value.Length > 0)
            .ToArray();

        if (normalizedValues.Length == 0)
        {
            allowedValues.Remove(column);
            return;
        }

        allowedValues[column] = new HashSet<string>(normalizedValues, StringComparer.OrdinalIgnoreCase);
    }

    /// <summary>
    /// Clears any checklist restriction for a column.
    /// </summary>
    /// <param name="column">The filter column to clear.</param>
    public void ClearAllowedValues(NpcFilterColumn column) => allowedValues.Remove(column);

    /// <summary>
    /// Gets the currently allowed checklist values for a column.
    /// </summary>
    /// <param name="column">The filter column to inspect.</param>
    /// <returns>A sorted snapshot of allowed values, or an empty list when unrestricted.</returns>
    public IReadOnlyList<string> GetAllowedValues(NpcFilterColumn column)
    {
        return allowedValues.TryGetValue(column, out var values)
            ? values.OrderBy(value => value, StringComparer.OrdinalIgnoreCase).ToArray()
            : Array.Empty<string>();
    }

    /// <summary>
    /// Creates a side-effect-free predicate over NPC row wrappers using the current checklist and applied search state.
    /// </summary>
    /// <returns>A predicate suitable for DynamicData or manual filtering.</returns>
    public Func<NpcRowViewModel, bool> CreatePredicate()
    {
        var allowedSnapshot = allowedValues.ToDictionary(
            pair => pair.Key,
            pair => new HashSet<string>(pair.Value, StringComparer.OrdinalIgnoreCase));
        var searchText = appliedGlobalSearchText.Trim();

        return row => row is not null && MatchesChecklists(row, allowedSnapshot) && MatchesGlobalSearch(row, searchText);
    }

    /// <summary>
    /// Builds a distinct sorted value list for the requested column without mutating row state.
    /// </summary>
    /// <param name="rows">The row snapshot used to populate a checklist popup.</param>
    /// <param name="column">The column whose values should be collected.</param>
    /// <returns>Distinct non-empty values sorted with an ordinal ignore-case comparer.</returns>
    /// <exception cref="ArgumentNullException">Thrown when <paramref name="rows" /> is null.</exception>
    public IReadOnlyList<string> GetAvailableValues(IEnumerable<NpcRowViewModel> rows, NpcFilterColumn column)
    {
        ArgumentNullException.ThrowIfNull(rows);

        return rows
            .SelectMany(row => GetColumnValues(row, column))
            .Select(NormalizeFilterValue)
            .Where(value => value.Length > 0)
            .Distinct(valueComparer)
            .OrderBy(value => value, valueComparer)
            .ToArray();
    }

    private static bool MatchesChecklists(
        NpcRowViewModel row,
        IReadOnlyDictionary<NpcFilterColumn, HashSet<string>> allowedSnapshot)
    {
        foreach (var (column, allowed) in allowedSnapshot)
        {
            if (allowed.Count == 0) continue;

            if (!GetColumnValues(row, column).Any(value => allowed.Contains(NormalizeFilterValue(value)))) return false;
        }

        return true;
    }

    private static bool MatchesGlobalSearch(NpcRowViewModel row, string searchText)
    {
        if (string.IsNullOrWhiteSpace(searchText)) return true;

        return SearchableValues(row).Any(value => value.Contains(searchText, StringComparison.OrdinalIgnoreCase));
    }

    private static IEnumerable<string> SearchableValues(NpcRowViewModel row)
    {
        yield return row.Mod;
        yield return row.Name;
        yield return row.EditorId;
        yield return row.FormId;
        yield return row.Race;

        foreach (var preset in row.Npc.SliderPresets) yield return preset.Name;
    }

    private static IEnumerable<string> GetColumnValues(NpcRowViewModel row, NpcFilterColumn column)
    {
        return column switch
        {
            NpcFilterColumn.Mod => Single(row.Mod),
            NpcFilterColumn.Name => Single(row.Name),
            NpcFilterColumn.EditorId => Single(row.EditorId),
            NpcFilterColumn.FormId => Single(row.FormId),
            NpcFilterColumn.Race => Single(row.Race),
            NpcFilterColumn.AssignmentState => Single(row.HasAssignments ? AssignedValue : EmptyValue),
            NpcFilterColumn.Preset => row.Npc.SliderPresets.Select(preset => preset.Name),
            _ => Array.Empty<string>()
        };
    }

    private static IEnumerable<string> Single(string value)
    {
        yield return value;
    }

    private static string NormalizeFilterValue(string? value) => (value ?? string.Empty).Trim();
}
