using Avalonia;
using Avalonia.Automation;
using Avalonia.Controls;
using Avalonia.Headless.XUnit;
using Avalonia.Input;
using Avalonia.Media;
using Avalonia.Styling;
using BS2BG.App;
using BS2BG.App.ViewModels;
using BS2BG.App.Views;
using Microsoft.Extensions.DependencyInjection;
using Xunit;

namespace BS2BG.Tests;

public sealed class M7ReleasePolishTests
{
    [AvaloniaFact]
    public void MainWindowPrimaryControlsExposeAccessibleNamesAndAccelerators()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();
        window.ApplyTemplate();

        Assert.Equal("Search active view", AccessibleName<TextBox>(window, "GlobalSearchBox"));
        Assert.Equal("Ctrl+F", Accelerator<TextBox>(window, "GlobalSearchBox"));
        Assert.Equal("Theme preference", AccessibleName<ComboBox>(window, "ThemePreferenceComboBox"));
        Assert.Equal("Open command palette", AccessibleName<Button>(window, "OpenCommandPaletteButton"));
        Assert.Equal("Ctrl+Shift+P", Accelerator<Button>(window, "OpenCommandPaletteButton"));
        Assert.Equal("New project", AccessibleName<MenuItem>(window, "NewProjectMenuItem"));
        Assert.Equal("Ctrl+N", Accelerator<MenuItem>(window, "NewProjectMenuItem"));

        foreach (var (name, expected) in new[]
                 {
                     ("ImportPresetsButton", "Add BodySlide XML presets"), ("PresetNameInputBox", "Preset name"),
                     ("TemplateProfileComboBox", "Template profile"),
                     ("GenerateTemplatesButton", "Generate templates"),
                     ("GeneratedTemplatesTextBox", "Generated templates"),
                     ("CustomTargetNameInputBox", "Custom target name"), ("ImportNpcsButton", "Import NPCs"),
                     ("ImportedNpcSearchBox", "Search imported NPCs"), ("NpcSearchBox", "Search NPCs"),
                     ("FillEmptyNpcsButton", "Fill empty NPC assignments"), ("NpcListBox", "Individual NPCs"),
                     ("SelectedNpcAssignButton", "Assign preset to selected NPCs"),
                     ("ViewImageButton", "View selected NPC image"),
                     ("AvailablePresetsListBox", "Available presets"),
                     ("AssignedPresetsListBox", "Assigned presets"), ("GeneratedMorphsTextBox", "Generated morphs")
                 })
            Assert.Equal(expected, AccessibleName<Control>(window, name));
    }

    [AvaloniaFact]
    public void MainWindowKeepsPrimaryKeyboardSurfacesFocusableAndCommandBound()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();
        var viewModel = Assert.IsType<MainWindowViewModel>(window.ViewModel);
        window.ApplyTemplate();

        Assert.True(window.FindControl<TextBox>("GlobalSearchBox")?.Focusable);
        Assert.True(window.FindControl<ComboBox>("ThemePreferenceComboBox")?.Focusable);
        Assert.True(window.FindControl<Button>("OpenCommandPaletteButton")?.Focusable);
        Assert.True(window.FindControl<TextBox>("PresetNameInputBox")?.Focusable);
        Assert.True(window.FindControl<TextBox>("NpcSearchBox")?.Focusable);
        Assert.True(window.FindControl<ListBox>("NpcListBox")?.Focusable);

        Assert.Same(viewModel.Templates.ImportPresetsCommand,
            window.FindControl<Button>("ImportPresetsButton")?.Command);
        Assert.Same(viewModel.Templates.GenerateTemplatesCommand,
            window.FindControl<Button>("GenerateTemplatesButton")?.Command);
        Assert.Same(viewModel.Morphs.ImportNpcsCommand, window.FindControl<Button>("ImportNpcsButton")?.Command);
        Assert.Same(viewModel.Morphs.FillEmptyNpcsCommand, window.FindControl<Button>("FillEmptyNpcsButton")?.Command);
        Assert.Same(viewModel.Morphs.GenerateMorphsCommand,
            window.FindControl<Button>("GenerateMorphsButton")?.Command);

        Assert.Contains(window.KeyBindings, binding =>
            ReferenceEquals(binding.Command, viewModel.FocusGlobalSearchCommand)
            && binding.Gesture?.Key == Key.F
            && binding.Gesture.KeyModifiers == KeyModifiers.Control);
        Assert.Contains(window.KeyBindings, binding =>
            ReferenceEquals(binding.Command, viewModel.OpenCommandPaletteCommand)
            && binding.Gesture?.Key == Key.P
            && binding.Gesture.KeyModifiers == (KeyModifiers.Control | KeyModifiers.Shift));
    }

    [AvaloniaFact]
    public void M7ThemeResourcesKeepWarningAndFocusContrastAboveAccessibilityThresholds()
    {
        var application = Assert.IsType<App.App>(Application.Current);

        AssertContrast(application, ThemeVariant.Light, "BS2BGWarningForegroundBrush", "BS2BGWarningBackgroundBrush",
            4.5);
        AssertContrast(application, ThemeVariant.Dark, "BS2BGWarningForegroundBrush", "BS2BGWarningBackgroundBrush",
            4.5);
        AssertContrast(application, ThemeVariant.Light, "BS2BGFocusBrush", "BS2BGWindowBackgroundBrush", 3.0);
        AssertContrast(application, ThemeVariant.Dark, "BS2BGFocusBrush", "BS2BGWindowBackgroundBrush", 3.0);
    }

    private static string? AccessibleName<TControl>(Control root, string name)
        where TControl : Control
    {
        var control = Assert.IsAssignableFrom<TControl>(root.FindControl<Control>(name));
        return AutomationProperties.GetName(control);
    }

    private static string? Accelerator<TControl>(Control root, string name)
        where TControl : Control
    {
        var control = Assert.IsAssignableFrom<TControl>(root.FindControl<Control>(name));
        return AutomationProperties.GetAcceleratorKey(control);
    }

    private static void AssertContrast(
        Application application,
        ThemeVariant theme,
        string foregroundKey,
        string backgroundKey,
        double minimum)
    {
        var foreground = GetBrush(application, foregroundKey, theme);
        var background = GetBrush(application, backgroundKey, theme);
        Assert.True(
            ContrastRatio(foreground.Color, background.Color) >= minimum,
            $"{foregroundKey} must contrast with {backgroundKey} in {theme}.");
    }

    private static SolidColorBrush GetBrush(Application application, string key, ThemeVariant theme)
    {
        Assert.True(application.TryGetResource(key, theme, out var resource), $"Missing resource {key}.");
        return Assert.IsType<SolidColorBrush>(resource);
    }

    private static double ContrastRatio(Color first, Color second)
    {
        var firstLuminance = RelativeLuminance(first);
        var secondLuminance = RelativeLuminance(second);
        var lighter = Math.Max(firstLuminance, secondLuminance);
        var darker = Math.Min(firstLuminance, secondLuminance);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double RelativeLuminance(Color color)
    {
        return 0.2126 * Linearize(color.R)
               + 0.7152 * Linearize(color.G)
               + 0.0722 * Linearize(color.B);
    }

    private static double Linearize(byte channel)
    {
        var value = channel / 255.0;
        return value <= 0.03928
            ? value / 12.92
            : Math.Pow((value + 0.055) / 1.055, 2.4);
    }
}
