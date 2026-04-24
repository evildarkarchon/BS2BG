namespace BS2BG.App.Services;

public sealed class UndoRedoService
{
    private readonly Stack<UndoRedoOperation> redoStack = new();
    private readonly Stack<UndoRedoOperation> undoStack = new();

    public bool IsReplaying { get; private set; }

    public bool CanUndo => undoStack.Count > 0;

    public bool CanRedo => redoStack.Count > 0;

    public event EventHandler? StateChanged;

    public void Record(string name, Action undo, Action redo)
    {
        if (IsReplaying) return;

        undoStack.Push(new UndoRedoOperation(name, undo, redo));
        redoStack.Clear();
        StateChanged?.Invoke(this, EventArgs.Empty);
    }

    public bool Undo()
    {
        if (undoStack.Count == 0) return false;

        var operation = undoStack.Pop();
        Replay(operation.Undo);
        redoStack.Push(operation);
        StateChanged?.Invoke(this, EventArgs.Empty);
        return true;
    }

    public bool Redo()
    {
        if (redoStack.Count == 0) return false;

        var operation = redoStack.Pop();
        Replay(operation.Redo);
        undoStack.Push(operation);
        StateChanged?.Invoke(this, EventArgs.Empty);
        return true;
    }

    public void Clear()
    {
        if (undoStack.Count == 0 && redoStack.Count == 0) return;

        undoStack.Clear();
        redoStack.Clear();
        StateChanged?.Invoke(this, EventArgs.Empty);
    }

    private void Replay(Action action)
    {
        IsReplaying = true;
        try
        {
            action();
        }
        finally
        {
            IsReplaying = false;
        }
    }

    private sealed class UndoRedoOperation(string name, Action undo, Action redo)
    {
        public string Name { get; } = name ?? string.Empty;

        public Action Undo { get; } = undo ?? throw new ArgumentNullException(nameof(undo));

        public Action Redo { get; } = redo ?? throw new ArgumentNullException(nameof(redo));
    }
}
