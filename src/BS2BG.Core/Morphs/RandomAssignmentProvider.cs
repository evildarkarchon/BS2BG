namespace BS2BG.Core.Morphs;

public sealed class RandomAssignmentProvider : IRandomAssignmentProvider
{
    private readonly Random random = new();
    private readonly object gate = new();

    public int NextIndex(int exclusiveMax)
    {
        if (exclusiveMax <= 0)
        {
            throw new ArgumentOutOfRangeException(nameof(exclusiveMax), "Exclusive max must be positive.");
        }

        lock (gate)
        {
            return random.Next(exclusiveMax);
        }
    }
}
