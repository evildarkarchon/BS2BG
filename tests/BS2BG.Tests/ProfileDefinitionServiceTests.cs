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

    [Fact]
    public void ValidateProfileJson_ReturnsInvalidJsonDiagnosticForMalformedJson()
    {
        var service = new ProfileDefinitionService();

        var result = service.ValidateProfileJson(
            "{ not valid json }",
            ProfileValidationContext.ForImport(Array.Empty<string>(), ProfileSourceKind.LocalCustom));

        result.IsValid.Should().BeFalse();
        result.Profile.Should().BeNull();
        result.Diagnostics.Should().ContainSingle(diagnostic => diagnostic.Code == "InvalidJson");
    }

    [Fact]
    public void ValidateProfileJson_RejectsNonnumericTableValues()
    {
        var service = new ProfileDefinitionService();

        var result = service.ValidateProfileJson(
            """
            {
              "Name": "Bad Numbers",
              "Defaults": {
                "Breasts": { "valueSmall": "low", "valueBig": 1 }
              },
              "Multipliers": {
                "Waist": "high"
              },
              "Inverted": []
            }
            """,
            ProfileValidationContext.ForImport(Array.Empty<string>(), ProfileSourceKind.LocalCustom));

        result.IsValid.Should().BeFalse();
        result.Diagnostics.Should().Contain(diagnostic => diagnostic.Code == "InvalidNumber" && diagnostic.Table == "Defaults");
        result.Diagnostics.Should().Contain(diagnostic => diagnostic.Code == "InvalidNumber" && diagnostic.Table == "Multipliers");
    }

    [Fact]
    public void ValidateProfileJson_UsesInternalNameInsteadOfFileName()
    {
        var service = new ProfileDefinitionService();

        var result = service.ValidateProfileJson(
            """
            {
              "Name": "Internal Identity",
              "Defaults": {},
              "Multipliers": {},
              "Inverted": []
            }
            """,
            ProfileValidationContext.ForImport(Array.Empty<string>(), ProfileSourceKind.LocalCustom, "Different File Name.json"));

        result.IsValid.Should().BeTrue();
        result.Profile!.Name.Should().Be("Internal Identity");
    }

    [Fact]
    public void ValidateProfileJson_RejectsUnsupportedVersion()
    {
        var service = new ProfileDefinitionService();

        var result = service.ValidateProfileJson(
            """
            {
              "Version": 2,
              "Name": "Future Profile",
              "Defaults": {},
              "Multipliers": {},
              "Inverted": []
            }
            """,
            ProfileValidationContext.ForImport(Array.Empty<string>(), ProfileSourceKind.LocalCustom));

        result.IsValid.Should().BeFalse();
        result.Diagnostics.Should().ContainSingle(diagnostic => diagnostic.Code == "UnsupportedVersion");
    }

    [Fact]
    public void ExportProfileJson_RoundTripsStableProfile()
    {
        var service = new ProfileDefinitionService();
        var profile = new CustomProfileDefinition(
            "Community CBBE",
            "Skyrim",
            new SliderProfile(
                new[] { new SliderDefault("Breasts", 0.25f, 0.75f) },
                new[] { new SliderMultiplier("Waist", 2.75f) },
                new[] { "InvertA" }),
            ProfileSourceKind.LocalCustom,
            "community.json");

        var firstExport = service.ExportProfileJson(profile);
        var roundTrip = service.ValidateProfileJson(
            firstExport,
            ProfileValidationContext.ForImport(Array.Empty<string>(), ProfileSourceKind.LocalCustom));
        var secondExport = service.ExportProfileJson(roundTrip.Profile!);

        roundTrip.IsValid.Should().BeTrue();
        secondExport.Should().Be(firstExport);
        firstExport.Should().StartWith("{\n  \"Version\": 1,\n  \"Name\": \"Community CBBE\"");
        firstExport.Should().NotEndWith("\n");
    }

    [Fact]
    public void ExportProfileJson_SortsTableKeysDeterministically()
    {
        var service = new ProfileDefinitionService();
        var profile = new CustomProfileDefinition(
            "Sorted",
            string.Empty,
            new SliderProfile(
                new[]
                {
                    new SliderDefault("zeta", 0f, 1f),
                    new SliderDefault("Alpha", 0.5f, -0.5f),
                },
                new[]
                {
                    new SliderMultiplier("zeta", 2f),
                    new SliderMultiplier("Alpha", 1f),
                },
                new[] { "zeta", "Alpha" }),
            ProfileSourceKind.LocalCustom,
            null);

        var json = service.ExportProfileJson(profile);

        json.IndexOf("\"Alpha\"", StringComparison.Ordinal).Should().BeLessThan(json.IndexOf("\"zeta\"", StringComparison.Ordinal));
        json.IndexOf("\"Alpha\": 1", StringComparison.Ordinal).Should().BeLessThan(json.IndexOf("\"zeta\": 2", StringComparison.Ordinal));
        json.IndexOf("\"Alpha\"\n", StringComparison.Ordinal).Should().BeLessThan(json.LastIndexOf("\"zeta\"", StringComparison.Ordinal));
    }

    [Fact]
    public void ValidateProfileJson_RejectsDuplicateJsonObjectProperties()
    {
        var service = new ProfileDefinitionService();

        var result = service.ValidateProfileJson(
            """
            {
              "Name": "Duplicate Json Properties",
              "Defaults": {},
              "Defaults": {},
              "Multipliers": {},
              "Inverted": []
            }
            """,
            ProfileValidationContext.ForImport(Array.Empty<string>(), ProfileSourceKind.LocalCustom));

        result.IsValid.Should().BeFalse();
        result.Diagnostics.Should().ContainSingle(diagnostic => diagnostic.Code == "DuplicateProperty" && diagnostic.Table == null);
    }

    [Fact]
    public void ValidateProfileJson_RejectsNonFiniteNumbers()
    {
        var service = new ProfileDefinitionService();

        var result = service.ValidateProfileJson(
            """
            {
              "Name": "Nonfinite",
              "Defaults": {
                "Breasts": { "valueSmall": 1e999, "valueBig": 1 }
              },
              "Multipliers": {
                "Waist": -1e999
              },
              "Inverted": []
            }
            """,
            ProfileValidationContext.ForImport(Array.Empty<string>(), ProfileSourceKind.LocalCustom));

        result.IsValid.Should().BeFalse();
        result.Diagnostics.Should().Contain(diagnostic => diagnostic.Code == "NonFiniteNumber" && diagnostic.Table == "Defaults");
        result.Diagnostics.Should().Contain(diagnostic => diagnostic.Code == "NonFiniteNumber" && diagnostic.Table == "Multipliers");
    }

    [Fact]
    public void ValidateProfileJson_DefaultsMissingVersionToOne()
    {
        var service = new ProfileDefinitionService();

        var result = service.ValidateProfileJson(
            """
            {
              "Name": "Missing Version",
              "Defaults": {},
              "Multipliers": {},
              "Inverted": []
            }
            """,
            ProfileValidationContext.ForImport(Array.Empty<string>(), ProfileSourceKind.LocalCustom));

        result.IsValid.Should().BeTrue();
        service.ExportProfileJson(result.Profile!).Should().StartWith("{\n  \"Version\": 1,");
    }
}
