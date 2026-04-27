using System.Windows.Input;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Interactivity;
using Avalonia.Threading;
using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.App.ViewModels.Workflow;
using BS2BG.Core.Models;

namespace BS2BG.App.Views;

public partial class MainWindow : Window
{
    public MainWindow()
        : this(new MainWindowViewModel())
    {
    }

    public MainWindow(
        MainWindowViewModel viewModel,
        WindowBodySlideXmlFilePicker? filePicker = null,
        WindowNpcTextFilePicker? npcTextFilePicker = null,
        WindowClipboardService? clipboardService = null,
        WindowImageViewService? imageViewService = null,
        WindowNoPresetNotificationService? noPresetNotificationService = null,
        WindowFileDialogService? fileDialogService = null,
        WindowAppDialogService? dialogService = null,
        ProfileManagementDialogService? profileManagementDialogService = null)
    {
        InitializeComponent();

        ViewModel = viewModel;
        DataContext = viewModel;
        Title = viewModel.Title;
        viewModel.PropertyChanged += (_, args) =>
        {
            if (args.PropertyName == nameof(MainWindowViewModel.Title))
            {
                Title = viewModel.Title;
            }
            else if (args.PropertyName == nameof(MainWindowViewModel.ShouldFocusGlobalSearch)
                     && viewModel.ShouldFocusGlobalSearch)
            {
                this.FindControl<TextBox>("GlobalSearchBox")?.Focus();
                viewModel.AcknowledgeGlobalSearchFocus();
            }
        };

        Width = AppShell.StartupWidth;
        Height = AppShell.StartupHeight;
        MinWidth = AppShell.MinWidth;
        MinHeight = AppShell.MinHeight;
        filePicker?.Attach(this);
        npcTextFilePicker?.Attach(this);
        clipboardService?.Attach(this);
        imageViewService?.Attach(this);
        noPresetNotificationService?.Attach(this);
        fileDialogService?.Attach(this);
        dialogService?.Attach(this);
        profileManagementDialogService?.Attach(this);
    }

    public MainWindowViewModel? ViewModel { get; }

    private void OnWorkspaceSelectionChanged(object? sender, SelectionChangedEventArgs args)
    {
        if (ViewModel is null || sender is not TabControl tabControl) return;

        ViewModel.ActiveWorkspace = tabControl.SelectedIndex switch
        {
            0 => AppWorkspace.Templates,
            1 => AppWorkspace.Morphs,
            2 => AppWorkspace.Diagnostics,
            _ => AppWorkspace.Profiles
        };
    }

    private void OnNpcSelectionChanged(object? sender, SelectionChangedEventArgs args)
    {
        if (ViewModel is null || sender is not ListBox listBox) return;

        ViewModel.Morphs.UpdateVisibleNpcSelection(listBox.SelectedItems?.OfType<Npc>() ?? Enumerable.Empty<Npc>());
    }

    private void OnNpcRaceFilterSelectionChanged(object? sender, SelectionChangedEventArgs args)
    {
        ApplyNpcColumnFilterSelection(sender, NpcFilterColumn.Race);
    }

    private void OnNpcModFilterSelectionChanged(object? sender, SelectionChangedEventArgs args) =>
        ApplyNpcColumnFilterSelection(sender, NpcFilterColumn.Mod);

    private void OnNpcNameFilterSelectionChanged(object? sender, SelectionChangedEventArgs args) =>
        ApplyNpcColumnFilterSelection(sender, NpcFilterColumn.Name);

    private void OnNpcEditorIdFilterSelectionChanged(object? sender, SelectionChangedEventArgs args) =>
        ApplyNpcColumnFilterSelection(sender, NpcFilterColumn.EditorId);

    private void OnNpcFormIdFilterSelectionChanged(object? sender, SelectionChangedEventArgs args) =>
        ApplyNpcColumnFilterSelection(sender, NpcFilterColumn.FormId);

    private void OnNpcAssignmentStateFilterSelectionChanged(object? sender, SelectionChangedEventArgs args) =>
        ApplyNpcColumnFilterSelection(sender, NpcFilterColumn.AssignmentState);

    private void OnNpcPresetFilterSelectionChanged(object? sender, SelectionChangedEventArgs args) =>
        ApplyNpcColumnFilterSelection(sender, NpcFilterColumn.Preset);

