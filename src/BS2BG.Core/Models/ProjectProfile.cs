namespace BS2BG.Core.Models;

public sealed class ProjectProfile
{
    private readonly IReadOnlyList<string> defaultSliderNames;

    public ProjectProfile(string name, IEnumerable<string> defaultSliderNames)
    {
        Name = ProjectProfileMapping.Resolve(name, isUunp: false);
        this.defaultSliderNames = defaultSliderNames?.ToArray()
            ?? throw new ArgumentNullException(nameof(defaultSliderNames));
    }

    public string Name { get; }

    public IReadOnlyList<string> DefaultSliderNames => defaultSliderNames;
}
