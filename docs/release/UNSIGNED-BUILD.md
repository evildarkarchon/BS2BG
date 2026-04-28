# Unsigned Build Verification

The portable package may be unsigned when no signing certificate is configured or
when SignTool is unavailable on the release machine. Unsigned artifacts are valid
release artifacts when SHA-256 verification succeeds for both the downloaded zip
and the extracted package files.

## Expected Windows Warning

Windows SmartScreen or antivirus tooling may warn that the publisher is unknown
the first time `BS2BG.App.exe` or `BS2BG.Cli.exe` is launched. This is expected
for an unsigned portable build and does not by itself indicate package
corruption.

## Check Signing Status

After extraction, open `SIGNING-INFO.txt`. It records `Status: Signed` when
Authenticode signing and verification ran successfully. It records `Status:
Unsigned` when signing was not configured or when SignTool was unavailable; in
that case, use the checksum steps below as the trust path.

## Verify The Zip

From the directory containing the downloaded files:

```powershell
$expected = (Get-Content .\BS2BG-v1.0.0-win-x64.zip.sha256).Split(' ')[0]
$actual = (Get-FileHash -Algorithm SHA256 .\BS2BG-v1.0.0-win-x64.zip).Hash.ToLowerInvariant()
$actual -eq $expected
```

The command should print `True`.

## Verify Extracted Files

After extracting the package, run this from the extracted folder:

```powershell
Get-Content .\SHA256SUMS.txt | ForEach-Object {
    $hash, $path = $_ -split ' \*', 2
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash.ToLowerInvariant()
    if ($actual -ne $hash) { throw "Checksum mismatch: $path" }
}
```

No output means the extracted files match the package checksum list. When both
zip and extracted-file checks pass, the unsigned package is valid even though
Windows may still display an unknown-publisher warning.
