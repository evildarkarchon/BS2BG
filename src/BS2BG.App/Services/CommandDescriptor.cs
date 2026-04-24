using System.Windows.Input;

namespace BS2BG.App.Services;

public sealed class CommandDescriptor
{
    public CommandDescriptor(string title, string group, string gestureText, ICommand command)
    {
        Title = title ?? throw new ArgumentNullException(nameof(title));
        Group = group ?? throw new ArgumentNullException(nameof(group));
        GestureText = gestureText ?? string.Empty;
        Command = command ?? throw new ArgumentNullException(nameof(command));
    }

    public string Title { get; }

    public string Group { get; }

    public string GestureText { get; }

    public ICommand Command { get; }

    public bool Matches(string searchText)
    {
        if (string.IsNullOrWhiteSpace(searchText))
        {
            return true;
        }

        return Contains(Title, searchText)
            || Contains(Group, searchText)
            || Contains(GestureText, searchText);
    }

    private static bool Contains(string value, string searchText)
    {
        return value.Contains(searchText, StringComparison.OrdinalIgnoreCase);
    }
}
