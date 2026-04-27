using Avalonia.Automation;
using Avalonia.Controls;
using Avalonia.Headless.XUnit;
using Avalonia.LogicalTree;
using Avalonia.VisualTree;
using System.Windows.Input;
using BS2BG.App;
using BS2BG.App.Views;
using Microsoft.Extensions.DependencyInjection;
using Xunit;

namespace BS2BG.Tests;

public sealed class MainWindowHeadlessTests
{
    [AvaloniaFact]
    public void ProfilesWorkspaceTabAndRequiredControlsArePresent()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();
        var window = provider.GetRequiredService<MainWindow>();
        window.ApplyTemplate();

        var tabHeaders = window.GetLogicalDescendants()
            .OfType<TabItem>()
            .Select(tab => tab.Header?.ToString())
            .ToArray();

        tabHeaders.Should().Contain("Profiles");
        window.FindControl<Button>("ImportProfileButton")?.Content.Should().Be("Import Profile");
        AutomationProperties.GetName(window.FindControl<Button>("ImportProfileButton")!)
            .Should().Be("Import Profile");
        window.FindControl<Button>("ManageProfilesButton")?.Command.Should()
            .BeSameAs(window.ViewModel?.Templates.ManageProfilesCommand);

        window.GetLogicalDescendants()
            .OfType<Button>()
            .Select(AutomationProperties.GetName)
            .Should().Contain("Delete Custom Profile");
    }

    [AvaloniaFact]
    public void ProfilesWorkspaceShowsMissingAndRejectedGroupsEvenWhenEmpty()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();
        var window = provider.GetRequiredService<MainWindow>();
        window.ApplyTemplate();

        var copy = string.Join("\n", window.GetLogicalDescendants()
            .OfType<TextBlock>()
            .Select(block => block.Text)
            .Where(text => !string.IsNullOrWhiteSpace(text)));

        copy.Should().Contain("Missing references");
        copy.Should().Contain("Rejected profile files");
        window.FindControl<ItemsControl>("MissingProfileReferencesList").Should().NotBeNull();
        window.FindControl<ItemsControl>("RejectedProfileFilesList").Should().NotBeNull();
    }

    [AvaloniaFact]
    public void ProfilesWorkspaceSourceRowsExposeSelectableLists()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();
        var window = provider.GetRequiredService<MainWindow>();
        window.ApplyTemplate();

        window.FindControl<ListBox>("BundledProfilesList").Should().NotBeNull();
        window.FindControl<ListBox>("CustomProfilesList").Should().NotBeNull();
        window.FindControl<ListBox>("EmbeddedProjectProfilesList").Should().NotBeNull();
        window.FindControl<ListBox>("MissingProfileReferencesList").Should().NotBeNull();
    }

    [AvaloniaFact]
    public void ProfilesWorkspaceEditorExposesTableAuthoringButtons()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();
        var window = provider.GetRequiredService<MainWindow>();
        window.Show();
        window.ApplyTemplate();
        var tabs = window.GetLogicalDescendants()
            .OfType<TabControl>()
            .Single(control => control.Items.OfType<TabItem>().Any(tab => tab.Header?.ToString() == "Profiles"));
        tabs.SelectedItem = tabs.Items.OfType<TabItem>().Single(tab => tab.Header?.ToString() == "Profiles");

        var editor = window.ViewModel!.Profiles.Editor;
        ExecuteCommand(editor.AddDefaultCommand);
        ExecuteCommand(editor.AddMultiplierCommand);
        ExecuteCommand(editor.AddInvertedCommand);
        window.ApplyTemplate();
        window.UpdateLayout();

        var buttonAutomationNames = window.GetVisualDescendants()
            .OfType<Button>()
            .Select(AutomationProperties.GetName)
            .Where(name => !string.IsNullOrWhiteSpace(name))
            .ToArray();

        buttonAutomationNames.Should().Contain("Add default slider");
        buttonAutomationNames.Should().Contain("Remove default slider");
        buttonAutomationNames.Should().Contain("Add multiplier slider");
        buttonAutomationNames.Should().Contain("Remove multiplier slider");
        buttonAutomationNames.Should().Contain("Add inverted slider");
        buttonAutomationNames.Should().Contain("Remove inverted slider");
    }

    /// <summary>
    /// Verifies profile table ItemsControl bindings target the filtered visible collections used by the editor search box.
    /// </summary>
    [Fact]
    public void ProfilesWorkspaceBindsAllEditableTablesToFilteredRows()
    {
        var axaml = File.ReadAllText(Path.Combine(
            AppContext.BaseDirectory,
            "..",
            "..",
            "..",
            "..",
            "..",
            "src",
            "BS2BG.App",
            "Views",
            "MainWindow.axaml"));

        axaml.Should().Contain("ItemsSource=\"{Binding VisibleDefaultRows}\"");
        axaml.Should().Contain("ItemsSource=\"{Binding VisibleMultiplierRows}\"");
        axaml.Should().Contain("ItemsSource=\"{Binding VisibleInvertedRows}\"");
        axaml.Should().Contain("DataTemplate x:DataType=\"vm:ProfileMultiplierRowViewModel\"");
        axaml.Should().Contain("DataTemplate x:DataType=\"vm:ProfileInvertedRowViewModel\"");
    }

    private static void ExecuteCommand(ICommand command)
    {
        command.CanExecute(null).Should().BeTrue();
        command.Execute(null);
    }
}
