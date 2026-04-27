using System.Windows.Input;
using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using Xunit;

namespace BS2BG.Tests;

public sealed class ProfileEditorViewModelTests
{
    /// <summary>
    /// Verifies blank profiles can be authored through command-driven table row insertion and removal.
    /// </summary>
    [Fact]
    public void AddAndRemoveCommandsAuthorEveryProfileTableFromBlankProfile()
    {
        var vm = ProfileEditorViewModel.Blank(new ProfileDefinitionService(), [], new StubUserProfileStore());
        vm.Name = "Command Authored";

        ExecuteCommand(vm.AddDefaultCommand);
        ExecuteCommand(vm.AddMultiplierCommand);
        ExecuteCommand(vm.AddInvertedCommand);

        vm.DefaultRows.Should().ContainSingle();
        vm.MultiplierRows.Should().ContainSingle();
        vm.InvertedRows.Should().ContainSingle();
        vm.DefaultRows[0].Slider.Should().NotBeNullOrWhiteSpace();
        vm.MultiplierRows[0].Slider.Should().NotBeNullOrWhiteSpace();
        vm.InvertedRows[0].Slider.Should().NotBeNullOrWhiteSpace();

        vm.DefaultRows[0].ValueSmall = "-12.5";
        vm.DefaultRows[0].ValueBig = "42";
        vm.MultiplierRows[0].Value = "-3.5";
        vm.ValidateProfileCommand.Execute().Subscribe();

        vm.IsValid.Should().BeTrue();
        vm.BuildProfile(ProfileSourceKind.LocalCustom, null).Should().NotBeNull();

        ExecuteCommand(vm.RemoveDefaultCommand, vm.DefaultRows[0]);
        ExecuteCommand(vm.RemoveMultiplierCommand, vm.MultiplierRows[0]);
        ExecuteCommand(vm.RemoveInvertedCommand, vm.InvertedRows[0]);

        vm.DefaultRows.Should().BeEmpty();
        vm.MultiplierRows.Should().BeEmpty();
        vm.InvertedRows.Should().BeEmpty();
        vm.IsValid.Should().BeTrue();
        vm.ValidationRows.Should().Contain(row => row.Text == ProfileEditorViewModel.BlankProfileInfo);
    }

    /// <summary>
    /// Verifies strict table validation rejects duplicate and blank slider names before catalog inclusion.
    /// </summary>
    [Fact]
    public void DuplicateAndBlankSliderRowsAreRejected()
    {
        var vm = ProfileEditorViewModel.Blank(new ProfileDefinitionService(), [], new StubUserProfileStore());
        vm.Name = "Strict Rows";

        ExecuteCommand(vm.AddDefaultCommand);
        ExecuteCommand(vm.AddDefaultCommand);
        vm.DefaultRows[1].Slider = vm.DefaultRows[0].Slider;

        vm.ValidateProfileCommand.Execute().Subscribe();

        vm.IsValid.Should().BeFalse();
        vm.ValidationRows.Should().Contain(row => row.Text.Contains("Defaults contains duplicate slider", StringComparison.Ordinal));

        vm.DefaultRows[1].Slider = "";
        vm.ValidateProfileCommand.Execute().Subscribe();

        vm.IsValid.Should().BeFalse();
        vm.ValidationRows.Should().Contain(row => row.Text.Contains("Defaults contains duplicate slider", StringComparison.Ordinal));
    }

