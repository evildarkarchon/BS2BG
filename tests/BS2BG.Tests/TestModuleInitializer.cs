using System.Reactive.Concurrency;
using System.Runtime.CompilerServices;
using ReactiveUI.Builder;

namespace BS2BG.Tests;

internal static class TestModuleInitializer
{
    [ModuleInitializer]
    public static void Initialize()
    {
        var builder = RxAppBuilder.CreateReactiveUIBuilder();
        builder.WithCoreServices();
        builder
            .WithMainThreadScheduler(ImmediateScheduler.Instance)
            .WithTaskPoolScheduler(ImmediateScheduler.Instance)
            .BuildApp();
    }
}
