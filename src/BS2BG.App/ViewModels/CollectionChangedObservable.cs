using System.Collections.Specialized;
using System.Reactive.Linq;

namespace BS2BG.App.ViewModels;

internal static class CollectionChangedObservable
{
    public static IObservable<TResult> Observe<TResult>(
        INotifyCollectionChanged collection,
        Func<TResult> evaluator) =>
        Observable.FromEventPattern<NotifyCollectionChangedEventHandler, NotifyCollectionChangedEventArgs>(
                h => collection.CollectionChanged += h,
                h => collection.CollectionChanged -= h)
            .Select(_ => evaluator())
            .StartWith(evaluator())
            .DistinctUntilChanged();
}
