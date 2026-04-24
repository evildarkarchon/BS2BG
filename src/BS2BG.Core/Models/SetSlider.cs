namespace BS2BG.Core.Models;

public sealed class SetSlider : ProjectModelNode
{
    private string name;
    private bool enabled = true;
    private int? valueSmall;
    private int? valueBig;
    private int percentMin = 100;
    private int percentMax = 100;

    public SetSlider(string name)
    {
        this.name = name ?? throw new ArgumentNullException(nameof(name));
    }

    public string Name
    {
        get => name;
        set => SetProperty(ref name, value ?? throw new ArgumentNullException(nameof(value)));
    }

    public bool Enabled
    {
        get => enabled;
        set => SetProperty(ref enabled, value);
    }

    public int? ValueSmall
    {
        get => valueSmall;
        set => SetProperty(ref valueSmall, value);
    }

    public int? ValueBig
    {
        get => valueBig;
        set => SetProperty(ref valueBig, value);
    }

    public int PercentMin
    {
        get => percentMin;
        set => SetProperty(ref percentMin, value);
    }

    public int PercentMax
    {
        get => percentMax;
        set => SetProperty(ref percentMax, value);
    }

    public bool IsMissingDefault => ValueSmall is null && ValueBig is null;
}
