using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using BS2BG.Core.Profiles;
using Xunit;

namespace BS2BG.Tests;

public sealed class RequestScopedProfileCatalogComposerTests
{
    /// <summary>
    /// Verifies catalog composition preserves bundled-first ordering followed by resolved profiles in reference order.
    /// </summary>
    [Fact]
    public void BuildForProjectIncludesBundledThenResolvedProfilesInReferenceOrder()
    {
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("Beta", "Embedded Body"));
        project.SliderPresets.Add(new SliderPreset("Alpha", "Community Body"));
        project.CustomProfiles.Add(TestProfiles.CreateProfile("Community Body", ProfileSourceKind.EmbeddedProject));
        project.CustomProfiles.Add(TestProfiles.CreateProfile("Embedded Body", ProfileSourceKind.EmbeddedProject));
        var resolution = ReferencedCustomProfileResolver.Resolve(project);
        var composer = new RequestScopedProfileCatalogComposer(TestProfiles.CreateBundledOnlyCatalog());

        var catalog = composer.BuildForProject(resolution);

        catalog.Entries.Select(entry => entry.Name).Should().Equal(
            ProjectProfileMapping.SkyrimCbbe,
            "Embedded Body",
            "Community Body");
        catalog.Entries.Should().OnlyContain(entry => entry.SourceKind == ProfileSourceKind.Bundled || !entry.IsEditable);
    }
}
