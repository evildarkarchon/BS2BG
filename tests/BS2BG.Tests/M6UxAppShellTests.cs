using System.Windows.Input;
using Avalonia.Controls;
using Avalonia.Controls.Primitives;
using Avalonia.Headless.XUnit;
using Avalonia.Input;
using Avalonia.Threading;
using BS2BG.App;
using BS2BG.App.ViewModels;
using BS2BG.App.Views;
using BS2BG.Core.Models;
using Microsoft.Extensions.DependencyInjection;

namespace BS2BG.Tests;

public sealed class M6UxAppShellTests
{
    [AvaloniaFact]
    public void MainWindowExposesM6SearchPaletteThemeAndSelectionControls()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();
        window.ApplyTemplate();

        window.FindControl<TextBox>("GlobalSearchBox").Should().NotBeNull();
        window.FindControl<Popup>("CommandPalettePopup").Should().NotBeNull();
        window.FindControl<ComboBox>("ThemePreferenceComboBox").Should().NotBeNull();
        window.FindControl<MenuItem>("UndoMenuItem").Should().NotBeNull();
        window.FindControl<MenuItem>("RedoMenuItem").Should().NotBeNull();
        window.FindControl<Button>("NpcFilterRaceButton").Should().NotBeNull();
        window.FindControl<Button>("SelectedNpcAssignButton").Should().NotBeNull();
        window.FindControl<Button>("SelectedNpcClearAssignmentsButton").Should().NotBeNull();
    }

    [AvaloniaFact]
    public void TargetPresetWarningBannerTracksWarningState()
    {
        var viewModel = new MainWindowViewModel();
        var window = new MainWindow(viewModel);
        window.ApplyTemplate();
        var banner = window.FindControl<Border>("TargetPresetWarningBanner").Should().BeAssignableTo<Border>().Which;
        var trimButton = window.FindControl<Button>("TargetPresetWarningTrimButton").Should().BeAssignableTo<Button>()
            .Which;

        banner.IsVisible.Should().BeFalse();
        trimButton.IsVisible.Should().BeFalse();

        var warningTarget = CreateTargetWithPresetCount("All|Female", 31);

        viewModel.Morphs.CustomTargets.Add(warningTarget);
        viewModel.Morphs.SelectedCustomTarget = warningTarget;

        banner.IsVisible.Should().BeTrue();
        trimButton.IsVisible.Should().BeFalse();

        for (var index = 31; index < 77; index++) warningTarget.AddSliderPreset(new SliderPreset("P" + index));

        banner.IsVisible.Should().BeTrue();
        trimButton.IsVisible.Should().BeTrue();
    }

    [AvaloniaFact]
    public void NpcRaceFilterButtonOpensPopupAndSelectionFiltersVisibleNpcs()
    {
        var viewModel = new MainWindowViewModel();
        viewModel.Morphs.Npcs.Add(CreateNpc("Lydia", "NordRace"));
        viewModel.Morphs.Npcs.Add(CreateNpc("Serana", "NordRaceVampire"));
        var window = new MainWindow(viewModel);
        window.ApplyTemplate();
        var button = window.FindControl<Button>("NpcFilterRaceButton").Should().BeAssignableTo<Button>().Which;
        var popup = window.FindControl<Popup>("NpcRaceFilterPopup").Should().BeAssignableTo<Popup>().Which;
        var valuesList = window.FindControl<ListBox>("NpcRaceFilterValuesListBox").Should().BeAssignableTo<ListBox>()
            .Which;

        button.Command.Should().BeSameAs(viewModel.Morphs.ToggleNpcRaceFilterCommand);

        button.Command.Should().NotBeNull();
        button.Command.Execute(null);

        popup.IsOpen.Should().BeTrue();

        valuesList.SelectedItems!.Add("NordRaceVampire");

        viewModel.Morphs.VisibleNpcs.Select(npc => npc.Name).Should().Equal("Serana");
    }

    [AvaloniaFact]
    public void NpcColumnFilterPopupsExposeRequiredSearchAndClearControls()
    {
        var viewModel = new MainWindowViewModel();
        var window = new MainWindow(viewModel);
        window.ApplyTemplate();

        AssertFilterPopup(window, "NpcModFilter", "Search mods");
        AssertFilterPopup(window, "NpcNameFilter", "Search names");
        AssertFilterPopup(window, "NpcEditorIdFilter", "Search editor IDs");
        AssertFilterPopup(window, "NpcFormIdFilter", "Search form IDs");
        AssertFilterPopup(window, "NpcRaceFilter", "Search races");
        AssertFilterPopup(window, "NpcAssignmentStateFilter", "Search assignment states");
        AssertFilterPopup(window, "NpcPresetFilter", "Search presets");
    }

    [AvaloniaFact]
    public void NpcColumnFilterPopupsApplyNonRaceChecklistSelections()
    {
        var viewModel = new MainWindowViewModel();
        viewModel.Morphs.Npcs.Add(CreateNpc("Lydia", "NordRace", "Skyrim.esm", "LydiaEditor", "000A2C8E"));
        viewModel.Morphs.Npcs.Add(CreateNpc("Piper", "HumanRace", "Fallout4.esm", "PiperEditor", "00002F1F"));
        var window = new MainWindow(viewModel);
        window.ApplyTemplate();
        var modValuesList = window.FindControl<ListBox>("NpcModFilterValuesListBox").Should().BeAssignableTo<ListBox>().Which;

        modValuesList.SelectedItems!.Add("Fallout4.esm");

        viewModel.Morphs.VisibleNpcs.Select(npc => npc.Name).Should().Equal("Piper");
    }

    [AvaloniaFact]
    public void MainWindowRoutesM6ShortcutsAndDropZones()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();
        var viewModel = window.ViewModel.Should().BeOfType<MainWindowViewModel>().Which;
        window.ApplyTemplate();

        window.KeyBindings.Any(binding =>
            ReferenceEquals(binding.Command, viewModel.FocusGlobalSearchCommand)
            && binding.Gesture?.Key == Key.F
            && binding.Gesture.KeyModifiers == KeyModifiers.Control).Should().BeTrue();
        window.KeyBindings.Any(binding =>
            ReferenceEquals(binding.Command, viewModel.OpenCommandPaletteCommand)
            && binding.Gesture?.Key == Key.P
            && binding.Gesture.KeyModifiers == (KeyModifiers.Control | KeyModifiers.Shift)).Should().BeTrue();

        DragDrop.GetAllowDrop(window.FindControl<Control>("TemplatesDropZone").Should().BeAssignableTo<Control>().Which)
            .Should().BeTrue();
        DragDrop.GetAllowDrop(window.FindControl<Control>("NpcDropZone").Should().BeAssignableTo<Control>().Which)
            .Should().BeTrue();
        viewModel.CommandPaletteItems.Select(item => item.Title).Should().Contain("Generate Templates");
    }

    [AvaloniaFact]
    public void CommandPaletteListBoxSelectionRunsCommandAndClosesPalette()
    {
        var viewModel = new MainWindowViewModel();
        var window = new MainWindow(viewModel);
        window.ApplyTemplate();
        var listBox = window.FindControl<ListBox>("CommandPaletteListBox").Should().BeAssignableTo<ListBox>().Which;

        ((ICommand)viewModel.OpenCommandPaletteCommand).Execute(null);
        viewModel.CommandPaletteSearchText = "Generate Templates";
        var descriptor = viewModel.VisibleCommandPaletteItems.Should().ContainSingle().Which;

        listBox.SelectedItem = descriptor;
        Dispatcher.UIThread.RunJobs();

        viewModel.IsCommandPaletteOpen.Should().BeFalse();
        viewModel.CommandPaletteSearchText.Should().BeEmpty();
    }

    private static void AssertFilterPopup(MainWindow window, string prefix, string placeholder)
    {
        window.FindControl<Button>(prefix + "Button").Should().NotBeNull();
        window.FindControl<Popup>(prefix + "Popup").Should().NotBeNull();
        window.FindControl<ListBox>(prefix + "ValuesListBox").Should().NotBeNull();
        window.FindControl<TextBox>(prefix + "SearchBox").Should().BeAssignableTo<TextBox>().Which
            .PlaceholderText.Should().Be(placeholder);
        window.FindControl<Button>(prefix + "ClearButton").Should().BeAssignableTo<Button>().Which
            .Content.Should().Be("Clear");
    }

    private static Npc CreateNpc(string name, string race) => new(name)
    {
        Mod = "Skyrim.esm", EditorId = name + "Editor", Race = race, FormId = "00000001"
    };

    private static Npc CreateNpc(string name, string race, string mod, string editorId, string formId) => new(name)
    {
        Mod = mod, EditorId = editorId, Race = race, FormId = formId
    };

    private static CustomMorphTarget CreateTargetWithPresetCount(string name, int presetCount)
    {
        var target = new CustomMorphTarget(name);
        for (var index = 0; index < presetCount; index++) target.AddSliderPreset(new SliderPreset("P" + index));

        return target;
    }
}