    /// <summary>
    /// Forwards checklist selections from view-owned ListBox controls into the ViewModel filter state.
    /// Avalonia multi-selection is a control collection rather than an ICommand parameter, so this code-behind remains view-only glue.
    /// </summary>
    /// <param name="sender">The checklist ListBox that raised the selection event.</param>
    /// <param name="column">The filter column represented by that ListBox.</param>
    private void ApplyNpcColumnFilterSelection(object? sender, NpcFilterColumn column)
    {
        if (ViewModel is null || sender is not ListBox listBox) return;

        var selectedValues = listBox.SelectedItems?.OfType<string>().ToArray() ?? Array.Empty<string>();
        ViewModel.Morphs.SetNpcColumnAllowedValues(column, selectedValues);
    }

    private void OnNpcRaceFilterClearClick(object? sender, RoutedEventArgs args)
    {
        this.FindControl<ListBox>("NpcRaceFilterValuesListBox")?.SelectedItems?.Clear();
        ViewModel?.Morphs.ClearNpcRaceFilter();
    }

    private void OnNpcModFilterClearClick(object? sender, RoutedEventArgs args) =>
        ClearNpcColumnFilter("NpcModFilterValuesListBox", NpcFilterColumn.Mod);

    private void OnNpcNameFilterClearClick(object? sender, RoutedEventArgs args) =>
        ClearNpcColumnFilter("NpcNameFilterValuesListBox", NpcFilterColumn.Name);

    private void OnNpcEditorIdFilterClearClick(object? sender, RoutedEventArgs args) =>
        ClearNpcColumnFilter("NpcEditorIdFilterValuesListBox", NpcFilterColumn.EditorId);

    private void OnNpcFormIdFilterClearClick(object? sender, RoutedEventArgs args) =>
        ClearNpcColumnFilter("NpcFormIdFilterValuesListBox", NpcFilterColumn.FormId);

    private void OnNpcAssignmentStateFilterClearClick(object? sender, RoutedEventArgs args) =>
        ClearNpcColumnFilter("NpcAssignmentStateFilterValuesListBox", NpcFilterColumn.AssignmentState);

    private void OnNpcPresetFilterClearClick(object? sender, RoutedEventArgs args) =>
        ClearNpcColumnFilter("NpcPresetFilterValuesListBox", NpcFilterColumn.Preset);

    /// <summary>
    /// Clears both the view selection collection and the matching ViewModel checklist filter.
    /// </summary>
    /// <param name="listBoxName">The named checklist ListBox to clear.</param>
    /// <param name="column">The filter column to clear in the ViewModel.</param>
    private void ClearNpcColumnFilter(string listBoxName, NpcFilterColumn column)
    {
        this.FindControl<ListBox>(listBoxName)?.SelectedItems?.Clear();
        ViewModel?.Morphs.ClearNpcColumnFilter(column);
    }

    private void OnCommandPaletteSelectionChanged(object? sender, SelectionChangedEventArgs args)
    {
        if (ViewModel is null || sender is not ListBox listBox) return;

        if (listBox.SelectedItem is CommandDescriptor descriptor)
        {
            // Defer until after SelectionChanged finishes — running the command synchronously here
            // mutates VisibleCommandPaletteItems (the ListBox's ItemsSource) and corrupts its selection
            // bookkeeping mid-event.
            Dispatcher.UIThread.Post(() =>
            {
                if (ViewModel is null) return;
                ((ICommand)ViewModel.RunCommandPaletteItemCommand).Execute(descriptor);
                listBox.SelectedItem = null;
            });
        }
    }

    private void OnDragOver(object? sender, DragEventArgs args)
    {
        args.DragEffects = args.DataTransfer.TryGetFiles() is { Length: > 0 }
            ? DragDropEffects.Copy
            : DragDropEffects.None;
        args.Handled = true;
    }

    private void OnDrop(object? sender, DragEventArgs args)
    {
        if (ViewModel is null) return;

        args.Handled = true;

        var paths = args.DataTransfer.TryGetFiles()?
            .Where(file => file.Path.IsFile)
            .Select(file => file.Path.LocalPath)
            .ToArray() ?? Array.Empty<string>();
        DispatchDroppedFilePaths(ViewModel, paths);
    }

    internal static void DispatchDroppedFilePaths(MainWindowViewModel viewModel, IReadOnlyList<string> paths)
    {
        if (viewModel.IsAnyBusy)
        {
            viewModel.NotifyDropIgnoredAsBusy();
            return;
        }

        viewModel.HandleDroppedFilesCommand.Execute(paths).Subscribe(_ => { }, _ => { });
    }
}
