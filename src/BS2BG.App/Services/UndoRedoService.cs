namespace BS2BG.App.Services;

public sealed class UndoRedoService
{
    public const int DefaultHistoryLimit = 100;

    private readonly int historyLimit;
    private readonly List<UndoRedoOperation> redoHistory = new();
    private readonly List<UndoRedoOperation> undoHistory = new();

    public UndoRedoService(int historyLimit = DefaultHistoryLimit)
    {
        ArgumentOutOfRangeException.ThrowIfNegativeOrZero(historyLimit);

        this.historyLimit = historyLimit;
    }

    public bool IsReplaying { get; private set; }

    public bool CanUndo => undoHistory.Count > 0;

    public bool CanRedo => redoHistory.Count > 0;

    public event EventHandler? StateChanged;

    public event EventHandler? HistoryPruned;

    /// <summary>
    /// Records one undoable user operation unless an undo/redo replay is already in progress.
    /// The history limit is operation-count based so large workflows cannot grow the service without bound.
    /// </summary>
    /// <exception cref="ArgumentNullException">Thrown when <paramref name="undo"/> or <paramref name="redo"/> is null.</exception>
    public void Record(string name, Action undo, Action redo)
    {
        if (IsReplaying) return;

        undoHistory.Add(new UndoRedoOperation(name, undo, redo));
        redoHistory.Clear();
        PruneOldestUndoOperationIfNeeded();
        StateChanged?.Invoke(this, EventArgs.Empty);
    }

    public bool Undo()
    {
        if (undoHistory.Count == 0) return false;

        var operation = PopLast(undoHistory);
        Replay(operation.Undo);
        redoHistory.Add(operation);
        StateChanged?.Invoke(this, EventArgs.Empty);
        return true;
    }

    public bool Redo()
    {
        if (redoHistory.Count == 0) return false;

        var operation = PopLast(redoHistory);
        Replay(operation.Redo);
        undoHistory.Add(operation);
        StateChanged?.Invoke(this, EventArgs.Empty);
        return true;
    }

    public void Clear()
    {
        if (undoHistory.Count == 0 && redoHistory.Count == 0) return;

        undoHistory.Clear();
        redoHistory.Clear();
        StateChanged?.Invoke(this, EventArgs.Empty);
    }

    private static UndoRedoOperation PopLast(List<UndoRedoOperation> history)
    {
        var index = history.Count - 1;
        var operation = history[index];
        history.RemoveAt(index);
        return operation;
    }

    private void PruneOldestUndoOperationIfNeeded()
    {
        if (undoHistory.Count <= historyLimit) return;

        undoHistory.RemoveAt(0);
        HistoryPruned?.Invoke(this, EventArgs.Empty);
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
