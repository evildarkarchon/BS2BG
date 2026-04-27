using System.Collections.ObjectModel;
using System.Globalization;
using System.Reactive;
using System.Reactive.Disposables;
using System.Reactive.Linq;
using BS2BG.App.Services;
using BS2BG.Core.Diagnostics;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using ReactiveUI;
using ReactiveUI.SourceGenerators;

namespace BS2BG.App.ViewModels;

/// <summary>
/// Reactive presentation state for the Diagnostics workspace.
/// Refreshes read-only Core diagnostic reports and exposes grouped findings, counts, selected detail, and navigation intent.
/// </summary>
public sealed partial class DiagnosticsViewModel : ReactiveObject, IDisposable
{
    private static readonly string[] AreaOrder =
    {
        "Project", "Profiles", "Templates", "Morphs/NPCs", "Import", "Export"
    };

    private readonly CompositeDisposable disposables = new();
    private readonly ITemplateProfileCatalogService profileCatalogService;
    private readonly ProfileDiagnosticsService profileDiagnosticsService;
    private readonly ProjectModel project;
    private readonly ProjectValidationService projectValidationService;
    private readonly IClipboardService clipboardService;
    private readonly DiagnosticsReportFormatter reportFormatter;

    [ObservableAsProperty] private bool _hasNavigationIntent;
    [ObservableAsProperty] private bool _isBusy;
    [ObservableAsProperty] private string _selectedDetailText = string.Empty;
    [ObservableAsProperty] private string _selectedNavigationTarget = string.Empty;

    [Reactive] private DiagnosticFindingViewModel? _selectedFinding;

    [Reactive(SetModifier = AccessModifier.Private)] private int _blockerCount;

    [Reactive(SetModifier = AccessModifier.Private)] private int _cautionCount;

    [Reactive(SetModifier = AccessModifier.Private)] private int _infoCount;

    [Reactive(SetModifier = AccessModifier.Private)] private string _profileSummaryText = string.Empty;

    [Reactive(SetModifier = AccessModifier.Private)]
    private string _statusMessage = "No diagnostics yet";

    [Reactive(SetModifier = AccessModifier.Private)]
    private string _summaryText = "No diagnostics yet";

    public DiagnosticsViewModel()
        : this(
            new ProjectModel(),
            CreateDesignTimeProfileCatalog(),
            new ProjectValidationService(),
            new ProfileDiagnosticsService(),
            new EmptyClipboardService(),
            new DiagnosticsReportFormatter())
    {
    }

    /// <summary>
    /// Creates diagnostics presentation state over the supplied project and read-only Core diagnostic services.
    /// </summary>
    public DiagnosticsViewModel(
        ProjectModel project,
        TemplateProfileCatalog profileCatalog,
        ProjectValidationService projectValidationService,
        ProfileDiagnosticsService profileDiagnosticsService,
        IClipboardService? clipboardService = null,
        DiagnosticsReportFormatter? reportFormatter = null)
        : this(
            project,
            new TemplateProfileCatalogService(profileCatalog ?? throw new ArgumentNullException(nameof(profileCatalog))),
            projectValidationService,
            profileDiagnosticsService,
            clipboardService,
            reportFormatter)
    {
    }

    public DiagnosticsViewModel(
        ProjectModel project,
        ITemplateProfileCatalogService profileCatalogService,
        ProjectValidationService projectValidationService,
        ProfileDiagnosticsService profileDiagnosticsService,
        IClipboardService? clipboardService = null,
        DiagnosticsReportFormatter? reportFormatter = null)
    {
        this.project = project ?? throw new ArgumentNullException(nameof(project));
        this.profileCatalogService = profileCatalogService ?? throw new ArgumentNullException(nameof(profileCatalogService));
        this.projectValidationService = projectValidationService
                                        ?? throw new ArgumentNullException(nameof(projectValidationService));
        this.profileDiagnosticsService = profileDiagnosticsService
                                         ?? throw new ArgumentNullException(nameof(profileDiagnosticsService));
        this.clipboardService = clipboardService ?? new EmptyClipboardService();
        this.reportFormatter = reportFormatter ?? new DiagnosticsReportFormatter();

        foreach (var area in AreaOrder) Areas.Add(area);

        var canRefresh = this.WhenAnyValue(x => x.IsBusy).Select(isBusy => !isBusy);
        RefreshDiagnosticsCommand = ReactiveCommand.CreateFromTask(
            (CancellationToken cancellationToken) => RefreshDiagnosticsAsync(cancellationToken),
            canRefresh);
        CopyReportCommand = ReactiveCommand.CreateFromTask(
            (CancellationToken cancellationToken) => CopyReportAsync(cancellationToken),
            canRefresh);

        disposables.Add(RefreshDiagnosticsCommand.ThrownExceptions
            .Subscribe(ex => StatusMessage = "Diagnostics could not be refreshed: " + FormatExceptionMessage(ex)));
        disposables.Add(CopyReportCommand.ThrownExceptions
            .Subscribe(ex => StatusMessage = "Diagnostics report copy failed: " + FormatExceptionMessage(ex)));

        _isBusyHelper = RefreshDiagnosticsCommand.IsExecuting.ToProperty(this, x => x.IsBusy, initialValue: false);
        _selectedDetailTextHelper = this.WhenAnyValue(x => x.SelectedFinding)
            .Select(FormatSelectedDetail)
            .ToProperty(this, x => x.SelectedDetailText, string.Empty);
        _selectedNavigationTargetHelper = this.WhenAnyValue(x => x.SelectedFinding)
            .Select(finding => finding?.NavigationTarget ?? string.Empty)
            .ToProperty(this, x => x.SelectedNavigationTarget, string.Empty);
        _hasNavigationIntentHelper = this.WhenAnyValue(x => x.SelectedFinding)
            .Select(finding => finding?.CanNavigate == true)
            .ToProperty(this, x => x.HasNavigationIntent, initialValue: false);
    }

