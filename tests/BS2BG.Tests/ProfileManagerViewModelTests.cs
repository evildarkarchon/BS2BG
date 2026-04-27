using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.Core.Diagnostics;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using System.Reactive.Threading.Tasks;
using System.Text.Json;
using System.Windows.Input;
using Xunit;
using SliderPreset = BS2BG.Core.Models.SliderPreset;

namespace BS2BG.Tests;

public sealed class ProfileManagerViewModelTests
{
    /// <summary>
    /// Verifies bundled catalog rows stay read-only while still allowing copy-to-custom authoring.
    /// </summary>
    [Fact]
    public void BundledProfileAllowsCopyButNotEditDeleteOrSave()
    {
        var vm = CreateManager(new TemplateProfileCatalog(new[]
        {
            new TemplateProfile("Bundled Body", CreateSliderProfile())
        }));

        vm.SelectedProfile = vm.ProfileEntries.Single(entry => entry.Name == "Bundled Body");

        ((ICommand)vm.CopyBundledProfileCommand).CanExecute(null).Should().BeTrue();
        ((ICommand)vm.DeleteCustomProfileCommand).CanExecute(null).Should().BeFalse();
        ((ICommand)vm.SaveProfileCommand).CanExecute(null).Should().BeFalse();
        vm.SelectedProfile.SourceLabel.Should().Be("Bundled — read-only");
    }

    /// <summary>
    /// Verifies copy-as-custom captures the selected bundled row before clearing row selection for the new editor.
    /// </summary>
    [Fact]
    public void CopyBundledProfileSeedsEditorFromSelectedBundledProfile()
    {
        var sourceProfile = new SliderProfile(
            [new SliderDefault("SourceSlider", 0.25f, 0.75f)],
            [new SliderMultiplier("SourceSlider", 1.5f)],
            ["SourceSlider"]);
        var vm = CreateManager(new TemplateProfileCatalog(new[]
        {
            new TemplateProfile("Bundled Body", sourceProfile)
        }));
        vm.SelectedProfile = vm.ProfileEntries.Single(entry => entry.Name == "Bundled Body");

        vm.CopyBundledProfileCommand.Execute().Subscribe();

        vm.SelectedProfile.Should().BeNull();
        vm.Editor.Name.Should().BeEmpty();
        vm.Editor.DefaultRows.Should().ContainSingle(row => row.Slider == "SourceSlider" && row.ValueSmall == "0.25" && row.ValueBig == "0.75");
        vm.Editor.MultiplierRows.Should().ContainSingle(row => row.Slider == "SourceSlider" && row.Value == "1.5");
        vm.Editor.InvertedRows.Should().ContainSingle(row => row.Slider == "SourceSlider" && row.IsInverted);
    }

    /// <summary>
    /// Verifies selection changes honor unsaved-editor discard confirmation and retain the prior editor when declined.
    /// </summary>
    [Fact]
    public async Task SelectionWithUnsavedEditsPromptsBeforeDiscardingPriorEditor()
    {
        var dialog = new StubProfileManagementDialogService { ConfirmDiscardUnsavedEditsResult = false };
        var catalog = new TemplateProfileCatalog(new[]
        {
            new ProfileCatalogEntry("Bundled Body", new TemplateProfile("Bundled Body", CreateSliderProfile()), ProfileSourceKind.Bundled, null, false),
            new ProfileCatalogEntry("Custom Body", new TemplateProfile("Custom Body", CreateSliderProfile()), ProfileSourceKind.LocalCustom, "custom.json", true)
        });
        var vm = CreateManager(catalog, dialog: dialog);
        vm.SelectedProfile = vm.ProfileEntries.Single(entry => entry.Name == "Custom Body");
        vm.Editor.Name = "Unsaved Custom";
        var priorEditor = vm.Editor;

        var changed = await vm.TrySelectProfileAsync(
            vm.ProfileEntries.Single(entry => entry.Name == "Bundled Body"),
            TestContext.Current.CancellationToken);

        changed.Should().BeFalse();
        dialog.ConfirmDiscardUnsavedEditsCalls.Should().Be(1);
        vm.SelectedProfile!.Name.Should().Be("Custom Body");
        vm.Editor.Should().BeSameAs(priorEditor);
    }

