using BS2BG.Core.Models;

namespace BS2BG.App.ViewModels.Workflow;

/// <summary>
/// Provides App-layer identity and filter-facing accessors for an imported NPC row.
/// Row identity is intentionally generated outside the Core NPC model so it is never serialized into project files.
/// </summary>
public sealed class NpcRowViewModel
{
    /// <summary>
    /// Initializes a new NPC row wrapper with a generated stable UI identity.
    /// </summary>
    /// <param name="npc">The mutable Core NPC model represented by this UI row.</param>
    /// <exception cref="ArgumentNullException">Thrown when <paramref name="npc" /> is null.</exception>
    public NpcRowViewModel(Npc npc)
    {
        Npc = npc ?? throw new ArgumentNullException(nameof(npc));
        RowId = Guid.NewGuid();
    }

    /// <summary>
    /// Gets the stable UI identity used for filtering, selection preservation, and future undo targeting.
    /// </summary>
    public Guid RowId { get; }

    /// <summary>
    /// Gets the mutable Core NPC model; domain/export fields remain owned by Core.
    /// </summary>
    public Npc Npc { get; }

    /// <summary>
    /// Gets the current plugin/mod name from the wrapped NPC.
    /// </summary>
    public string Mod => Npc.Mod;

    /// <summary>
    /// Gets the current display name from the wrapped NPC.
    /// </summary>
    public string Name => Npc.Name;

    /// <summary>
    /// Gets the current editor ID from the wrapped NPC.
    /// </summary>
    public string EditorId => Npc.EditorId;

    /// <summary>
    /// Gets the current normalized form ID from the wrapped NPC.
    /// </summary>
    public string FormId => Npc.FormId;

    /// <summary>
    /// Gets the current race value from the wrapped NPC.
    /// </summary>
    public string Race => Npc.Race;

    /// <summary>
    /// Gets whether this NPC currently has at least one assigned preset.
    /// </summary>
    public bool HasAssignments => Npc.HasPresets;

    /// <summary>
    /// Gets the current pipe-delimited assigned preset names from the wrapped NPC.
    /// </summary>
    public string PresetsText => Npc.SliderPresetsText;
}
