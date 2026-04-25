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

namespace BS2BG.Tests;

public sealed class M7ReleasePolishTests
{
    [AvaloniaFact]
    public void MainWindowPrimaryControlsExposeAccessibleNamesAndAccelerators()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();
        window.ApplyTemplate();

        AccessibleName<TextBox>(window, "GlobalSearchBox").Should().Be("Search active view");
        Accelerator<TextBox>(window, "GlobalSearchBox").Should().Be("Ctrl+F");
        AccessibleName<ComboBox>(window, "ThemePreferenceComboBox").Should().Be("Theme preference");
        AccessibleName<Button>(window, "OpenCommandPaletteButton").Should().Be("Open command palette");
        Accelerator<Button>(window, "OpenCommandPaletteButton").Should().Be("Ctrl+Shift+P");
        AccessibleName<MenuItem>(window, "NewProjectMenuItem").Should().Be("New project");
        Accelerator<MenuItem>(window, "NewProjectMenuItem").Should().Be("Ctrl+N");

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
            AccessibleName<Control>(window, name).Should().Be(expected);
    }

    [AvaloniaFact]
    public void MainWindowKeepsPrimaryKeyboardSurfacesFocusableAndCommandBound()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();
        var viewModel = window.ViewModel.Should().BeOfType<MainWindowViewModel>().Which;
        window.ApplyTemplate();

        window.FindControl<TextBox>("GlobalSearchBox")?.Focusable.Should().BeTrue();
        window.FindControl<ComboBox>("ThemePreferenceComboBox")?.Focusable.Should().BeTrue();
        window.FindControl<Button>("OpenCommandPaletteButton")?.Focusable.Should().BeTrue();
        window.FindControl<TextBox>("PresetNameInputBox")?.Focusable.Should().BeTrue();
        window.FindControl<TextBox>("NpcSearchBox")?.Focusable.Should().BeTrue();
        window.FindControl<ListBox>("NpcListBox")?.Focusable.Should().BeTrue();

        window.FindControl<Button>("ImportPresetsButton")?.Command.Should()
            .BeSameAs(viewModel.Templates.ImportPresetsCommand);
        window.FindControl<Button>("GenerateTemplatesButton")?.Command.Should()
            .BeSameAs(viewModel.Templates.GenerateTemplatesCommand);
        window.FindControl<Button>("ImportNpcsButton")?.Command.Should().BeSameAs(viewModel.Morphs.ImportNpcsCommand);
        window.FindControl<Button>("FillEmptyNpcsButton")?.Command.Should()
            .BeSameAs(viewModel.Morphs.FillEmptyNpcsCommand);
        window.FindControl<Button>("GenerateMorphsButton")?.Command.Should()
            .BeSameAs(viewModel.Morphs.GenerateMorphsCommand);

        window.KeyBindings.Any(binding =>
            ReferenceEquals(binding.Command, viewModel.FocusGlobalSearchCommand)
            && binding.Gesture?.Key == Key.F
            && binding.Gesture.KeyModifiers == KeyModifiers.Control).Should().BeTrue();
        window.KeyBindings.Any(binding =>
            ReferenceEquals(binding.Command, viewModel.OpenCommandPaletteCommand)
            && binding.Gesture?.Key == Key.P
            && binding.Gesture.KeyModifiers == (KeyModifiers.Control | KeyModifiers.Shift)).Should().BeTrue();
    }

    [AvaloniaFact]
    public void M7ThemeResourcesKeepWarningAndFocusContrastAboveAccessibilityThresholds()
    {
        var application = Application.Current.Should().BeOfType<App.App>().Which;

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
        var control = root.FindControl<Control>(name).Should().BeAssignableTo<TControl>().Which;
        return AutomationProperties.GetName(control);
    }

    private static string? Accelerator<TControl>(Control root, string name)
        where TControl : Control
    {
        var control = root.FindControl<Control>(name).Should().BeAssignableTo<TControl>().Which;
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
        ContrastRatio(foreground.Color, background.Color)
            .Should()
            .BeGreaterThanOrEqualTo(minimum, $"{foregroundKey} must contrast with {backgroundKey} in {theme}.");
    }

    private static SolidColorBrush GetBrush(Application application, string key, ThemeVariant theme)
    {
        application.TryGetResource(key, theme, out var resource).Should().BeTrue($"Missing resource {key}.");
        return resource.Should().BeOfType<SolidColorBrush>().Which;
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
