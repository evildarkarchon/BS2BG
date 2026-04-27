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
}
