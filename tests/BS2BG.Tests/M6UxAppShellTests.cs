using System.Windows.Input;
using Avalonia.Controls;
using Avalonia.Controls.Primitives;
using Avalonia.Headless.XUnit;
using Avalonia.Input;
using Avalonia.Threading;
using BS2BG.App;
using BS2BG.App.ViewModels;
using BS2BG.App.ViewModels.Workflow;
using BS2BG.App.Views;
using BS2BG.Core.Models;
using Microsoft.Extensions.DependencyInjection;

namespace BS2BG.Tests;

public sealed class M6UxAppShellTests
{
    private static readonly string[] NordRaceFilterValues = ["NordRace"];
    private static readonly string[] FalloutModFilterValues = ["Fallout4.esm"];

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
        window.FindControl<Button>("NpcRaceFilterButton").Should().NotBeNull();
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
        var button = window.FindControl<Button>("NpcRaceFilterButton").Should().BeAssignableTo<Button>().Which;
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
    public void NpcColumnFilterPopupsAreAutomationNamedAndLightDismissable()
    {
        var viewModel = new MainWindowViewModel();
        var window = new MainWindow(viewModel);
        window.ApplyTemplate();

        AssertLightDismissableFilter(window, "NpcModFilter", "Filter NPCs by mod");
        AssertLightDismissableFilter(window, "NpcNameFilter", "Filter NPCs by name");
        AssertLightDismissableFilter(window, "NpcEditorIdFilter", "Filter NPCs by editor ID");
        AssertLightDismissableFilter(window, "NpcFormIdFilter", "Filter NPCs by form ID");
        AssertLightDismissableFilter(window, "NpcRaceFilter", "Filter NPCs by race");
        AssertLightDismissableFilter(window, "NpcAssignmentStateFilter", "Filter NPCs by assignment state");
        AssertLightDismissableFilter(window, "NpcPresetFilter", "Filter NPCs by presets");
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
    public void ActiveNpcFiltersShowBadgesAndFilteredEmptyCopy()
    {
        var viewModel = new MainWindowViewModel();
        viewModel.Morphs.Npcs.Add(CreateNpc("Lydia", "NordRace", "Skyrim.esm", "LydiaEditor", "000A2C8E"));
        viewModel.Morphs.Npcs.Add(CreateNpc("Piper", "HumanRace", "Fallout4.esm", "PiperEditor", "00002F1F"));
        var window = new MainWindow(viewModel);
        window.ApplyTemplate();

        viewModel.Morphs.SetNpcColumnAllowedValues(NpcFilterColumn.Race, NordRaceFilterValues);
        viewModel.Morphs.SetNpcColumnAllowedValues(NpcFilterColumn.Mod, FalloutModFilterValues);
        Dispatcher.UIThread.RunJobs();

        window.FindControl<TextBlock>("NpcRaceFilterBadge").Should().BeAssignableTo<TextBlock>().Which
            .Text.Should().Be("Race: 1 selected");
        window.FindControl<TextBlock>("NpcFilteredEmptyHeading").Should().BeAssignableTo<TextBlock>().Which
            .Text.Should().Be("No NPCs match the current filters");
        window.FindControl<TextBlock>("NpcFilteredEmptyBody").Should().BeAssignableTo<TextBlock>().Which.Text.Should()
            .Be("Clear one or more filters to show hidden NPCs, or change the bulk scope before running an action.");
        window.FindControl<Border>("NpcFilteredEmptyState").Should().BeAssignableTo<Border>().Which.IsVisible.Should()
            .BeTrue();
    }

    [AvaloniaFact]
    public void MorphsBulkActionsExposeScopeSelectorAndVisibleEmptyCta()
    {
        var viewModel = new MainWindowViewModel();
        var window = new MainWindow(viewModel);
        window.ApplyTemplate();

        var scopeLabel = window.FindControl<TextBlock>("NpcBulkScopeLabel").Should().BeAssignableTo<TextBlock>().Which;
        var scopeSelector = window.FindControl<ComboBox>("NpcBulkScopeComboBox").Should().BeAssignableTo<ComboBox>().Which;
        var fillButton = window.FindControl<Button>("FillEmptyNpcsButton").Should().BeAssignableTo<Button>().Which;
        var clearButton = window.FindControl<Button>("ClearVisibleNpcsButton").Should().BeAssignableTo<Button>().Which;

        scopeLabel.Text.Should().Be("Scope");
        scopeSelector.ItemsSource.Should().BeSameAs(viewModel.Morphs.NpcBulkScopes);
        scopeSelector.SelectedItem.Should().Be(NpcBulkScope.All);
        scopeSelector.GetValue(Avalonia.Automation.AutomationProperties.NameProperty).Should().Be("Scope");
        viewModel.Morphs.NpcBulkScopes.Select(scope => scope.ToDisplayName())
            .Should().Equal("All", "Visible", "Selected", "Visible Empty");
        fillButton.Content.Should().Be("Fill Visible Empty");
        fillButton.GetValue(Avalonia.Automation.AutomationProperties.NameProperty).Should().Be("Fill Visible Empty");
        clearButton.GetValue(Avalonia.Automation.AutomationProperties.NameProperty).Should().Be("Clear scoped NPCs");
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

    private static void AssertLightDismissableFilter(MainWindow window, string prefix, string automationName)
    {
        window.FindControl<Button>(prefix + "Button").Should().BeAssignableTo<Button>().Which
            .GetValue(Avalonia.Automation.AutomationProperties.NameProperty).Should().Be(automationName);
        window.FindControl<Popup>(prefix + "Popup").Should().BeAssignableTo<Popup>().Which
            .IsLightDismissEnabled.Should().BeTrue();
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
