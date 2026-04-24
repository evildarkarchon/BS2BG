using BS2BG.Core.Formatting;
using BS2BG.Core.Models;

namespace BS2BG.Core.Generation;

public sealed class TemplateProfile(string name, SliderProfile sliderProfile)
{
    public string Name { get; } = ProjectProfileMapping.Resolve(name, false);

    public SliderProfile SliderProfile { get; } =
        sliderProfile ?? throw new ArgumentNullException(nameof(sliderProfile));

    public IReadOnlyList<string> DefaultSliderNames =>
        SliderProfile.Defaults.Select(defaultValue => defaultValue.Name).ToArray();
}
