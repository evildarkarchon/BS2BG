using ReactiveUI;

namespace BS2BG.App.ViewModels;

public sealed class MainWindowViewModel : ReactiveObject
{
    private readonly string title = AppShell.Title;

    public MainWindowViewModel()
        : this(new TemplatesViewModel())
    {
    }

    public MainWindowViewModel(TemplatesViewModel templates)
    {
        Templates = templates ?? throw new ArgumentNullException(nameof(templates));
    }

    public string Title => title;

    public TemplatesViewModel Templates { get; }
}
