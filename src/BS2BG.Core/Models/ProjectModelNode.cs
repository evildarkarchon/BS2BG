using System.ComponentModel;
using System.Runtime.CompilerServices;

namespace BS2BG.Core.Models;

public abstract class ProjectModelNode : INotifyPropertyChanged
{
    internal event EventHandler? Changed;

    public event PropertyChangedEventHandler? PropertyChanged;

    protected bool SetProperty<T>(ref T field, T value, [CallerMemberName] string propertyName = "")
    {
        if (EqualityComparer<T>.Default.Equals(field, value))
        {
            return false;
        }

        field = value;
        NotifyChanged(propertyName);
        return true;
    }

    protected void NotifyChanged([CallerMemberName] string propertyName = "")
    {
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
        Changed?.Invoke(this, EventArgs.Empty);
    }
}
