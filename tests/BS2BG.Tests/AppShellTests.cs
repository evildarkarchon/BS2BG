using System.Diagnostics.CodeAnalysis;
using Avalonia.Automation;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Headless.XUnit;
using Avalonia.Input;
using Avalonia.LogicalTree;
using BS2BG.App;
using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.App.Views;
using BS2BG.Core.Models;
using Microsoft.Extensions.DependencyInjection;
using Xunit;

namespace BS2BG.Tests;

[SuppressMessage("Performance", "CA1861:Avoid constant arrays as arguments",
    Justification = "Small expected arrays keep shell assertions readable.")]
public sealed class AppShellTests
{
    [Fact]
    public void ServiceProviderResolvesRootViewModel()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var viewModel = provider.GetRequiredService<MainWindowViewModel>();

        viewModel.Should().NotBeNull();
    }

    [Fact]
    public void ServiceProviderResolvesRootViewModelWithDiagnosticsWorkspace()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var viewModel = provider.GetRequiredService<MainWindowViewModel>();

        viewModel.Diagnostics.Should().BeSameAs(provider.GetRequiredService<DiagnosticsViewModel>());
        viewModel.ActiveWorkspace = AppWorkspace.Diagnostics;
        viewModel.ActiveWorkspace.Should().Be(AppWorkspace.Diagnostics);
    }

    [AvaloniaFact]
    public void ServiceProviderResolvesMainWindowWithPrdShellSettings()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();

        window.ViewModel.Should().BeOfType<MainWindowViewModel>();
        window.Title.Should().Be(AppShell.Title);
        window.Width.Should().Be(AppShell.StartupWidth);
        window.Height.Should().Be(AppShell.StartupHeight);
        window.MinWidth.Should().Be(AppShell.MinWidth);
        window.MinHeight.Should().Be(AppShell.MinHeight);
    }

    [Fact]
    public void StartupSizeMatchesPreferredDefaultWindowCapture()
    {
        AppShell.StartupWidth.Should().Be(1422);
        AppShell.StartupHeight.Should().Be(817);
    }

    [AvaloniaFact]
    public void MainWindowTitleTracksRootViewModelTitle()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();
        var project = provider.GetRequiredService<ProjectModel>();

        project.SliderPresets.Add(new SliderPreset("Alpha"));

        window.Title.Should().Be(AppShell.Title + " *");
    }

    [AvaloniaFact]
    public void MainWindowExposesTemplatesAndMorphsWorkspaces()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();

        var tabHeaders = window.GetLogicalDescendants()
            .OfType<TabItem>()
            .Select(tab => tab.Header?.ToString())
            .ToArray();
        tabHeaders.Should().Contain("Templates");
        tabHeaders.Should().Contain("Morphs");
    }

    [AvaloniaFact]
    public void MainWindowExposesDiagnosticsWorkspaceTabAndActions()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();
        window.ApplyTemplate();

        var tabHeaders = window.GetLogicalDescendants()
            .OfType<TabItem>()
            .Select(tab => tab.Header?.ToString())
            .ToArray();
        tabHeaders.Should().Contain("Diagnostics");
        window.FindControl<Button>("RunDiagnosticsButton")?.Command.Should()
            .BeSameAs(window.ViewModel?.Diagnostics.RefreshDiagnosticsCommand);
        window.FindControl<Button>("CopyDiagnosticsReportButton")?.Command.Should()
            .BeSameAs(window.ViewModel?.Diagnostics.CopyReportCommand);
        AutomationProperties.GetName(window.FindControl<Button>("PreviewNpcImportButton"))
            .Should().Be("Preview NPC Import");
        AutomationProperties.GetName(window.FindControl<Button>("PreviewExportButton"))
            .Should().Be("Preview Export");
    }

    [AvaloniaFact]
    public void MainWindowExposesM5FileAndHelpMenus()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();

        window.GetLogicalDescendants()
            .OfType<MenuItem>()
            .Where(item => item.Name is
                "NewProjectMenuItem"
                or "OpenProjectMenuItem"
                or "SaveProjectMenuItem"
                or "SaveProjectAsMenuItem"
                or "ExportBosJsonMenuItem"
                or "ExportBodyGenInisMenuItem"
                or "AboutMenuItem")
            .Select(item => item.Header?.ToString())
            .ToArray().Should().Equal("New", "Open...", "Save", "Save As...", "Export Templates as BoS JSON",
                "Export BodyGen INIs", "About Bodyslide to Bodygen");
    }

    /// <summary>
    /// Verifies that the Templates workflow exposes the neutral unresolved-profile fallback panel by name and automation label.
    /// </summary>
    [AvaloniaFact]
    public void MainWindowExposesProfileFallbackInformationPanel()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();
        window.ApplyTemplate();

        var panel = window.FindControl<Border>("ProfileFallbackInformationPanel");

        panel.Should().NotBeNull();
        AutomationProperties.GetName(panel).Should().Be("Profile fallback information");
    }

    /// <summary>
    /// Verifies that bundled profile selector names remain exact user-facing labels without confidence qualifiers.
    /// </summary>
    [AvaloniaFact]
    public void MainWindowProfileSelectorUsesBundledDisplayNamesWithoutExperimentalLabel()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();
        var viewModel = window.ViewModel.Should().BeOfType<MainWindowViewModel>().Which;

        viewModel.Templates.ProfileNames.Should().Equal("Skyrim CBBE", "Skyrim UUNP", "Fallout 4 CBBE");
        viewModel.Templates.ProfileNames.Should().OnlyContain(name =>
            !name.Contains("experimental", StringComparison.OrdinalIgnoreCase));
    }

    /// <summary>
    /// Verifies that visible shell copy does not introduce mismatch or experimental warnings in the main workflow.
    /// </summary>
    [AvaloniaFact]
    public void MainWindowWorkflowCopyDoesNotContainMismatchOrExperimentalLanguage()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();
        window.ApplyTemplate();

        var shellText = string.Join("\n", CollectMainWorkflowCopy(window));

        shellText.Contains("experimental", StringComparison.OrdinalIgnoreCase).Should().BeFalse();
        shellText.Contains("mismatch", StringComparison.OrdinalIgnoreCase).Should().BeFalse();
    }

    /// <summary>
    /// Verifies that neutral information brushes exist as resources distinct from warning banner brushes.
    /// </summary>
    [AvaloniaFact]
    public void ThemeResourcesDefineNeutralProfileFallbackBrushesDistinctFromWarnings()
    {
        var resources = File.ReadAllText(Path.GetFullPath(
            Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "..", "..", "src", "BS2BG.App", "Themes", "ThemeResources.axaml")));

        resources.Should().Contain("BS2BGInfoBackgroundBrush");
        resources.Should().Contain("BS2BGInfoBorderBrush");
        resources.Should().Contain("BS2BGInfoForegroundBrush");
        resources.Should().Contain("BS2BGWarningBackgroundBrush");
        resources.Should().Contain("BS2BGWarningBorderBrush");
        resources.Should().Contain("BS2BGWarningForegroundBrush");
        resources.Should().Contain("BS2BGInfoBackgroundBrush\" Color=\"#EEF6FF");
        resources.Should().Contain("BS2BGInfoBackgroundBrush\" Color=\"#102A43");
        resources.Should().NotContain("BS2BGInfoBackgroundBrush\" Color=\"#FFF4CE");
        resources.Should().NotContain("BS2BGInfoBackgroundBrush\" Color=\"#3D2E00");
    }

    [AvaloniaFact]
    public void MainWindowRoutesM5CommandsThroughMenuItemsAndKeyBindings()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();
        var viewModel = window.ViewModel.Should().BeOfType<MainWindowViewModel>().Which;
        window.ApplyTemplate();

        window.FindControl<MenuItem>("NewProjectMenuItem")?.Command.Should().BeSameAs(viewModel.NewProjectCommand);
        window.FindControl<MenuItem>("SaveProjectAsMenuItem")?.Command.Should()
            .BeSameAs(viewModel.SaveProjectAsCommand);
        window.FindControl<MenuItem>("ExportBodyGenInisMenuItem")?.Command.Should()
            .BeSameAs(viewModel.ExportBodyGenInisCommand);
        window.FindControl<TextBlock>("ShellStatusText").Should().NotBeNull();

        window.KeyBindings.Any(binding =>
            ReferenceEquals(binding.Command, viewModel.SaveProjectAsCommand)
            && binding.Gesture?.Key == Key.S
            && binding.Gesture.KeyModifiers == (KeyModifiers.Control | KeyModifiers.Alt)).Should().BeTrue();
        window.KeyBindings.Any(binding =>
            ReferenceEquals(binding.Command, viewModel.ExportBosJsonCommand)
            && binding.Gesture?.Key == Key.B
            && binding.Gesture.KeyModifiers == KeyModifiers.Control).Should().BeTrue();
        window.KeyBindings.Any(binding =>
            ReferenceEquals(binding.Command, viewModel.ExportBodyGenInisCommand)
            && binding.Gesture?.Key == Key.X
            && binding.Gesture.KeyModifiers == KeyModifiers.Control).Should().BeTrue();
    }

    private static IEnumerable<string> CollectMainWorkflowCopy(MainWindow window)
    {
        foreach (var descendant in window.GetLogicalDescendants())
        {
            switch (descendant)
            {
                case TextBlock { Text: { Length: > 0 } text }:
                    yield return text;
                    break;
                case Button { Content: not null } button:
                    yield return button.Content.ToString()!;
                    break;
                case MenuItem { Header: not null } menuItem:
                    yield return menuItem.Header.ToString()!;
                    break;
                case ComboBox comboBox:
                    foreach (var item in comboBox.Items)
                    {
                        if (item is not null)
                        {
                            yield return item.ToString()!;
                        }
                    }

                    break;
            }
        }
    }

    [AvaloniaFact]
    public void MainWindowExposesM4InspectorAndParityActions()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();
        window.Width = AppShell.MinWidth;
        window.Height = AppShell.MinHeight;
        window.ApplyTemplate();
        window.Measure(new Size(AppShell.MinWidth, AppShell.MinHeight));
        window.Arrange(new Rect(0, 0, AppShell.MinWidth, AppShell.MinHeight));

        window.FindControl<ItemsControl>("SetSliderInspectorRows").Should().NotBeNull();
        window.FindControl<TextBox>("BosJsonTextBox").Should().NotBeNull();
        window.FindControl<Button>("ViewImageButton").Should().NotBeNull();
    }

    [Fact]
    public void ServiceProviderResolvesM4WindowServices()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        provider.GetRequiredService<INpcImageLookupService>().Should().BeOfType<NpcImageLookupService>();
        provider.GetRequiredService<IImageViewService>().Should().BeOfType<WindowImageViewService>();
        provider.GetRequiredService<INoPresetNotificationService>().Should()
            .BeOfType<WindowNoPresetNotificationService>();
    }

    [AvaloniaFact]
    public void AboutDialogUsesRequiredCreditsAndSizing()
    {
        var service = new WindowAppDialogService();

        var window = service.CreateAboutWindow();

        window.Title.Should().Be(AppShell.Title);
        window.Width.Should().Be(400);
        window.Height.Should().Be(200);
        window.CanResize.Should().BeFalse();

        var text = string.Join(
            "\n",
            window.GetLogicalDescendants()
                .OfType<TextBlock>()
                .Select(block => block.Text));
        text.Should().Contain("Bodyslide to Bodygen");
        text.Should().Contain("Totiman / asdasfa");
        text.Should().Contain("evildarkarchon");
    }
}
