namespace BS2BG.Core.Morphs;

public interface IRandomAssignmentProvider
{
    int NextIndex(int exclusiveMax);
}
