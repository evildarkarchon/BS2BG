global using FluentAssertions;

using System.Runtime.CompilerServices;

namespace BS2BG.Tests;

internal static class FluentAssertionsSetup
{
    [ModuleInitializer]
    internal static void Initialize()
    {
        FluentAssertions.License.Accepted = true;
    }
}