    public ObservableCollection<DiagnosticFindingViewModel> Findings { get; } = new();

    public ObservableCollection<string> Areas { get; } = new();

    public ObservableCollection<ProfileSliderDiagnostic> ProfileSliderDiagnostics { get; } = new();

    public ReactiveCommand<Unit, Unit> RefreshDiagnosticsCommand { get; }

    public ReactiveCommand<Unit, Unit> CopyReportCommand { get; }

    public void Dispose() => disposables.Dispose();

    /// <summary>
    /// Clears presentation-only diagnostics so a replaced project cannot display findings from an earlier project.
    /// </summary>
    public void ClearReport()
    {
        Findings.Clear();
        ProfileSliderDiagnostics.Clear();
        SelectedFinding = null;
        BlockerCount = 0;
        CautionCount = 0;
        InfoCount = 0;
        SummaryText = "No diagnostics yet";
        ProfileSummaryText = string.Empty;
        StatusMessage = "No diagnostics yet";
    }

    /// <summary>
    /// Refreshes project and profile diagnostic findings using read-only Core services.
    /// The method intentionally avoids dirty/version mutations so diagnostics can be run safely before export.
    /// </summary>
    public Task RefreshDiagnosticsAsync(CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();

        var projectReport = projectValidationService.Validate(project, profileCatalogService.Current);
        var profileReport = profileDiagnosticsService.Analyze(project, profileCatalogService.Current);
        var findings = projectReport.Findings
            .Concat(profileReport.Findings)
            .Select(finding => new DiagnosticFindingViewModel(finding))
            .OrderBy(finding => AreaSortIndex(finding.Area))
            .ThenBy(finding => SeveritySortIndex(finding.Severity))
            .ThenBy(finding => finding.Title, StringComparer.OrdinalIgnoreCase)
            .ToArray();

        Findings.Clear();
        foreach (var finding in findings) Findings.Add(finding);

        ProfileSliderDiagnostics.Clear();
        foreach (var slider in profileReport.SliderDiagnostics) ProfileSliderDiagnostics.Add(slider);

        BlockerCount = findings.Count(finding => finding.Severity == DiagnosticSeverity.Blocker);
        CautionCount = findings.Count(finding => finding.Severity == DiagnosticSeverity.Caution);
        InfoCount = findings.Count(finding => finding.Severity == DiagnosticSeverity.Info);
        SummaryText = BlockerCount > 0
            ? BlockerCount.ToString(CultureInfo.InvariantCulture) + " blocker(s) need attention before output is ready."
            : "No blockers found. Review cautions and info before exporting.";
        ProfileSummaryText = FormatProfileSummary(profileReport.Summary);
        SelectedFinding = Findings.FirstOrDefault();
        StatusMessage = "Diagnostics refreshed.";

        return Task.CompletedTask;
    }

    /// <summary>
    /// Copies the current diagnostics report to the clipboard without changing project data.
    /// </summary>
    public async Task CopyReportAsync(CancellationToken cancellationToken = default)
    {
        var report = reportFormatter.Format(Findings, DateTimeOffset.Now);
        await clipboardService.SetTextAsync(report, cancellationToken);
        StatusMessage = "Diagnostics report copied to clipboard.";
    }

    private static string FormatSelectedDetail(DiagnosticFindingViewModel? finding)
    {
        if (finding is null) return string.Empty;

        var text = finding.Detail;
        if (!string.IsNullOrWhiteSpace(finding.ActionHint)) text += Environment.NewLine + finding.ActionHint;
        return text;
    }

    private static string FormatProfileSummary(ProfileDiagnosticsSummary summary)
    {
        return "Profiles: " + summary.AffectedPresetCount.ToString(CultureInfo.InvariantCulture)
               + " preset(s); " + summary.KnownSliderCount.ToString(CultureInfo.InvariantCulture)
               + " known slider(s); " + summary.UnknownSliderCount.ToString(CultureInfo.InvariantCulture)
               + " unknown slider(s); " + summary.InjectedDefaultCount.ToString(CultureInfo.InvariantCulture)
               + " injected default(s); " + summary.MultiplierCount.ToString(CultureInfo.InvariantCulture)
               + " multiplier(s); " + summary.InversionCount.ToString(CultureInfo.InvariantCulture)
               + " inversion(s).";
    }

    private static int AreaSortIndex(string area)
    {
        var index = Array.FindIndex(AreaOrder, value => string.Equals(value, area, StringComparison.OrdinalIgnoreCase));
        return index >= 0 ? index : AreaOrder.Length;
    }

    private static int SeveritySortIndex(DiagnosticSeverity severity) => severity switch
    {
        DiagnosticSeverity.Blocker => 0,
        DiagnosticSeverity.Caution => 1,
        DiagnosticSeverity.Info => 2,
        _ => 3
    };

    private static string FormatExceptionMessage(Exception exception)
    {
        return string.IsNullOrWhiteSpace(exception.Message)
            ? exception.GetType().Name
            : exception.Message;
    }

    private static TemplateProfileCatalog CreateDesignTimeProfileCatalog()
    {
        return new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(
                ProjectProfileMapping.SkyrimCbbe,
                new BS2BG.Core.Formatting.SliderProfile(
                    Array.Empty<BS2BG.Core.Formatting.SliderDefault>(),
                    Array.Empty<BS2BG.Core.Formatting.SliderMultiplier>(),
                    Array.Empty<string>()))
        });
    }

    private sealed class EmptyClipboardService : IClipboardService
    {
        public Task SetTextAsync(string text, CancellationToken cancellationToken) => Task.CompletedTask;
    }
}
