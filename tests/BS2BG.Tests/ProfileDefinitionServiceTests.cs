using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using FluentAssertions;
using Xunit;

namespace BS2BG.Tests;

public sealed class ProfileDefinitionServiceTests
{
    [Fact]
    public void ValidateProfileJson_AllowsValidNamedProfile()
    {
        var service = new ProfileDefinitionService();
        var result = service.ValidateProfileJson(
            """
            {
              "Name": "Community CBBE",
              "Game": "Skyrim",
              "Defaults": {
                "Breasts": { "valueSmall": 0.25, "valueBig": 0.75 }
              },
              "Multipliers": {
                "Breasts": 1.5
              },
              "Inverted": ["Waist"]
            }
            """,
            ProfileValidationContext.ForImport(Array.Empty<string>(), ProfileSourceKind.LocalCustom, "community.json"));

        result.IsValid.Should().BeTrue();
        result.Profile.Should().NotBeNull();
        result.Profile!.Name.Should().Be("Community CBBE");
        result.Profile.Game.Should().Be("Skyrim");
        result.Profile.SourceKind.Should().Be(ProfileSourceKind.LocalCustom);
        result.Profile.FilePath.Should().Be("community.json");
        result.Profile.SliderProfile.Defaults.Should().ContainSingle(defaultValue => defaultValue.Name == "Breasts");
        result.Profile.SliderProfile.Multipliers.Should().ContainSingle(multiplier => multiplier.Name == "Breasts");
        result.Profile.SliderProfile.InvertedNames.Should().ContainSingle("Waist");
    }

    [Fact]
    public void ValidateProfileJson_AllowsBlankProfile()
    {
        var service = new ProfileDefinitionService();
        var result = service.ValidateProfileJson(
            """
            {
              "Name": "Blank Start",
              "Game": "Skyrim",
              "Defaults": {},
              "Multipliers": {},
              "Inverted": []
            }
            """,
            ProfileValidationContext.ForImport(Array.Empty<string>(), ProfileSourceKind.LocalCustom));

        result.IsValid.Should().BeTrue();
        result.Profile.Should().NotBeNull();
        result.Profile!.SliderProfile.Defaults.Should().BeEmpty();
        result.Profile.SliderProfile.Multipliers.Should().BeEmpty();
        result.Profile.SliderProfile.InvertedNames.Should().BeEmpty();
    }

    [Fact]
    public void ValidateProfileJson_AllowsBroadFiniteNumbers()
    {
        var service = new ProfileDefinitionService();
        var result = service.ValidateProfileJson(
            """
            {
              "Name": "Broad Numbers",
              "Defaults": {
                "Extreme": { "valueSmall": -42.5, "valueBig": 10000 }
              },
              "Multipliers": {
                "Extreme": 2.75
              },
              "Inverted": []
            }
            """,
            ProfileValidationContext.ForImport(Array.Empty<string>(), ProfileSourceKind.LocalCustom));

        result.IsValid.Should().BeTrue();
        result.Profile!.SliderProfile.Defaults.Should().ContainSingle(defaultValue =>
            defaultValue.Name == "Extreme" && defaultValue.ValueSmall.Equals(-42.5f) && defaultValue.ValueBig.Equals(10000f));
        result.Profile.SliderProfile.Multipliers.Should().ContainSingle(multiplier =>
            multiplier.Name == "Extreme" && multiplier.Value.Equals(2.75f));
    }

    [Fact]
    public void ValidateProfileJson_RejectsDuplicateDisplayName()
    {
        var service = new ProfileDefinitionService();
        var result = service.ValidateProfileJson(
            """
            {
              "Name": "skyrim cbbe",
              "Defaults": {},
              "Multipliers": {},
              "Inverted": []
            }
            """,
            ProfileValidationContext.ForImport(new[] { "Skyrim CBBE" }, ProfileSourceKind.LocalCustom));

        result.IsValid.Should().BeFalse();
        result.Profile.Should().BeNull();
        result.Diagnostics.Should().Contain(diagnostic =>
            diagnostic.Severity == ProfileValidationSeverity.Blocker && diagnostic.Code == "DuplicateProfileName");
    }

    [Fact]
    public void ValidateProfileJson_RejectsDuplicateSliderNamesAndBlankSliderNames()
    {
        var service = new ProfileDefinitionService();
        var result = service.ValidateProfileJson(
            """
            {
              "Name": "Bad Sliders",
              "Defaults": {
                "Breasts": { "valueSmall": 0, "valueBig": 1 },
                "breasts": { "valueSmall": 0.5, "valueBig": 0.75 },
                "": { "valueSmall": 0, "valueBig": 1 }
              },
              "Multipliers": {
                "Waist": 1,
                "waist": 2,
                " ": 1
              },
              "Inverted": ["Legs", "legs", ""]
            }
            """,
            ProfileValidationContext.ForImport(Array.Empty<string>(), ProfileSourceKind.LocalCustom));

        result.IsValid.Should().BeFalse();
        result.Diagnostics.Should().Contain(diagnostic => diagnostic.Code == "DuplicateSliderName" && diagnostic.Table == "Defaults");
        result.Diagnostics.Should().Contain(diagnostic => diagnostic.Code == "BlankSliderName" && diagnostic.Table == "Defaults");
        result.Diagnostics.Should().Contain(diagnostic => diagnostic.Code == "DuplicateSliderName" && diagnostic.Table == "Multipliers");
        result.Diagnostics.Should().Contain(diagnostic => diagnostic.Code == "BlankSliderName" && diagnostic.Table == "Multipliers");
        result.Diagnostics.Should().Contain(diagnostic => diagnostic.Code == "DuplicateSliderName" && diagnostic.Table == "Inverted");
        result.Diagnostics.Should().Contain(diagnostic => diagnostic.Code == "BlankSliderName" && diagnostic.Table == "Inverted");
    }

    [Fact]
    public void DefinitionallyEquals_UsesNormalizedConflictSemantics()
    {
        var left = new CustomProfileDefinition(
            "Community CBBE",
            "Skyrim",
            new SliderProfile(
                new[]
                {
                    new SliderDefault("Breasts", 0.25f, 0.75f),
                    new SliderDefault("Waist", 1f, -1f),
                },
                new[]
                {
                    new SliderMultiplier("Arms", 2f),
                    new SliderMultiplier("Legs", -3f),
                },
                new[] { "InvertA", "invertb" }),
            ProfileSourceKind.LocalCustom,
            "left.json");
        var right = new CustomProfileDefinition(
            "community cbbe",
            "Skyrim",
            new SliderProfile(
                new[]
                {
                    new SliderDefault("Waist", 1f, -1f),
                    new SliderDefault("Breasts", 0.25f, 0.75f),
                },
                new[]
                {
                    new SliderMultiplier("Legs", -3f),
                    new SliderMultiplier("Arms", 2f),
                },
                new[] { "invertb", "InvertA" }),
            ProfileSourceKind.EmbeddedProject,
            "right.json");
        var caseChangedSlider = right with
        {
            SliderProfile = new SliderProfile(
                new[]
                {
                    new SliderDefault("waist", 1f, -1f),
                    new SliderDefault("Breasts", 0.25f, 0.75f),
                },
                right.SliderProfile.Multipliers,
                right.SliderProfile.InvertedNames),
        };

        ProfileDefinitionEquality.DefinitionallyEquals(left, right).Should().BeTrue();
        ProfileDefinitionEquality.DefinitionallyEquals(left, caseChangedSlider).Should().BeFalse();
    }
}
