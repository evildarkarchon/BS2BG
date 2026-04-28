using System.Globalization;
using Avalonia.Data.Converters;
using BS2BG.Core.Morphs;

namespace BS2BG.App.Services;

/// <summary>
/// Converts assignment strategy enum values into the exact Morphs strategy selector labels from the Phase 5 UI spec.
/// </summary>
public sealed class AssignmentStrategyKindDisplayConverter : IValueConverter
{
    /// <inheritdoc />
    public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        return value is AssignmentStrategyKind kind ? ToDisplayName(kind) : string.Empty;
    }

    /// <inheritdoc />
    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) =>
        throw new NotSupportedException("Assignment strategy labels are one-way only.");

    private static string ToDisplayName(AssignmentStrategyKind kind)
    {
        return kind switch
        {
            AssignmentStrategyKind.SeededRandom => "Seeded random",
            AssignmentStrategyKind.RoundRobin => "Round-robin",
            AssignmentStrategyKind.Weighted => "Weighted",
            AssignmentStrategyKind.RaceFilters => "Race filters",
            AssignmentStrategyKind.GroupsBuckets => "Groups / buckets",
            _ => kind.ToString()
        };
    }
}