    /// <summary>
    /// Verifies broad finite profile numbers outside common bundled ranges are allowed when otherwise well-formed.
    /// </summary>
    [Fact]
    public void BroadFiniteNumericValuesOutsideCommonRangesRemainAccepted()
    {
        var vm = ProfileEditorViewModel.Blank(new ProfileDefinitionService(), [], new StubUserProfileStore());
        vm.Name = "Unusual Body";

        ExecuteCommand(vm.AddDefaultCommand);
        ExecuteCommand(vm.AddMultiplierCommand);
        vm.DefaultRows[0].ValueSmall = "-2.75";
        vm.DefaultRows[0].ValueBig = "8.5";
        vm.MultiplierRows[0].Value = "-12.25";

        vm.ValidateProfileCommand.Execute().Subscribe();

        vm.IsValid.Should().BeTrue();
        vm.ValidationRows.Should().NotContain(row => row.Severity == ProfileValidationSeverity.Blocker);
        var profile = vm.BuildProfile(ProfileSourceKind.LocalCustom, null);
        profile.Should().NotBeNull();
        profile!.SliderProfile.Defaults[0].ValueSmall.Should().Be(-2.75f);
        profile.SliderProfile.Multipliers[0].Value.Should().Be(-12.25f);
    }

    /// <summary>
    /// Verifies save gating blocks duplicate-name candidates and allows unique blank profiles.
    /// </summary>
    [Fact]
    public void SaveCanExecuteFalseWithBlockerAndTrueForValidBlankProfile()
    {
        var store = new StubUserProfileStore();
        var vm = ProfileEditorViewModel.Blank(new ProfileDefinitionService(), ["Existing Body"], store);

        vm.Name = "Existing Body";
        vm.ValidateProfileCommand.Execute().Subscribe();

        vm.IsValid.Should().BeFalse();
        ((ICommand)vm.SaveProfileCommand).CanExecute(null).Should().BeFalse();

        vm.Name = "New Blank";
        vm.ValidateProfileCommand.Execute().Subscribe();

        vm.IsValid.Should().BeTrue();
        ((ICommand)vm.SaveProfileCommand).CanExecute(null).Should().BeTrue();
        vm.ValidationRows.Should().Contain(row => row.Text == "Blank profiles are allowed. Add defaults, multipliers, and inverted sliders when you are ready.");
    }

    /// <summary>
    /// Verifies validation reflects the live editor buffer as malformed row text is fixed.
    /// </summary>
    [Fact]
    public void ValidationTransitionsValidBlockerValidAsCurrentBufferChanges()
    {
        var vm = ProfileEditorViewModel.Blank(new ProfileDefinitionService(), [], new StubUserProfileStore());
        vm.Name = "Editable";
        vm.DefaultRows.Add(new ProfileDefaultRowViewModel("Slider", "0", "1"));

        vm.ValidateProfileCommand.Execute().Subscribe();
        vm.IsValid.Should().BeTrue();

        vm.DefaultRows[0].ValueSmall = "not a number";
        vm.ValidateProfileCommand.Execute().Subscribe();
        vm.IsValid.Should().BeFalse();
        vm.ValidationRows.Should().Contain(row => row.Text == "Defaults value for Slider must be a number. Broad finite values are allowed; malformed values are not.");

        vm.DefaultRows[0].ValueSmall = "0.25";
        vm.ValidateProfileCommand.Execute().Subscribe();
        vm.IsValid.Should().BeTrue();
    }

    /// <summary>
    /// Verifies row property edits immediately refresh validation and save availability without manual validation commands.
    /// </summary>
    [Fact]
    public void RowPropertyEditsRefreshValidationAndSaveAvailabilityAutomatically()
    {
        var vm = ProfileEditorViewModel.FromProfile(
            "Live Rows",
            "SkyrimSE",
            new SliderProfile(
                [new SliderDefault("Existing Default", 0f, 1f)],
                [new SliderMultiplier("Existing Multiplier", 1f)],
                ["Existing Inverted"]),
            ProfileSourceKind.LocalCustom,
            null,
            new ProfileDefinitionService(),
            [],
            new StubUserProfileStore());

        vm.IsValid.Should().BeTrue();
        ((ICommand)vm.SaveProfileCommand).CanExecute(null).Should().BeTrue();

        vm.DefaultRows[0].ValueSmall = "not a number";

        vm.IsValid.Should().BeFalse();
        ((ICommand)vm.SaveProfileCommand).CanExecute(null).Should().BeFalse();
        vm.ValidationRows.Should().Contain(row => row.Text == "Defaults value for Existing Default must be a number. Broad finite values are allowed; malformed values are not.");

        vm.DefaultRows[0].ValueSmall = "0.25";

        vm.IsValid.Should().BeTrue();
        ((ICommand)vm.SaveProfileCommand).CanExecute(null).Should().BeTrue();

        ExecuteCommand(vm.AddMultiplierCommand);
        var removed = vm.MultiplierRows.Last();
        ExecuteCommand(vm.RemoveMultiplierCommand, removed);
        removed.Value = "not a number";

        vm.IsValid.Should().BeTrue();
        vm.ValidationRows.Should().NotContain(row => row.Text.Contains("not a number", StringComparison.Ordinal));
    }

