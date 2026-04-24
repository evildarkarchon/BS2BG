using BS2BG.Core.Formatting;
using BS2BG.Core.Models;

namespace BS2BG.Core.Generation;

public sealed class TemplateProfile
{
    public TemplateProfile(string name, SliderProfile sliderProfile)
    {
        Name = ProjectProfileMapping.Resolve(name, isUunp: false);
        SliderProfile = sliderProfile ?? throw new ArgumentNullException(nameof(sliderProfile));
    }

    public string Name { get; }

    public SliderProfile SliderProfile { get; }

    public IReadOnlyList<string> DefaultSliderNames =>
        SliderProfile.Defaults.Select(defaultValue => defaultValue.Name).ToArray();
}
