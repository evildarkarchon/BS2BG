namespace BS2BG.App.Services;

public sealed class UndoRedoService
{
    private readonly Stack<UndoRedoOperation> undoStack = new();
    private readonly Stack<UndoRedoOperation> redoStack = new();
    private bool isReplaying;

    public event EventHandler? StateChanged;

    public bool IsReplaying => isReplaying;

    public bool CanUndo => undoStack.Count > 0;

    public bool CanRedo => redoStack.Count > 0;

    public void Record(string name, Action undo, Action redo)
    {
        if (isReplaying)
        {
            return;
        }

        undoStack.Push(new UndoRedoOperation(name, undo, redo));
        redoStack.Clear();
        StateChanged?.Invoke(this, EventArgs.Empty);
    }

    public bool Undo()
    {
        if (undoStack.Count == 0)
        {
            return false;
        }

        var operation = undoStack.Pop();
        Replay(operation.Undo);
        redoStack.Push(operation);
        StateChanged?.Invoke(this, EventArgs.Empty);
        return true;
    }

    public bool Redo()
    {
        if (redoStack.Count == 0)
        {
            return false;
        }

        var operation = redoStack.Pop();
        Replay(operation.Redo);
        undoStack.Push(operation);
        StateChanged?.Invoke(this, EventArgs.Empty);
        return true;
    }

    public void Clear()
    {
        if (undoStack.Count == 0 && redoStack.Count == 0)
        {
            return;
        }

        undoStack.Clear();
        redoStack.Clear();
        StateChanged?.Invoke(this, EventArgs.Empty);
    }

    private void Replay(Action action)
    {
        isReplaying = true;
        try
        {
            action();
        }
        finally
        {
            isReplaying = false;
        }
    }

    private sealed class UndoRedoOperation
    {
        public UndoRedoOperation(string name, Action undo, Action redo)
        {
            Name = name ?? string.Empty;
            Undo = undo ?? throw new ArgumentNullException(nameof(undo));
            Redo = redo ?? throw new ArgumentNullException(nameof(redo));
        }

        public string Name { get; }

        public Action Undo { get; }

        public Action Redo { get; }
    }
}