    /// <summary>
    /// Verifies a row selected after the initial profile becomes the manager's actionable profile target.
    /// </summary>
    [Fact]
    public async Task SelectingNonInitialProfileRowChangesActionTarget()
    {
        var catalog = new TemplateProfileCatalog(new[]
        {
            new ProfileCatalogEntry("Bundled Body", new TemplateProfile("Bundled Body", CreateSliderProfile()), ProfileSourceKind.Bundled, null, false),
            new ProfileCatalogEntry("Custom Body", new TemplateProfile("Custom Body", CreateSliderProfile()), ProfileSourceKind.LocalCustom, "custom.json", true)
        });
        var vm = CreateManager(catalog);
        var initial = vm.SelectedProfile;

        var changed = await vm.TrySelectProfileAsync(
            vm.ProfileEntries.Single(entry => entry.Name == "Custom Body"),
            TestContext.Current.CancellationToken);

        changed.Should().BeTrue();
        vm.SelectedProfile.Should().NotBeSameAs(initial);
        vm.SelectedProfile!.Name.Should().Be("Custom Body");
        vm.Editor.Name.Should().Be("Custom Body");
    }

    /// <summary>
    /// Verifies referenced custom-profile deletes require explicit confirmation and keep project references unresolved.
    /// </summary>
    [Fact]
    public async Task DeleteReferencedCustomProfileRequiresConfirmationAndLeavesProjectReferencesUnresolved()
    {
        var dialog = new StubProfileManagementDialogService
        {
            ConfirmDeleteProfileResult = true,
            ConfirmDeleteReferencedProfileResult = true
        };
        var store = new StubUserProfileStore();
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("Preset") { ProfileName = "Custom Body" });
        var catalogService = new StubTemplateProfileCatalogService(new TemplateProfileCatalog(new[]
        {
            new ProfileCatalogEntry("Bundled Body", new TemplateProfile("Bundled Body", CreateSliderProfile()), ProfileSourceKind.Bundled, null, false),
            new ProfileCatalogEntry("Custom Body", new TemplateProfile("Custom Body", CreateSliderProfile()), ProfileSourceKind.LocalCustom, "custom.json", true)
        }));
        var vm = CreateManager(catalogService.Current, project, store, dialog, catalogService);
        vm.SelectedProfile = vm.ProfileEntries.Single(entry => entry.Name == "Custom Body");

        await vm.DeleteSelectedCustomProfileAsync(TestContext.Current.CancellationToken);

