namespace BS2BG.Core.Morphs;

/// <summary>
/// Deterministic assignment random provider backed by a pinned Mulberry32 implementation.
/// </summary>
/// <remarks>
/// Persisted seed replay must not depend on runtime-specific <see cref="Random" /> behavior. Mulberry32 is small,
/// portable, and stable for project-sharing scenarios where the exact draw sequence is part of the data contract.
/// </remarks>
public sealed class DeterministicAssignmentRandomProvider : IRandomAssignmentProvider
{
    private uint state;

    /// <summary>
    /// Creates a deterministic provider from the persisted signed seed value.
    /// </summary>
    /// <param name="seed">Persisted replay seed; identical seeds produce identical draw sequences.</param>
    public DeterministicAssignmentRandomProvider(int seed)
    {
        state = unchecked((uint)seed);
    }

    /// <summary>
    /// Returns the next deterministic index in the requested exclusive range.
    /// </summary>
    /// <param name="exclusiveMax">Exclusive upper bound for the requested index.</param>
    /// <returns>A deterministic index in the range [0, <paramref name="exclusiveMax" />).</returns>
    public int NextIndex(int exclusiveMax)
    {
        if (exclusiveMax <= 0)
            throw new ArgumentOutOfRangeException(nameof(exclusiveMax), "Exclusive max must be positive.");

        return (int)(NextUInt32() % (uint)exclusiveMax);
    }

    private uint NextUInt32()
    {
        unchecked
        {
            state += 0x6D2B79F5u;
            var value = state;
            value = (value ^ (value >> 15)) * (value | 1u);
            value ^= value + ((value ^ (value >> 7)) * (value | 61u));
            return value ^ (value >> 14);
        }
    }
}
