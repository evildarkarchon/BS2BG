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
        WindowAppDialogService? dialogService = null)
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
    }

    public MainWindowViewModel? ViewModel { get; }

    private void OnWorkspaceSelectionChanged(object? sender, SelectionChangedEventArgs args)
    {
        if (ViewModel is null || sender is not TabControl tabControl) return;

        ViewModel.ActiveWorkspace = tabControl.SelectedIndex == 0
            ? AppWorkspace.Templates
            : AppWorkspace.Morphs;
    }

    private void OnNpcSelectionChanged(object? sender, SelectionChangedEventArgs args)
    {
        if (ViewModel is null || sender is not ListBox listBox) return;

        ViewModel.Morphs.UpdateVisibleNpcSelection(listBox.SelectedItems?.OfType<Npc>() ?? Enumerable.Empty<Npc>());
    }

    private void OnNpcRaceFilterSelectionChanged(object? sender, SelectionChangedEventArgs args)
    {
        if (ViewModel is null || sender is not ListBox listBox) return;

        var selectedRaces = listBox.SelectedItems?.OfType<string>().ToArray() ?? Array.Empty<string>();
        ViewModel.Morphs.SetNpcColumnAllowedValues(NpcFilterColumn.Race, selectedRaces);
    }

    private void OnNpcRaceFilterClearClick(object? sender, RoutedEventArgs args)
    {
        this.FindControl<ListBox>("NpcRaceFilterValuesListBox")?.SelectedItems?.Clear();
        ViewModel?.Morphs.ClearNpcRaceFilter();
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
