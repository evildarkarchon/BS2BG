## 1. Accessibility

- [x] 1.1 Audit tab order, focus visuals, keyboard activation, and automation names across primary workflows
- [x] 1.2 Fix contrast and theme accessibility issues
- [x] 1.3 Add accessibility notes to the manual QA checklist

## 2. Packaging

- [x] 2.1 Add release publish script/configuration for self-contained single-file `win-x64`
- [x] 2.2 Package executable, profiles, fonts/assets, README, credits, and release notes into a portable zip layout
- [x] 2.3 Validate the package from a clean extraction directory without installed .NET or Java runtimes

## 3. Signing And Documentation

- [x] 3.1 Add signing step if a certificate is available, or document unsigned warning expectations
- [x] 3.2 Generate checksums for release artifacts
- [x] 3.3 Write release notes covering parity, known limitations, FO4 profile caveat, and credits

## 4. Release Gates

- [x] 4.1 Measure cold launch time on target Windows 11 hardware and confirm it is under 1.5 seconds
- [x] 4.2 Complete Windows 10/11, DPI, dark/light, and cross-platform launch QA matrix
- [x] 4.3 Run final `dotnet test` and package smoke checks
