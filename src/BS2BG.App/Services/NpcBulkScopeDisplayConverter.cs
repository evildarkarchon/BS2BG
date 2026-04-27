using System.Globalization;
using Avalonia.Data.Converters;
using BS2BG.App.ViewModels.Workflow;

namespace BS2BG.App.Services;

/// <summary>
/// Converts NPC bulk scope enum values into the exact labels required by the Morphs bulk-action selector.
/// </summary>
public sealed class NpcBulkScopeDisplayConverter : IValueConverter
{
    /// <inheritdoc />
    public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        return value is NpcBulkScope scope ? scope.ToDisplayName() : string.Empty;
    }

    /// <inheritdoc />
    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) =>
        throw new NotSupportedException("Bulk scope display labels are one-way only.");
}
