using ReactiveUI;

namespace BS2BG.App.ViewModels;

public sealed class MainWindowViewModel : ReactiveObject
{
    private readonly string title = AppShell.Title;

    public string Title => title;
}