    /// <summary>
    /// Verifies row search changes only the visible projection and not the saved candidate data.
    /// </summary>
    [Fact]
    public void SearchTextFiltersRowsWithoutRemovingThemFromSavedCandidate()
    {
        var store = new StubUserProfileStore();
        var vm = ProfileEditorViewModel.Blank(new ProfileDefinitionService(), [], store);
        vm.Name = "Searchable";
        vm.DefaultRows.Add(new ProfileDefaultRowViewModel("Breasts", "0", "1"));
        vm.DefaultRows.Add(new ProfileDefaultRowViewModel("Waist", "0.5", "0.75"));

        vm.SearchText = "waist";
        vm.SaveProfileCommand.Execute().Subscribe();

        vm.VisibleDefaultRows.Should().ContainSingle(row => row.Slider == "Waist");
        store.SavedProfiles.Should().ContainSingle();
        store.SavedProfiles[0].SliderProfile.Defaults.Should().HaveCount(2);
    }

    /// <summary>
    /// Verifies store failures keep the unsaved editor buffer intact and report the UI-SPEC failure copy.
    /// </summary>
    [Fact]
    public void SaveIoFailurePreservesUnsavedRowsAndLeavesProfileUncommitted()
    {
        var store = new StubUserProfileStore { SaveSucceeds = false };
        var vm = ProfileEditorViewModel.Blank(new ProfileDefinitionService(), [], store);
        vm.Name = "Unsaved";
        vm.DefaultRows.Add(new ProfileDefaultRowViewModel("Slider", "0", "1"));

        vm.SaveProfileCommand.Execute().Subscribe();

        vm.HasUnsavedChanges.Should().BeTrue();
        vm.DefaultRows.Should().ContainSingle(row => row.Slider == "Slider");
        store.SavedProfiles.Should().BeEmpty();
        vm.StatusRows.Should().Contain(row => row.Text == "Profile could not be saved. Review the validation messages below; malformed or ambiguous profile data is not added to the catalog.");
    }

    private sealed class StubUserProfileStore : IUserProfileStore
    {
        public bool SaveSucceeds { get; set; } = true;

        public List<CustomProfileDefinition> SavedProfiles { get; } = [];

        public UserProfileDiscoveryResult DiscoverProfiles() => DiscoverProfiles([]);

        public UserProfileDiscoveryResult DiscoverProfiles(IEnumerable<string> existingProfileNames) => new([], []);

        public UserProfileSaveResult SaveProfile(CustomProfileDefinition profile)
        {
            if (!SaveSucceeds)
            {
                return new(false, null, [new ProfileValidationDiagnostic(ProfileValidationSeverity.Blocker, "ProfileSaveFailed", "Denied", null, null)]);
            }

            SavedProfiles.Add(profile);
            return new(true, profile.FilePath, []);
        }

        public UserProfileDeleteResult DeleteProfile(CustomProfileDefinition profile) => new(true, profile.FilePath, []);
        public string GetDefaultProfileDirectory() => string.Empty;
    }

    private static void ExecuteCommand(ICommand command, object? parameter = null)
    {
        command.CanExecute(parameter).Should().BeTrue();
        command.Execute(parameter);
    }
}
