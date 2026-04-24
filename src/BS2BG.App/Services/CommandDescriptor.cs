using System.Windows.Input;

namespace BS2BG.App.Services;

public sealed class CommandDescriptor(string title, string group, string gestureText, ICommand command)
{
    public string Title { get; } = title ?? throw new ArgumentNullException(nameof(title));

    public string Group { get; } = group ?? throw new ArgumentNullException(nameof(group));

    public string GestureText { get; } = gestureText ?? string.Empty;

    public ICommand Command { get; } = command ?? throw new ArgumentNullException(nameof(command));

    public bool Matches(string searchText)
    {
        if (string.IsNullOrWhiteSpace(searchText)) return true;

        return Contains(Title, searchText)
               || Contains(Group, searchText)
               || Contains(GestureText, searchText);
    }

    private static bool Contains(string value, string searchText) =>
        value.Contains(searchText, StringComparison.OrdinalIgnoreCase);
}
