#Requires -Modules @{ ModuleName = 'Pester'; ModuleVersion = '5.0.0' }
<#
.SYNOPSIS
    Pester tests for the deterministic parts of WindowsAppImage.psm1 (issue #97).

.DESCRIPTION
    jlink, jpackage, and the packaged launcher are exercised only by running tools/java25/package-java25.ps1 end
    to end. Everything that decides whether the staged payload, the measured module closure, the launcher
    configuration, the image layout, or the runtime release is accepted is covered here on synthetic trees, so a
    regression is caught before a runtime is linked.

    Run with:  Invoke-Pester -Path tools/java25 -Output Detailed
#>

BeforeAll {
    Import-Module (Join-Path $PSScriptRoot 'WindowsAppImage.psm1') -Force

    # Creates a file (and its parent directories) under TestDrive and returns its path.
    function New-TreeFile {
        param([string]$Relative, [string]$Content = 'x')
        $path = Join-Path $TestDrive $Relative
        New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force | Out-Null
        Set-Content -LiteralPath $path -Value $Content -NoNewline -Encoding utf8
        return $path
    }

    # Builds a jar (zip) whose entries are the given relative path -> content pairs.
    function New-TestJar {
        param([string]$Path, [hashtable]$Entries)
        $stage = Join-Path $TestDrive ("jarstage-" + [guid]::NewGuid().ToString('N'))
        foreach ($entry in $Entries.Keys) {
            $file = Join-Path $stage $entry
            New-Item -ItemType Directory -Path (Split-Path -Parent $file) -Force | Out-Null
            Set-Content -LiteralPath $file -Value $Entries[$entry] -NoNewline -Encoding utf8
        }
        New-Item -ItemType Directory -Path (Split-Path -Parent $Path) -Force | Out-Null
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::CreateFromDirectory($stage, $Path)
        return $Path
    }
}

Describe 'Get-StagedApplication' {
    It 'returns the single application jar and every lib/ dependency' {
        $root = Join-Path $TestDrive 'staged\ok'
        New-TreeFile 'staged\ok\app-1.0.jar' | Out-Null
        New-TreeFile 'staged\ok\lib\b.jar' | Out-Null
        New-TreeFile 'staged\ok\lib\a.jar' | Out-Null
        $staged = Get-StagedApplication -StagingDir $root
        $staged.MainJarName | Should -Be 'app-1.0.jar'
        $staged.LibJarNames | Should -Be @('a.jar', 'b.jar')
        $staged.LibJars | Should -HaveCount 2
    }

    It 'fails closed when the staging directory holds no or several top-level jars' {
        $none = Join-Path $TestDrive 'staged\none'
        New-TreeFile 'staged\none\lib\a.jar' | Out-Null
        { Get-StagedApplication -StagingDir $none } | Should -Throw -ExpectedMessage '*exactly one*'
        New-TreeFile 'staged\two\x.jar' | Out-Null
        New-TreeFile 'staged\two\y.jar' | Out-Null
        { Get-StagedApplication -StagingDir (Join-Path $TestDrive 'staged\two') } | Should -Throw -ExpectedMessage '*exactly one*'
    }

    It 'fails closed when a JavaFX jar has been staged onto the classpath' {
        New-TreeFile 'staged\fx\app.jar' | Out-Null
        New-TreeFile 'staged\fx\lib\javafx-base-25.0.4-win.jar' | Out-Null
        { Get-StagedApplication -StagingDir (Join-Path $TestDrive 'staged\fx') } | Should -Throw -ExpectedMessage '*javafx-base-25.0.4-win.jar*'
    }
}

Describe 'ConvertFrom-JdepsModuleDeps' {
    It 'parses the comma-separated module line into a sorted unique list' {
        ConvertFrom-JdepsModuleDeps -Output @('java.xml,java.base,javafx.fxml,java.base') | Should -Be @('java.base', 'java.xml', 'javafx.fxml')
    }

    It 'ignores jdeps warnings and blank lines around the module line' {
        $output = @('Warning: split package: javafx.foo', '', 'java.base,javafx.controls', '')
        ConvertFrom-JdepsModuleDeps -Output $output | Should -Be @('java.base', 'javafx.controls')
    }

    It 'fails closed on jdeps errors or output without a module line' {
        { ConvertFrom-JdepsModuleDeps -Output @('Error: Module javafx.fxml not found') } | Should -Throw -ExpectedMessage '*Error: Module javafx.fxml not found*'
        { ConvertFrom-JdepsModuleDeps -Output @() } | Should -Throw -ExpectedMessage '*no module line*'
        { ConvertFrom-JdepsModuleDeps -Output @('java.base', 'java.xml') } | Should -Throw -ExpectedMessage '*exactly one*'
    }
}

Describe 'Resolve-RuntimeModules' {
    BeforeAll {
        $script:Pinned = @('javafx.base', 'javafx.graphics', 'javafx.controls', 'javafx.fxml')
    }

    It 'unions the measured closure with the documented explicit additions, sorted' {
        $resolved = Resolve-RuntimeModules -MeasuredModules @('javafx.fxml', 'java.base', 'javafx.controls') -ExplicitModules ([ordered]@{ 'jdk.charsets' = 'extended charsets' }) -PinnedJavaFxModules $script:Pinned
        $resolved.Modules | Should -Be @('java.base', 'javafx.controls', 'javafx.fxml', 'jdk.charsets')
        $resolved.Measured | Should -Be @('java.base', 'javafx.controls', 'javafx.fxml')
        $resolved.Explicit['jdk.charsets'] | Should -Be 'extended charsets'
    }

    It 'fails closed when the measurement saw no JavaFX module at all' {
        { Resolve-RuntimeModules -MeasuredModules @('java.base', 'java.xml') -ExplicitModules @{} -PinnedJavaFxModules $script:Pinned } |
            Should -Throw -ExpectedMessage '*no javafx*'
    }

    It 'fails closed when the application needs a JavaFX module the lock does not pin' {
        { Resolve-RuntimeModules -MeasuredModules @('java.base', 'javafx.controls', 'javafx.web') -ExplicitModules @{} -PinnedJavaFxModules $script:Pinned } |
            Should -Throw -ExpectedMessage '*javafx.web*'
    }
}

Describe 'Read-LauncherConfig and Assert-LauncherConfig' {
    BeforeAll {
        # The exact shape jpackage 25 generates: the main jar is the first classpath entry, the version travels as
        # a -Djpackage.app-version option, and the bundled runtime is implicit (no app.runtime key).
        $script:CfgText = @"
[Application]
app.classpath=`$APPDIR\app-1.0.jar
app.mainclass=com.asdasfa.jbs2bg.Launcher
app.classpath=`$APPDIR\lib\a.jar
app.classpath=`$APPDIR\lib\b.jar

[JavaOptions]
java-options=-Djpackage.app-version=1.1.2
java-options=--enable-native-access=javafx.graphics
"@
        $script:CfgPath = New-TreeFile 'cfg\BS2BG.cfg' $script:CfgText
        $script:Config = Read-LauncherConfig -Path $script:CfgPath
        $script:Expected = @{
            MainClass           = 'com.asdasfa.jbs2bg.Launcher'
            MainJarName         = 'app-1.0.jar'
            LibJarNames         = @('a.jar', 'b.jar')
            AppVersion          = '1.1.2'
            RequiredJavaOptions = @('--enable-native-access=javafx.graphics')
        }
    }

    It 'reads repeated keys as lists within their sections' {
        $script:Config['Application']['app.classpath'] | Should -Be @('$APPDIR\app-1.0.jar', '$APPDIR\lib\a.jar', '$APPDIR\lib\b.jar')
        $script:Config['Application']['app.mainclass'] | Should -Be @('com.asdasfa.jbs2bg.Launcher')
        $script:Config['JavaOptions']['java-options'] | Should -HaveCount 2
    }

    It 'accepts a launcher configuration that names the launcher, the jar, every lib jar, and the stamped version' {
        { Assert-LauncherConfig -Config $script:Config @script:Expected } | Should -Not -Throw
    }

    It 'fails closed when the main class is not the launcher' {
        $expected = $script:Expected.Clone(); $expected.MainClass = 'com.asdasfa.jbs2bg.Main'
        { Assert-LauncherConfig -Config $script:Config @expected } | Should -Throw -ExpectedMessage '*app.mainclass*'
    }

    It 'fails closed when the main jar or a staged lib jar is missing from the classpath' {
        $expected = $script:Expected.Clone(); $expected.LibJarNames = @('a.jar', 'b.jar', 'c.jar')
        { Assert-LauncherConfig -Config $script:Config @expected } | Should -Throw -ExpectedMessage '*c.jar*'
        $expected = $script:Expected.Clone(); $expected.MainJarName = 'other-2.0.jar'
        { Assert-LauncherConfig -Config $script:Config @expected } | Should -Throw -ExpectedMessage '*other-2.0.jar*'
    }

    It 'fails closed when the stamped version differs or a required java option is absent' {
        $config = Read-LauncherConfig -Path (New-TreeFile 'cfg\version.cfg' ($script:CfgText -replace 'app-version=1.1.2', 'app-version=1.1.3'))
        { Assert-LauncherConfig -Config $config @script:Expected } | Should -Throw -ExpectedMessage '*jpackage.app-version*'
        $expected = $script:Expected.Clone(); $expected.RequiredJavaOptions = @('--enable-native-access=javafx.graphics', '-Xss1m')
        { Assert-LauncherConfig -Config $script:Config @expected } | Should -Throw -ExpectedMessage '*-Xss1m*'
    }

    It 'fails closed when the classpath or runtime reaches outside the image' {
        $text = $script:CfgText -replace 'app.mainclass=', "app.classpath=C:\jdk\lib\tools.jar`napp.mainclass="
        $config = Read-LauncherConfig -Path (New-TreeFile 'cfg\absolute.cfg' $text)
        { Assert-LauncherConfig -Config $config @script:Expected } | Should -Throw -ExpectedMessage '*tools.jar*'
        $text = $script:CfgText -replace 'app.mainclass=', "app.runtime=C:\Program Files\Java\jdk-25`napp.mainclass="
        $config = Read-LauncherConfig -Path (New-TreeFile 'cfg\runtime.cfg' $text)
        { Assert-LauncherConfig -Config $config @script:Expected } | Should -Throw -ExpectedMessage '*app.runtime*'
    }

    It 'fails closed when a JavaFX jar sits on the launcher classpath' {
        $text = $script:CfgText -replace 'app.mainclass=', "app.classpath=`$APPDIR\lib\javafx-base.jar`napp.mainclass="
        $config = Read-LauncherConfig -Path (New-TreeFile 'cfg\fx.cfg' $text)
        { Assert-LauncherConfig -Config $config @script:Expected } | Should -Throw -ExpectedMessage '*javafx-base.jar*'
    }
}

Describe 'Assert-JpackageState' {
    BeforeAll {
        $script:StateText = '<?xml version="1.0" ?><jpackage-state version="25.0.4.1" platform="windows"><app-version>1.1.2</app-version><main-launcher>BS2BG</main-launcher><main-class>com.asdasfa.jbs2bg.Launcher</main-class></jpackage-state>'
        $script:StatePath = New-TreeFile 'state\.jpackage.xml' $script:StateText
    }

    It 'returns the packaging tool version and platform when the stamped identity matches' {
        $state = Assert-JpackageState -Path $script:StatePath -AppVersion '1.1.2' -LauncherName 'BS2BG' -MainClass 'com.asdasfa.jbs2bg.Launcher'
        $state.ToolVersion | Should -Be '25.0.4.1'
        $state.Platform | Should -Be 'windows'
    }

    It 'fails closed when the version, launcher, or main class differ' {
        { Assert-JpackageState -Path $script:StatePath -AppVersion '1.1.3' -LauncherName 'BS2BG' -MainClass 'com.asdasfa.jbs2bg.Launcher' } | Should -Throw -ExpectedMessage '*app-version*'
        { Assert-JpackageState -Path $script:StatePath -AppVersion '1.1.2' -LauncherName 'jBS2BG' -MainClass 'com.asdasfa.jbs2bg.Launcher' } | Should -Throw -ExpectedMessage '*main-launcher*'
        { Assert-JpackageState -Path $script:StatePath -AppVersion '1.1.2' -LauncherName 'BS2BG' -MainClass 'com.asdasfa.jbs2bg.Main' } | Should -Throw -ExpectedMessage '*main-class*'
    }
}

Describe 'Assert-AppImageLayout' {
    BeforeAll {
        # A synthetic image shaped like jpackage's Windows app-image output.
        $script:Image = Join-Path $TestDrive 'image\BS2BG'
        foreach ($relative in @('BS2BG.exe', 'app\app-1.0.jar', 'app\lib\a.jar', 'app\lib\b.jar', 'app\BS2BG.cfg', 'app\.jpackage.xml',
                'runtime\release', 'runtime\lib\modules', 'runtime\bin\server\jvm.dll', 'runtime\legal\java.base\LICENSE',
                'THIRD-PARTY-NOTICES.txt')) {
            New-TreeFile "image\BS2BG\$relative" | Out-Null
        }
        $script:Layout = @{
            LauncherName  = 'BS2BG'
            MainJarName   = 'app-1.0.jar'
            LibJarNames   = @('a.jar', 'b.jar')
            RequiredFiles = @('THIRD-PARTY-NOTICES.txt')
        }
    }

    It 'accepts a complete image and returns its inventory' {
        $inventory = Assert-AppImageLayout -ImageDir $script:Image @script:Layout
        $inventory.FileCount | Should -Be 11
        $inventory.Files | Should -Contain 'app/lib/a.jar'
        $inventory.Files | Should -Contain 'runtime/bin/server/jvm.dll'
    }

    It 'fails closed when the launcher, a payload jar, the runtime, the notices, or the legal directory is missing' {
        foreach ($relative in @('BS2BG.exe', 'app\lib\b.jar', 'runtime\lib\modules', 'THIRD-PARTY-NOTICES.txt', 'runtime\legal\java.base\LICENSE')) {
            $copy = Join-Path $TestDrive ("image-missing-" + ($relative -replace '[\\.]', '_'))
            Copy-Item -LiteralPath $script:Image -Destination $copy -Recurse
            Remove-Item -LiteralPath (Join-Path $copy $relative) -Force
            { Assert-AppImageLayout -ImageDir $copy @script:Layout } | Should -Throw -ExpectedMessage "*$(Split-Path -Leaf $relative)*"
        }
    }

    It 'fails closed when an unexpected jar is part of the payload' {
        $copy = Join-Path $TestDrive 'image-extra'
        Copy-Item -LiteralPath $script:Image -Destination $copy -Recurse
        New-TreeFile 'image-extra\app\lib\stray.jar' | Out-Null
        { Assert-AppImageLayout -ImageDir $copy @script:Layout } | Should -Throw -ExpectedMessage '*stray.jar*'
    }
}

Describe 'Get-TreeDigest' {
    It 'is deterministic, path-ordered, and sensitive to a single byte' {
        $root = Join-Path $TestDrive 'digest\a'
        New-TreeFile 'digest\a\z.txt' 'zzz' | Out-Null
        New-TreeFile 'digest\a\sub\m.txt' 'mmm' | Out-Null
        $first = Get-TreeDigest -Root $root
        $second = Get-TreeDigest -Root $root
        $first.Sha256 | Should -Be $second.Sha256
        $first.Sha256 | Should -Match '^[0-9a-f]{64}$'
        $first.Files.path | Should -Be @('sub/m.txt', 'z.txt')
        Set-Content -LiteralPath (Join-Path $root 'z.txt') -Value 'zzy' -NoNewline -Encoding utf8
        (Get-TreeDigest -Root $root).Sha256 | Should -Not -Be $first.Sha256
    }
}

Describe 'Assert-RuntimeRelease' {
    BeforeAll {
        $script:Runtime = Join-Path $TestDrive 'runtime\ok'
        # jlink writes exactly these two keys into a linked runtime's release file.
        New-TreeFile 'runtime\ok\release' "JAVA_VERSION=`"25.0.4.1`"`nMODULES=`"java.base java.logging javafx.base javafx.graphics jdk.charsets`"`n" | Out-Null
    }

    It 'returns the module list when the Java version and every expected module match' {
        $release = Assert-RuntimeRelease -RuntimeDir $script:Runtime -ExpectedJavaVersion '25.0.4.1' -ExpectedModules @('java.base', 'javafx.graphics', 'jdk.charsets')
        $release.Modules | Should -Be @('java.base', 'java.logging', 'javafx.base', 'javafx.graphics', 'jdk.charsets')
        $release.Release['JAVA_VERSION'] | Should -Be '25.0.4.1'
    }

    It 'fails closed on a different Java version or a missing module' {
        { Assert-RuntimeRelease -RuntimeDir $script:Runtime -ExpectedJavaVersion '25.0.5' -ExpectedModules @('java.base') } |
            Should -Throw -ExpectedMessage '*JAVA_VERSION*'
        { Assert-RuntimeRelease -RuntimeDir $script:Runtime -ExpectedJavaVersion '25.0.4.1' -ExpectedModules @('java.base', 'javafx.fxml') } |
            Should -Throw -ExpectedMessage '*javafx.fxml*'
    }
}

Describe 'Get-ScrubbedEnvironment' {
    It 'removes every JDK discovery variable and every Java directory from PATH' {
        $environment = @{
            'PATH'             = 'C:\Windows\system32;C:\Program Files\Common Files\Oracle\Java\javapath;C:\Tools;C:\Program Files\Eclipse Adoptium\jdk-25\bin;C:\Users\me\.jdks\temurin\bin'
            'JAVA_HOME'        = 'C:\Program Files\Java\jdk-26'
            'JDK_JAVA_OPTIONS' = '-Xmx1g'
            'JAVA_TOOL_OPTIONS'= '-Dx=1'
            '_JAVA_OPTIONS'    = '-Dy=2'
            'CLASSPATH'        = 'C:\old.jar'
            'TEMP'             = 'C:\Temp'
        }
        $scrubbed = Get-ScrubbedEnvironment -Environment $environment
        $scrubbed.Variables['PATH'] | Should -Be 'C:\Windows\system32;C:\Tools'
        foreach ($name in @('JAVA_HOME', 'JDK_JAVA_OPTIONS', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'CLASSPATH')) {
            $scrubbed.Variables.ContainsKey($name) | Should -BeFalse
            $scrubbed.RemovedVariables | Should -Contain $name
        }
        $scrubbed.Variables['TEMP'] | Should -Be 'C:\Temp'
        $scrubbed.RemovedPathEntries | Should -HaveCount 3
    }
}

Describe 'New-ThirdPartyNotices' {
    BeforeAll {
        $staging = Join-Path $TestDrive 'notices\staging'
        New-TreeFile 'notices\staging\app-1.0.jar' | Out-Null
        New-TestJar -Path (Join-Path $staging 'lib\lib-a-1.2.jar') -Entries @{
            'META-INF/maven/org.example/lib-a/pom.properties' = "groupId=org.example`nartifactId=lib-a`nversion=1.2`n"
            'META-INF/maven/org.example/lib-a/pom.xml'        = '<project><licenses><license><name>MIT License</name><url>https://opensource.org/licenses/MIT</url></license></licenses></project>'
            'META-INF/LICENSE.txt'                             = 'MIT text'
            'META-INF/NOTICE.txt'                              = 'notice text'
        } | Out-Null
        New-TestJar -Path (Join-Path $staging 'lib\lib-b-3.0.jar') -Entries @{
            'org/example/B.class' = 'class'
        } | Out-Null
        $script:Staged = Get-StagedApplication -StagingDir $staging
        $script:Output = Join-Path $TestDrive 'notices\out'
        $script:Result = New-ThirdPartyNotices -StagedApplication $script:Staged -OutputDir $script:Output -ApplicationName 'BS2BG' -ApplicationVersion '1.1.2' -RuntimeComponents @(
            [pscustomobject]@{ name = 'Eclipse Temurin JDK'; version = '25.0.4.1+1'; license = 'GPLv2 with Classpath Exception'; noticesPath = 'runtime/legal/java.base' }
        )
    }

    It 'writes the notices file and extracts each library license and notice file' {
        Test-Path (Join-Path $script:Output 'THIRD-PARTY-NOTICES.txt') | Should -BeTrue
        Test-Path (Join-Path $script:Output 'notices\lib-a-1.2\LICENSE.txt') | Should -BeTrue
        Test-Path (Join-Path $script:Output 'notices\lib-a-1.2\NOTICE.txt') | Should -BeTrue
        $text = Get-Content -LiteralPath (Join-Path $script:Output 'THIRD-PARTY-NOTICES.txt') -Raw
        $text | Should -BeLike '*org.example:lib-a:1.2*'
        $text | Should -BeLike '*MIT License*'
        $text | Should -BeLike '*Eclipse Temurin JDK 25.0.4.1+1*'
        $text | Should -BeLike '*runtime/legal/java.base*'
    }

    It 'records a library without embedded license metadata explicitly instead of omitting it' {
        $component = $script:Result.Components | Where-Object { $_.jar -eq 'lib-b-3.0.jar' }
        $component | Should -Not -BeNullOrEmpty
        $component.licenses | Should -BeNullOrEmpty
        $component.extractedFiles | Should -BeNullOrEmpty
        (Get-Content -LiteralPath (Join-Path $script:Output 'THIRD-PARTY-NOTICES.txt') -Raw) | Should -BeLike '*lib-b-3.0.jar*no license metadata*'
    }
}
