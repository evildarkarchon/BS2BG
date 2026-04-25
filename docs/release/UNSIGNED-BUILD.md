# Unsigned Build Verification

The v1.0.0 portable package is intentionally unsigned because no signing
certificate is available for this milestone.

## Expected Windows Warning

Windows SmartScreen or antivirus tooling may warn that the publisher is unknown
the first time `BS2BG.App.exe` is launched. This is expected for an unsigned
portable build and does not by itself indicate package corruption.

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

No output means the extracted files match the package checksum list.
