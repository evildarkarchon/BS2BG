using Avalonia.Controls;
using Avalonia.Controls.Primitives;
using Avalonia.Headless.XUnit;
using Avalonia.Input;
using Avalonia.LogicalTree;
using BS2BG.App;
using BS2BG.App.ViewModels;
using BS2BG.App.Views;
using BS2BG.Core.Models;
using Microsoft.Extensions.DependencyInjection;
using Xunit;

namespace BS2BG.Tests;

public sealed class M6UxAppShellTests
{
    [AvaloniaFact]
    public void MainWindowExposesM6SearchPaletteThemeAndSelectionControls()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();
        window.ApplyTemplate();

        Assert.NotNull(window.FindControl<TextBox>("GlobalSearchBox"));
        Assert.NotNull(window.FindControl<Popup>("CommandPalettePopup"));
        Assert.NotNull(window.FindControl<ComboBox>("ThemePreferenceComboBox"));
        Assert.NotNull(window.FindControl<MenuItem>("UndoMenuItem"));
        Assert.NotNull(window.FindControl<MenuItem>("RedoMenuItem"));
        Assert.NotNull(window.FindControl<Button>("NpcFilterRaceButton"));
        Assert.NotNull(window.FindControl<Button>("SelectedNpcAssignButton"));
        Assert.NotNull(window.FindControl<Button>("SelectedNpcClearAssignmentsButton"));
    }

    [AvaloniaFact]
    public void TargetPresetWarningBannerTracksWarningState()
    {
        var viewModel = new MainWindowViewModel();
        var window = new MainWindow(viewModel);
        window.ApplyTemplate();
        var banner = Assert.IsAssignableFrom<Border>(window.FindControl<Border>("TargetPresetWarningBanner"));
        var trimButton = Assert.IsAssignableFrom<Button>(window.FindControl<Button>("TargetPresetWarningTrimButton"));

        Assert.False(banner.IsVisible);
        Assert.False(trimButton.IsVisible);

        var warningTarget = CreateTargetWithPresetCount("All|Female", 31);

        viewModel.Morphs.CustomTargets.Add(warningTarget);
        viewModel.Morphs.SelectedCustomTarget = warningTarget;

        Assert.True(banner.IsVisible);
        Assert.False(trimButton.IsVisible);

        for (var index = 31; index < 77; index++)
        {
            warningTarget.AddSliderPreset(new SliderPreset("P" + index));
        }

        Assert.True(banner.IsVisible);
        Assert.True(trimButton.IsVisible);
    }

    [AvaloniaFact]
    public void NpcRaceFilterButtonOpensPopupAndSelectionFiltersVisibleNpcs()
    {
        var viewModel = new MainWindowViewModel();
        viewModel.Morphs.Npcs.Add(CreateNpc("Lydia", "NordRace"));
        viewModel.Morphs.Npcs.Add(CreateNpc("Serana", "NordRaceVampire"));
        var window = new MainWindow(viewModel);
        window.ApplyTemplate();
        var button = Assert.IsAssignableFrom<Button>(window.FindControl<Button>("NpcFilterRaceButton"));
        var popup = Assert.IsAssignableFrom<Popup>(window.FindControl<Popup>("NpcRaceFilterPopup"));
        var valuesList = Assert.IsAssignableFrom<ListBox>(window.FindControl<ListBox>("NpcRaceFilterValuesListBox"));

        Assert.Same(viewModel.Morphs.ToggleNpcRaceFilterCommand, button.Command);

        Assert.NotNull(button.Command);
        button.Command.Execute(null);

        Assert.True(popup.IsOpen);

        valuesList.SelectedItems!.Add("NordRaceVampire");

        Assert.Equal(["Serana"], viewModel.Morphs.VisibleNpcs.Select(npc => npc.Name));
    }

    [AvaloniaFact]
    public void MainWindowRoutesM6ShortcutsAndDropZones()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();
        var viewModel = Assert.IsType<MainWindowViewModel>(window.ViewModel);
        window.ApplyTemplate();

        Assert.Contains(window.KeyBindings, binding =>
            ReferenceEquals(binding.Command, viewModel.FocusGlobalSearchCommand)
            && binding.Gesture?.Key == Key.F
            && binding.Gesture.KeyModifiers == KeyModifiers.Control);
        Assert.Contains(window.KeyBindings, binding =>
            ReferenceEquals(binding.Command, viewModel.OpenCommandPaletteCommand)
            && binding.Gesture?.Key == Key.P
            && binding.Gesture.KeyModifiers == (KeyModifiers.Control | KeyModifiers.Shift));

        Assert.True(DragDrop.GetAllowDrop(Assert.IsAssignableFrom<Control>(
            window.FindControl<Control>("TemplatesDropZone"))));
        Assert.True(DragDrop.GetAllowDrop(Assert.IsAssignableFrom<Control>(
            window.FindControl<Control>("NpcDropZone"))));
        Assert.Contains(
            "Generate Templates",
            viewModel.CommandPaletteItems.Select(item => item.Title));
    }

    private static Npc CreateNpc(string name, string race)
    {
        return new Npc(name)
        {
            Mod = "Skyrim.esm",
            EditorId = name + "Editor",
            Race = race,
            FormId = "00000001",
        };
    }

    private static CustomMorphTarget CreateTargetWithPresetCount(string name, int presetCount)
    {
        var target = new CustomMorphTarget(name);
        for (var index = 0; index < presetCount; index++)
        {
            target.AddSliderPreset(new SliderPreset("P" + index));
        }

        return target;
    }
}
