namespace BS2BG.Core.Models;

public sealed class ProjectProfile(string name, IEnumerable<string> defaultSliderNames)
{
    public string Name { get; } = ProjectProfileMapping.Resolve(name, false);

    public IReadOnlyList<string> DefaultSliderNames { get; } = defaultSliderNames?.ToArray()
                                                               ?? throw new ArgumentNullException(
                                                                   nameof(defaultSliderNames));
}