        dialog.ConfirmDeleteReferencedProfileCalls.Should().Be(1);
        dialog.LastAffectedPresetCount.Should().Be(1);
        store.DeletedProfiles.Should().ContainSingle(profile => profile.Name == "Custom Body");
        catalogService.RefreshCalls.Should().Be(1);
        project.SliderPresets[0].ProfileName.Should().Be("Custom Body");
    }

    /// <summary>
    /// Verifies unresolved project profile names get a neutral missing-source label in the manager rail.
    /// </summary>
    [Fact]
    public void MissingProjectProfileRowsUseFallbackSourceLabel()
    {
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("Preset") { ProfileName = "Missing Body" });
        var vm = CreateManager(new TemplateProfileCatalog(new[]
        {
            new TemplateProfile("Bundled Body", CreateSliderProfile())
        }), project);

        vm.ProfileEntries.Single(entry => entry.Name == "Missing Body").SourceLabel.Should().Be("Missing — using fallback");
    }

    /// <summary>
    /// Verifies recovery import uses internal profile names only; a matching filename cannot resolve a different profile identity.
    /// </summary>
    [Fact]
    public async Task ImportMatchingProfileRejectsFilenameMatchWhenInternalNameDiffers()
    {
        using var directory = new TemporaryDirectory();
        var file = directory.WriteJson("Missing Body.json", CreateProfileJson("Different Internal Name"));
        var dialog = new StubProfileManagementDialogService { ImportFiles = [file] };
        var store = new StubUserProfileStore();
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("Preset") { ProfileName = "Missing Body" });
        var vm = CreateManager(new TemplateProfileCatalog(new[]
        {
            new TemplateProfile("Bundled Body", CreateSliderProfile())
        }), project, store, dialog);

        var resolved = await vm.ImportMatchingProfileForMissingReferenceAsync(
            "Missing Body",
            TestContext.Current.CancellationToken);

        resolved.Should().BeFalse();
        store.SavedProfiles.Should().BeEmpty();
        vm.StatusMessage.Should().Contain("Imported profiles resolve missing references only when the internal profile display name matches exactly");
        vm.ProfileEntries.Should().Contain(entry => entry.Name == "Missing Body" && entry.IsMissing);
    }

    /// <summary>
    /// Verifies project-embedded copies can be activated as project-scoped catalog overlays without local store writes.
    /// </summary>
    [Fact]
    public void UseProjectCopyActivatesOverlayWithoutWritingLocalStore()
    {
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("Preset") { ProfileName = "Embedded Body" });
        project.CustomProfiles.Add(new CustomProfileDefinition(
            "Embedded Body",
            string.Empty,
            CreateSliderProfile(),
            ProfileSourceKind.EmbeddedProject,
            null));
        var store = new StubUserProfileStore();
        var catalogService = new StubTemplateProfileCatalogService(new TemplateProfileCatalog(new[]
        {
            new TemplateProfile("Bundled Body", CreateSliderProfile())
        }));
        var vm = CreateManager(catalogService.Current, project, store, catalogService: catalogService);

        vm.UseProjectCopyForMissingReference("Embedded Body").Should().BeTrue();

        catalogService.ProjectOverlayNames.Should().Equal("Embedded Body");
        catalogService.Current.ContainsProfile("Embedded Body").Should().BeTrue();
        store.SavedProfiles.Should().BeEmpty();
        vm.StatusMessage.Should().Be("Project copy is active for 'Embedded Body'.");
    }

    /// <summary>
    /// Verifies the advertised remap recovery action delegates to the undoable templates remapper after choosing an installed profile.
    /// </summary>
    [Fact]
    public async Task RecoveryRemapActionPromptsForInstalledProfileAndDelegatesReferenceRemap()
    {
        var dialog = new StubProfileManagementDialogService { RemapProfileName = "Bundled Body" };
        var remapper = new StubProfileReferenceRemapper { RemapResult = true };
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("Preset") { ProfileName = "Missing Body" });
        var vm = CreateManager(new TemplateProfileCatalog(new[]
        {
            new TemplateProfile("Bundled Body", CreateSliderProfile())
        }), project, dialog: dialog, remapper: remapper);

        var handled = await vm.ExecuteRecoveryActionAsync(
            ProfileRecoveryActionKind.RemapToInstalledProfile,
            "Missing Body",
            TestContext.Current.CancellationToken);

        handled.Should().BeTrue();
        dialog.LastRemapMissingProfileName.Should().Be("Missing Body");
        dialog.LastRemapInstalledProfileNames.Should().Equal("Bundled Body");
        remapper.Requests.Should().ContainSingle().Which.Should().Be(("Missing Body", "Bundled Body"));
        vm.StatusMessage.Should().Be("Remapped presets from 'Missing Body' to 'Bundled Body'.");
    }

    /// <summary>
    /// Verifies standalone profile JSON export is available for custom and embedded rows but not bundled or missing rows.
    /// </summary>
    [Fact]
    public async Task ExportProfileJsonIsEnabledOnlyForCustomAndEmbeddedProfiles()
    {
        using var directory = new TemporaryDirectory();
        var exportPath = Path.Combine(directory.DirectoryPath, "exported.json");
        var dialog = new StubProfileManagementDialogService { ExportPath = exportPath };
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("Preset") { ProfileName = "Missing Body" });
        var catalog = new TemplateProfileCatalog(new[]
        {
            new ProfileCatalogEntry("Bundled Body", new TemplateProfile("Bundled Body", CreateSliderProfile()), ProfileSourceKind.Bundled, null, false),
            new ProfileCatalogEntry("Custom Body", new TemplateProfile("Custom Body", CreateSliderProfile()), ProfileSourceKind.LocalCustom, "custom.json", true),
            new ProfileCatalogEntry("Embedded Body", new TemplateProfile("Embedded Body", CreateSliderProfile()), ProfileSourceKind.EmbeddedProject, null, false)
        });
        var vm = CreateManager(catalog, project, dialog: dialog);

        vm.SelectedProfile = vm.ProfileEntries.Single(entry => entry.Name == "Bundled Body");
        ((ICommand)vm.ExportProfileCommand).CanExecute(null).Should().BeFalse();
        vm.SelectedProfile = vm.ProfileEntries.Single(entry => entry.Name == "Missing Body");
        ((ICommand)vm.ExportProfileCommand).CanExecute(null).Should().BeFalse();
        vm.SelectedProfile = vm.ProfileEntries.Single(entry => entry.Name == "Custom Body");
        ((ICommand)vm.ExportProfileCommand).CanExecute(null).Should().BeTrue();

        await vm.ExportProfileCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        File.ReadAllText(exportPath).Should().Contain("\"Name\": \"Custom Body\"");
        vm.StatusMessage.Should().Be("Profile JSON exported.");

        vm.SelectedProfile = vm.ProfileEntries.Single(entry => entry.Name == "Embedded Body");
        ((ICommand)vm.ExportProfileCommand).CanExecute(null).Should().BeTrue();
    }

    /// <summary>
    /// Verifies selected profile JSON export preserves Game metadata from the source custom definition.
    /// </summary>
    [Fact]
    public async Task ExportProfileJsonPreservesSelectedCustomProfileGameMetadata()
    {
        using var directory = new TemporaryDirectory();
        var exportPath = Path.Combine(directory.DirectoryPath, "exported.json");
        var dialog = new StubProfileManagementDialogService { ExportPath = exportPath };
        var localProfile = new CustomProfileDefinition(
            "Custom Body",
            "Skyrim Special Edition",
            CreateSliderProfile(),
            ProfileSourceKind.LocalCustom,
            "custom.json");
        var catalog = new TemplateProfileCatalog(new[]
        {
            new ProfileCatalogEntry("Bundled Body", new TemplateProfile("Bundled Body", CreateSliderProfile()), ProfileSourceKind.Bundled, null, false),
            new ProfileCatalogEntry("Custom Body", new TemplateProfile("Custom Body", localProfile.SliderProfile), ProfileSourceKind.LocalCustom, "custom.json", true)
        });
        var catalogService = new StubTemplateProfileCatalogService(catalog)
        {
            LocalCustomProfilesOverride = [localProfile]
        };
        var vm = CreateManager(catalog, dialog: dialog, catalogService: catalogService);
        vm.SelectedProfile = vm.ProfileEntries.Single(entry => entry.Name == "Custom Body");

        await vm.ExportProfileCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        File.ReadAllText(exportPath).Should().Contain("\"Game\": \"Skyrim Special Edition\"");
    }

    /// <summary>
    /// Verifies newly created custom-profile candidates can be saved from the visible manager-level Save Profile command.
    /// </summary>
    [Fact]
    public async Task CreateBlankProfileCanBeSavedFromVisibleManagerSaveCommand()
    {
        var store = new StubUserProfileStore();
        var catalogService = new StubTemplateProfileCatalogService(new TemplateProfileCatalog(new[]
        {
            new TemplateProfile("Bundled Body", CreateSliderProfile())
        }));
        var vm = CreateManager(catalogService.Current, store: store, catalogService: catalogService);

        vm.CreateBlankProfileCommand.Execute().Subscribe();
        vm.Editor.Name = "Created Body";

        ((ICommand)vm.SaveProfileCommand).CanExecute(null).Should().BeTrue();
        await vm.SaveProfileCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        store.SavedProfiles.Should().ContainSingle(profile => profile.Name == "Created Body");
        vm.StatusMessage.Should().Be("Profile saved.");
        catalogService.RefreshCalls.Should().Be(1);
        vm.Editor.HasUnsavedChanges.Should().BeFalse();
    }

    /// <summary>
    /// Verifies copied bundled profiles save as new local custom profiles without inheriting bundled row file metadata.
    /// </summary>
    [Fact]
    public async Task CopyBundledProfileCanBeSavedAsLocalCustomWithoutFilePath()
    {
        var sourceProfile = new SliderProfile(
            [new SliderDefault("SourceSlider", 0.25f, 0.75f)],
            [new SliderMultiplier("SourceSlider", 1.5f)],
            ["SourceSlider"]);
        var store = new StubUserProfileStore();
        var catalogService = new StubTemplateProfileCatalogService(new TemplateProfileCatalog(new[]
        {
            new TemplateProfile("Bundled Body", sourceProfile)
        }));
        var vm = CreateManager(catalogService.Current, store: store, catalogService: catalogService);
        vm.SelectedProfile = vm.ProfileEntries.Single(entry => entry.Name == "Bundled Body");

        vm.CopyBundledProfileCommand.Execute().Subscribe();
        vm.Editor.Name = "Copied Body";
        await vm.SaveProfileCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        store.SavedProfiles.Should().ContainSingle();
        store.SavedProfiles[0].Name.Should().Be("Copied Body");
        store.SavedProfiles[0].SourceKind.Should().Be(ProfileSourceKind.LocalCustom);
        store.SavedProfiles[0].FilePath.Should().BeNull();
        store.SavedProfiles[0].SliderProfile.Defaults.Should().ContainSingle(row => row.Name == "SourceSlider");
        store.SavedProfiles[0].SliderProfile.Multipliers.Should().ContainSingle(row => row.Name == "SourceSlider");
        store.SavedProfiles[0].SliderProfile.InvertedNames.Should().ContainSingle().Which.Should().Be("SourceSlider");
    }

    /// <summary>
    /// Verifies existing local rows keep their file path while non-local editor states remain blocked from manager save.
    /// </summary>
    [Fact]
    public async Task ManagerSaveKeepsExistingLocalPathAndBlocksInvalidEditors()
    {
        var store = new StubUserProfileStore();
        var catalog = new TemplateProfileCatalog(new[]
        {
            new ProfileCatalogEntry("Bundled Body", new TemplateProfile("Bundled Body", CreateSliderProfile()), ProfileSourceKind.Bundled, null, false),
            new ProfileCatalogEntry("Custom Body", new TemplateProfile("Custom Body", CreateSliderProfile()), ProfileSourceKind.LocalCustom, "custom.json", true),
            new ProfileCatalogEntry("Embedded Body", new TemplateProfile("Embedded Body", CreateSliderProfile()), ProfileSourceKind.EmbeddedProject, null, false)
        });
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("Preset") { ProfileName = "Missing Body" });
        var vm = CreateManager(catalog, project, store);

        vm.SelectedProfile = vm.ProfileEntries.Single(entry => entry.Name == "Custom Body");
        await vm.SaveProfileCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        store.SavedProfiles.Should().ContainSingle(profile => profile.FilePath == "custom.json");

        vm.SelectedProfile = vm.ProfileEntries.Single(entry => entry.Name == "Bundled Body");
        ((ICommand)vm.SaveProfileCommand).CanExecute(null).Should().BeFalse();
        vm.SelectedProfile = vm.ProfileEntries.Single(entry => entry.Name == "Embedded Body");
        ((ICommand)vm.SaveProfileCommand).CanExecute(null).Should().BeFalse();
        vm.SelectedProfile = vm.ProfileEntries.Single(entry => entry.Name == "Missing Body");
        ((ICommand)vm.SaveProfileCommand).CanExecute(null).Should().BeFalse();
        vm.CreateBlankProfileCommand.Execute().Subscribe();
        ((ICommand)vm.SaveProfileCommand).CanExecute(null).Should().BeFalse();
    }

    /// <summary>
    /// Verifies the one-way-to-source ListBox selection path rolls back to the committed row when discard is declined.
    /// </summary>
    [Fact]
    public async Task DeclinedPropertySetSelectionRestoresCommittedRowAndEditor()
    {
        var dialog = new StubProfileManagementDialogService { ConfirmDiscardUnsavedEditsResult = false };
        var catalog = new TemplateProfileCatalog(new[]
        {
            new ProfileCatalogEntry("Bundled Body", new TemplateProfile("Bundled Body", CreateSliderProfile()), ProfileSourceKind.Bundled, null, false),
            new ProfileCatalogEntry("Custom Body", new TemplateProfile("Custom Body", CreateSliderProfile()), ProfileSourceKind.LocalCustom, "custom.json", true)
        });
        var vm = CreateManager(catalog, dialog: dialog);
        vm.SelectedProfile = vm.ProfileEntries.Single(entry => entry.Name == "Custom Body");
        vm.Editor.Name = "Dirty Custom";
        var priorEditor = vm.Editor;

        vm.SelectedProfile = vm.ProfileEntries.Single(entry => entry.Name == "Bundled Body");
        await Task.Yield();

        dialog.ConfirmDiscardUnsavedEditsCalls.Should().Be(1);
        vm.SelectedProfile!.Name.Should().Be("Custom Body");
        vm.Editor.Should().BeSameAs(priorEditor);
    }

    /// <summary>
    /// Verifies profile search refresh preserves dirty editor buffers instead of replacing them with the first visible row.
    /// </summary>
    [Fact]
    public void DirtyEditorSurvivesSearchRefreshWhenSelectedRowIsFilteredOut()
    {
        var catalog = new TemplateProfileCatalog(new[]
        {
            new ProfileCatalogEntry("Bundled Body", new TemplateProfile("Bundled Body", CreateSliderProfile()), ProfileSourceKind.Bundled, null, false),
            new ProfileCatalogEntry("Custom Body", new TemplateProfile("Custom Body", CreateSliderProfile()), ProfileSourceKind.LocalCustom, "custom.json", true)
        });
        var vm = CreateManager(catalog);
        vm.SelectedProfile = vm.ProfileEntries.Single(entry => entry.Name == "Custom Body");
        vm.Editor.Game = "Dirty Game";
        var priorEditor = vm.Editor;

        vm.SearchText = "Bundled";

        vm.SelectedProfile!.Name.Should().Be("Custom Body");
        vm.Editor.Should().BeSameAs(priorEditor);
        vm.Editor.Game.Should().Be("Dirty Game");
    }

    /// <summary>
    /// Verifies catalog refreshes preserve dirty editor buffers while clean editors can be rebuilt from catalog state.
    /// </summary>
    [Fact]
    public void DirtyCatalogRefreshPreservesEditorWhileCleanRefreshRebuildsEditor()
    {
        var catalogService = new StubTemplateProfileCatalogService(new TemplateProfileCatalog(new[]
        {
            new ProfileCatalogEntry("Bundled Body", new TemplateProfile("Bundled Body", CreateSliderProfile()), ProfileSourceKind.Bundled, null, false),
            new ProfileCatalogEntry("Custom Body", new TemplateProfile("Custom Body", CreateSliderProfile()), ProfileSourceKind.LocalCustom, "custom.json", true)
        }));
        var vm = CreateManager(catalogService.Current, catalogService: catalogService);
        vm.SelectedProfile = vm.ProfileEntries.Single(entry => entry.Name == "Custom Body");
        vm.Editor.Game = "Dirty Game";
        var dirtyEditor = vm.Editor;

        catalogService.Refresh();

        vm.SelectedProfile!.Name.Should().Be("Custom Body");
        vm.Editor.Should().BeSameAs(dirtyEditor);
        vm.Editor.Game.Should().Be("Dirty Game");

        vm.Editor.AcceptSaved();
        catalogService.Refresh();

        vm.Editor.Should().NotBeSameAs(dirtyEditor);
        vm.Editor.Name.Should().Be("Custom Body");
    }

    /// <summary>
    /// Verifies profile import read failures become status text without mutating manager state.
    /// </summary>
    [Fact]
    public async Task ImportProfileReadFailureSetsStatusAndPreservesSelectionAndEditor()
    {
        var missingPath = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N"), "missing.json");
        var dialog = new StubProfileManagementDialogService { ImportFiles = [missingPath] };
        var store = new StubUserProfileStore();
        var vm = CreateManager(new TemplateProfileCatalog(new[]
        {
            new ProfileCatalogEntry("Custom Body", new TemplateProfile("Custom Body", CreateSliderProfile()), ProfileSourceKind.LocalCustom, "custom.json", true)
        }), store: store, dialog: dialog);
        var priorSelection = vm.SelectedProfile;
        var priorEditor = vm.Editor;

        var act = async () => await vm.ImportProfileCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        await act.Should().NotThrowAsync();
        store.SavedProfiles.Should().BeEmpty();
        vm.SelectedProfile.Should().BeSameAs(priorSelection);
        vm.Editor.Should().BeSameAs(priorEditor);
        vm.StatusMessage.Should().StartWith("Profile JSON could not be read:");
    }

    /// <summary>
    /// Verifies one import batch rejects duplicate internal names before a later file can overwrite an earlier saved profile.
    /// </summary>
    [Fact]
    public async Task ImportProfileBatchRejectsDuplicateNamesAfterEarlierBatchSave()
    {
        using var directory = new TemporaryDirectory();
        var first = directory.WriteJson("first.json", CreateProfileJson("Imported Body"));
        var second = directory.WriteJson("second.json", CreateProfileJson("Imported Body"));
        var dialog = new StubProfileManagementDialogService { ImportFiles = [first, second] };
        var store = new StubUserProfileStore();
        var catalogService = new StubTemplateProfileCatalogService(new TemplateProfileCatalog(new[]
        {
            new TemplateProfile("Bundled Body", CreateSliderProfile())
        }));
        var vm = CreateManager(catalogService.Current, store: store, dialog: dialog, catalogService: catalogService);

        await vm.ImportProfileCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        store.SavedProfiles.Should().ContainSingle(profile => profile.Name == "Imported Body");
        vm.StatusMessage.Should().Be("Profile name 'Imported Body' conflicts with an existing profile.");
        catalogService.RefreshCalls.Should().Be(1);
    }

    /// <summary>
    /// Verifies missing-reference recovery import read failures preserve the missing row for a later retry.
    /// </summary>
    [Fact]
    public async Task ImportMatchingProfileReadFailureSetsStatusAndKeepsMissingReference()
    {
        var missingPath = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N"), "missing.json");
        var dialog = new StubProfileManagementDialogService { ImportFiles = [missingPath] };
        var store = new StubUserProfileStore();
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("Preset") { ProfileName = "Missing Body" });
        var vm = CreateManager(new TemplateProfileCatalog(new[]
        {
            new TemplateProfile("Bundled Body", CreateSliderProfile())
        }), project, store, dialog);
        var priorEditor = vm.Editor;

        var resolved = await vm.ImportMatchingProfileForMissingReferenceAsync(
            "Missing Body",
            TestContext.Current.CancellationToken);

        resolved.Should().BeFalse();
        store.SavedProfiles.Should().BeEmpty();
        vm.Editor.Should().BeSameAs(priorEditor);
        vm.ProfileEntries.Should().Contain(entry => entry.Name == "Missing Body" && entry.IsMissing);
        vm.StatusMessage.Should().StartWith("Profile JSON could not be read:");
    }

    /// <summary>
    /// Verifies profile export write failures become status text instead of command exceptions.
    /// </summary>
    [Fact]
    public async Task ExportProfileWriteFailureSetsStatusAndPreservesSelection()
    {
        var exportPath = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N"), "exported.json");
        var dialog = new StubProfileManagementDialogService { ExportPath = exportPath };
        var vm = CreateManager(new TemplateProfileCatalog(new[]
        {
            new ProfileCatalogEntry("Custom Body", new TemplateProfile("Custom Body", CreateSliderProfile()), ProfileSourceKind.LocalCustom, "custom.json", true)
        }), dialog: dialog);
        var priorSelection = vm.SelectedProfile;
        var priorEditor = vm.Editor;

        var act = async () => await vm.ExportProfileCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        await act.Should().NotThrowAsync();
        vm.SelectedProfile.Should().BeSameAs(priorSelection);
        vm.Editor.Should().BeSameAs(priorEditor);
        vm.StatusMessage.Should().StartWith("Profile JSON could not be exported:");
    }

    private static ProfileManagerViewModel CreateManager(
        TemplateProfileCatalog catalog,
        ProjectModel? project = null,
        IUserProfileStore? store = null,
        IProfileManagementDialogService? dialog = null,
        ITemplateProfileCatalogService? catalogService = null,
        IProfileReferenceRemapper? remapper = null) =>
        new(
            project ?? new ProjectModel(),
            catalogService ?? new StubTemplateProfileCatalogService(catalog),
            store ?? new StubUserProfileStore(),
            new ProfileDefinitionService(),
            dialog ?? new StubProfileManagementDialogService(),
            remapper);

    private static SliderProfile CreateSliderProfile() => new(
        [new SliderDefault("Slider", 0f, 1f)],
        [new SliderMultiplier("Slider", 1f)],
        []);

    private sealed class StubProfileManagementDialogService : IProfileManagementDialogService
    {
        public bool ConfirmDeleteProfileResult { get; set; } = true;

        public bool ConfirmDeleteReferencedProfileResult { get; set; } = true;

        public bool ConfirmDiscardUnsavedEditsResult { get; set; } = true;

        public IReadOnlyList<string> ImportFiles { get; init; } = [];

        public string? ExportPath { get; init; }

        public string? RemapProfileName { get; init; }

        public int ConfirmDeleteReferencedProfileCalls { get; private set; }

        public int ConfirmDiscardUnsavedEditsCalls { get; private set; }

        public int LastAffectedPresetCount { get; private set; }

        public string? LastRemapMissingProfileName { get; private set; }

        public IReadOnlyList<string> LastRemapInstalledProfileNames { get; private set; } = [];

        public Task<IReadOnlyList<string>> PickProfileImportFilesAsync(CancellationToken cancellationToken) =>
            Task.FromResult(ImportFiles);

        public Task<string?> PickProfileExportPathAsync(string suggestedFileName, CancellationToken cancellationToken) =>
            Task.FromResult(ExportPath);

        public Task<string?> PickInstalledProfileForRemapAsync(
            string missingProfileName,
            IReadOnlyList<string> installedProfileNames,
            CancellationToken cancellationToken)
        {
            LastRemapMissingProfileName = missingProfileName;
            LastRemapInstalledProfileNames = installedProfileNames;
            return Task.FromResult(RemapProfileName);
        }

        public Task<bool> ConfirmDeleteProfileAsync(string profileName, CancellationToken cancellationToken) =>
            Task.FromResult(ConfirmDeleteProfileResult);

        public Task<bool> ConfirmDeleteReferencedProfileAsync(string profileName, int affectedPresetCount, CancellationToken cancellationToken)
        {
            ConfirmDeleteReferencedProfileCalls++;
            LastAffectedPresetCount = affectedPresetCount;
            return Task.FromResult(ConfirmDeleteReferencedProfileResult);
        }

        public Task<bool> ConfirmDiscardUnsavedEditsAsync(CancellationToken cancellationToken)
        {
            ConfirmDiscardUnsavedEditsCalls++;
            return Task.FromResult(ConfirmDiscardUnsavedEditsResult);
        }
    }

    private sealed class StubProfileReferenceRemapper : IProfileReferenceRemapper
    {
        public bool RemapResult { get; init; }

        public List<(string MissingProfileName, string InstalledProfileName)> Requests { get; } = [];

        public bool RemapProfileReferences(string missingProfileName, string installedProfileName)
        {
            Requests.Add((missingProfileName, installedProfileName));
            return RemapResult;
        }
    }

    private sealed class StubUserProfileStore : IUserProfileStore
    {
        public bool SaveSucceeds { get; set; } = true;

        public List<CustomProfileDefinition> DeletedProfiles { get; } = [];

        public List<CustomProfileDefinition> SavedProfiles { get; } = [];

        public UserProfileDiscoveryResult DiscoverProfiles() => DiscoverProfiles([]);

        public UserProfileDiscoveryResult DiscoverProfiles(IEnumerable<string> existingProfileNames) =>
            new([], []);

        public UserProfileSaveResult SaveProfile(CustomProfileDefinition profile)
        {
            if (!SaveSucceeds)
            {
                return new(false, null, [new ProfileValidationDiagnostic(ProfileValidationSeverity.Blocker, "ProfileSaveFailed", "Denied", null, null)]);
            }

            SavedProfiles.Add(profile);
            return new(true, profile.FilePath, []);
        }

        public UserProfileDeleteResult DeleteProfile(CustomProfileDefinition profile)
        {
            DeletedProfiles.Add(profile);
            return new(true, profile.FilePath, []);
        }

        public string GetDefaultProfileDirectory() => string.Empty;
    }

    private sealed class StubTemplateProfileCatalogService(TemplateProfileCatalog catalog) : ITemplateProfileCatalogService, IDisposable
    {
        private readonly System.Reactive.Subjects.BehaviorSubject<TemplateProfileCatalog> changed = new(catalog);

        public TemplateProfileCatalog Current { get; private set; } = catalog;

        public IReadOnlyList<ProfileValidationDiagnostic> LastDiscoveryDiagnostics => [];

        public IReadOnlyList<CustomProfileDefinition> LocalCustomProfiles => LocalCustomProfilesOverride;

        public IReadOnlyList<CustomProfileDefinition> LocalCustomProfilesOverride { get; init; } = [];

        public IReadOnlyList<CustomProfileDefinition> ProjectProfiles => [];

        public IObservable<TemplateProfileCatalog> CatalogChanged => changed;

        public int RefreshCalls { get; private set; }

        public IReadOnlyList<string> ProjectOverlayNames { get; private set; } = [];

        public TemplateProfileCatalog Refresh()
        {
            RefreshCalls++;
            changed.OnNext(Current);
            return Current;
        }

        public TemplateProfileCatalog ClearProjectProfiles() => Current;

        public TemplateProfileCatalog WithProjectProfiles(IEnumerable<CustomProfileDefinition> projectProfiles)
        {
            var profiles = projectProfiles.ToArray();
            ProjectOverlayNames = profiles.Select(profile => profile.Name).ToArray();
            Current = new TemplateProfileCatalog(Current.Entries
                .Where(entry => profiles.All(profile => !string.Equals(profile.Name, entry.Name, StringComparison.OrdinalIgnoreCase)))
                .Concat(profiles.Select(profile => new ProfileCatalogEntry(
                    profile.Name,
                    new TemplateProfile(profile.Name, profile.SliderProfile),
                    ProfileSourceKind.EmbeddedProject,
                    profile.FilePath,
                    false))));
            changed.OnNext(Current);
            return Current;
        }

        public UserProfileSaveResult SaveLocalProfile(CustomProfileDefinition profile) => new(false, null, []);

        public void Dispose() => changed.Dispose();
    }

    private static string CreateProfileJson(string name) => JsonSerializer.Serialize(new
    {
        Version = 1,
        Name = name,
        Game = string.Empty,
        Defaults = new Dictionary<string, float>(),
        Multipliers = new Dictionary<string, float>(),
        Inverted = Array.Empty<string>()
    });

    private sealed class TemporaryDirectory : IDisposable
    {
        private readonly string path = System.IO.Path.Combine(System.IO.Path.GetTempPath(), Guid.NewGuid().ToString("N"));

        public string DirectoryPath => path;

        public TemporaryDirectory() => Directory.CreateDirectory(path);

        public void Dispose() => Directory.Delete(path, true);

        public string WriteJson(string fileName, string json)
        {
            var filePath = System.IO.Path.Combine(path, fileName);
            File.WriteAllText(filePath, json);
            return filePath;
        }
    }
}
