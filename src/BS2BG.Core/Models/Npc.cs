namespace BS2BG.Core.Models;

public sealed class Npc : MorphTargetBase
{
    private string mod = "Skyrim.esm";
    private string editorId = string.Empty;
    private string race = string.Empty;
    private string formId = string.Empty;

    public Npc(string name)
        : base(name)
    {
    }

    public string Mod
    {
        get => mod;
        set => SetProperty(ref mod, value ?? string.Empty);
    }

    public string EditorId
    {
        get => editorId;
        set => SetProperty(ref editorId, value ?? string.Empty);
    }

    public string Race
    {
        get => race;
        set => SetProperty(ref race, value ?? string.Empty);
    }

    public string FormId
    {
        get => formId;
        set => SetProperty(ref formId, NormalizeFormId(value));
    }

    public override string ToMorphLine()
    {
        return Mod + "|" + FormId + "=" + string.Join("|", SliderPresets.Select(sliderPreset => sliderPreset.Name));
    }

    private static string NormalizeFormId(string? value)
    {
        var normalized = (value ?? string.Empty).Trim();

        if (normalized.Length > 6)
        {
            normalized = normalized.Substring(normalized.Length - 6);
        }

        normalized = normalized.TrimStart('0');
        return normalized.Length == 0 ? "0" : normalized;
    }
}
