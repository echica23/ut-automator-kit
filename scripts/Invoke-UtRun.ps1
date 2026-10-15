#requires -Version 5.1
<#
.SYNOPSIS
AIが生成したUTを既存UTから分離して実行し、MavenとJaCoCoの結果を集計する。
.DESCRIPTION
prepare: 対象と設定を検査し、実行フォルダと変更確認用の基準値を作る。
execute: AIがjava/へ配置したUTを実行し、XMLから結果を集計する。
UT生成、仕様判断、原因分析、最終レポートはAIが担当する。Mavenの所要時間はこのスクリプトで計測する。
詳しい引数・成果物・終了条件は ../docs/script-spec.md を参照。
.NOTES
Windows PowerShell 5.1 / PowerShell 7用。設定済みで信頼できるMavenプロジェクトを前提とする。
実行済みフォルダは再利用しない。再試行はprepareで新しい実行IDを作る。
#>
param(
    [Parameter(Mandatory)][ValidateSet('prepare','execute')][string]$Action,
    [Parameter(Mandatory)][string]$Project,
    [ValidateSet('coverage','spec','review')][string]$Mode,
    [string[]]$Targets,
    [string]$Spec,
    [switch]$Javadoc,
    [string]$Output,
    [string]$Run,
    [string]$Config = 'ut-kit.json'
)

# PowerShell側のエラーは停止させる。Mavenの終了コードは後段で別途評価する。
$ErrorActionPreference = 'Stop'

# 確認記録をUTF-8のJSONで出力する。ディレクトリ作成は呼出し側が行う。
function Write-Json($Object, $Path) {
    $json = ConvertTo-Json -InputObject $Object -Depth 15
    [IO.File]::WriteAllText($Path, $json, [Text.UTF8Encoding]::new($false))
}

# 5.1ではネイティブstderrがErrorRecordになる。stderrの有無で中断せず終了コードを評価する。
# PowerShellのエラー制御は関数内だけ変更する。ログはUTF-8、JSON/XMLとは別の診断情報。
function Invoke-MavenLog($Command, $Arguments, $LogPath) {
    $resolved = Get-Command $Command -ErrorAction Stop
    $ErrorActionPreference = 'Continue'
    $PSNativeCommandUseErrorActionPreference = $false
    $global:LASTEXITCODE = -1
    & $resolved.Source @Arguments 2>&1 |
        ForEach-Object { $_.ToString() } |
        Out-File -LiteralPath $LogPath -Encoding utf8 -ErrorAction Stop
    return $global:LASTEXITCODE
}
# XML宣言の文字コードと改行正規化を両ホストで揃える。外部DTDは読み込まない。
function Read-Xml($Path) {
    $options = New-Object System.Xml.XmlReaderSettings
    $options.DtdProcessing = [System.Xml.DtdProcessing]::Ignore
    $options.XmlResolver = $null
    $reader = [System.Xml.XmlReader]::Create($Path, $options)
    try {
        $document = New-Object System.Xml.XmlDocument
        $document.XmlResolver = $null
        $document.Load($reader)
        return ,$document
    }
    finally {
        $reader.Dispose()
    }
}
# 相対パスを基準ディレクトリから絶対化する補助関数。

# 名前にUnderとあるが、基準配下への制限は行わない。出力先の制限はprepareで確認する。
function Resolve-Under($Base, $Value) {
    if ([IO.Path]::IsPathRooted($Value)) {
        return [IO.Path]::GetFullPath($Value)
    }
    return [IO.Path]::GetFullPath((Join-Path $Base $Value))
}

# ファイル集合とSHA-256を記録。ディレクトリは再帰列挙し、存在しない項目は記録しない。

# 後で集合も比較するため、既存ファイルの編集だけでなく追加・削除も検出できる。
function Get-Snapshot($Root, $Paths) {
    $result = @{
    }
    foreach ($item in $Paths) {
        $path = Resolve-Under $Root $item
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            $result[$path] = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
        }
        elseif (Test-Path -LiteralPath $path) {
            Get-ChildItem -LiteralPath $path -Recurse -File -Force | ForEach-Object {
                $result[$_.FullName] = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
            }
        }
    }
    return $result
}

# 共通の事前確認: プロジェクト、設定形式、Maven導入状態を確認する。
$root = (Resolve-Path -LiteralPath $Project).Path
$configPath = Resolve-Under $root $Config
$settings = Get-Content -LiteralPath $configPath -Raw -Encoding UTF8 | ConvertFrom-Json
# 設定省略は既存環境との互換性のためPowerShell。別方式への自動切替はしない。
$runnerName = 'powershell'
if ($settings.PSObject.Properties.Name -contains 'runner') {
    if ($settings.runner -isnot [string] -or $settings.runner -cnotin @('java','powershell')) {
        throw 'runner must be java or powershell.'
    }
    $runnerName = $settings.runner
}
if ($runnerName -ne 'powershell') {
    throw 'Runner mismatch: configured runner is java; no automatic fallback.'
}
if ($settings.schemaVersion -ne 1) {
    throw 'Unsupported config schema.'
}
if (!(Test-Path -LiteralPath (Join-Path $root 'pom.xml'))) {
    throw 'pom.xml missing; Maven only.'
}

# 初版では対象モジュールのPOMに明示されたプロパティ参照を要求する。親POMの有効設定は解決しない。
[xml]$pom = Read-Xml (Join-Path $root 'pom.xml')
if ([string]$pom.project.build.testSourceDirectory -ne '${ut.sources}' -or
    [string]$pom.project.build.directory -ne '${ut.build}') {
    throw 'Setup incomplete: build.testSourceDirectory=${ut.sources} and build.directory=${ut.build} required.'
}
if ($settings.profile -notin @($pom.project.profiles.profile.id)) {
    throw 'Configured opt-in profile is missing from module POM.'
}

# 利用者指定の保護対象に加え、src・pom・.mvn・設定ファイルは必ず照合対象にする。
$protected = @($settings.protectedPaths) + @('src','pom.xml','.mvn',$configPath)

# Project Rulesは任意。未設定・null・空配列なら従来の汎用ルールだけを使う。
# 内容はAIが解釈し、スクリプトは参照先と変更を検査する。
$projectRules = @()
if ($null -ne $settings.projectRules) {
    if ($settings.projectRules -isnot [array]) {
        throw 'projectRules must be an array of Markdown file paths.'
    }
    foreach ($rule in $settings.projectRules) {
        if ($rule -isnot [string] -or [string]::IsNullOrWhiteSpace($rule)) {
            throw 'Each projectRules entry must be a non-empty string.'
        }
        $rulePath = Resolve-Under $root $rule
        if ([IO.Path]::GetExtension($rulePath) -ne '.md' -or
            !(Test-Path -LiteralPath $rulePath -PathType Leaf)) {
            throw "Project Rules must reference an existing Markdown file: $rulePath"
        }
        if ($rulePath -in @($projectRules | ForEach-Object { $_.path })) {
            throw "Duplicate Project Rules path: $rulePath"
        }
        $projectRules += [ordered]@{
            path = $rulePath
            sha256 = (Get-FileHash -LiteralPath $rulePath -Algorithm SHA256).Hash
        }
        $protected += $rulePath
    }
}
# prepare: ケース設計の前に実行フォルダと変更確認の基準値を確保する。UT自体は生成しない。
if ($Action -eq 'prepare') {
    if (!$Mode -or !$Targets) {
        throw 'Mode and concrete Targets are required.'
    }
    # ワイルドカード解決はAIの役割。ここには完全修飾クラス名だけを渡す。
    foreach ($target in $Targets) {
        if ($target -notmatch '^[A-Za-z_$][\w$]*(\.[A-Za-z_$][\w$]*)+$') {
            throw "Expected exact qualified class name: $target"
        }
        $source = Join-Path $root ('src/main/java/' + $target.Replace('.','/') + '.java')
        if (!(Test-Path -LiteralPath $source)) {
            throw "Source not found in standard source root: $target"
        }
    }
    # 根拠を明示選択する。Javadocの読解はAIが担当し、ソースはsrcの保護で変更検出する。
    if ($Javadoc -and $PSBoundParameters.ContainsKey('Spec')) {
        throw 'Spec and Javadoc are mutually exclusive.'
    }
    if ($Mode -eq 'coverage' -and $Javadoc) {
        throw 'Javadoc is only available for spec/review.'
    }
    $specSource = 'implementation'
    if ($Mode -ne 'coverage') {
        if ($Javadoc) {
            $specSource = 'javadoc'
        } else {
            $specSource = 'document'
        }
    }
    # 文書を根拠にする場合は仕様参照パスを必須とする。
    # ディレクトリは全ファイルを再帰的にハッシュ記録する。内容の選択・読解はAIが行う。
    $specPath = $null
    if ($specSource -eq 'document') {
        if (!$Spec) {
            throw 'Spec required for spec/review unless Javadoc is selected.'
        }
        $specPath = (Resolve-Path -LiteralPath (Resolve-Under $root $Spec)).Path
        if (!(Test-Path -LiteralPath $specPath -PathType Leaf) -and
            !(Test-Path -LiteralPath $specPath -PathType Container)) {
            throw 'Spec must be a file or directory.'
        }
        $protected += $specPath
    }
    # -Output優先、未指定なら設定値。保護対象内・プロジェクト直下・.git側への出力を拒否する。
    # パス文字列に基づく確認であり、リンク先やジャンクションの実体まで検証するものではない。
    $outputRoot = Resolve-Under $root $(if ($Output) {
            $Output
        } else {
            $settings.outputRoot
    })
    foreach ($path in $protected) {
        $p = (Resolve-Under $root $path).TrimEnd('\','/')
        if ($outputRoot -eq $p -or
            $outputRoot.StartsWith($p + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Output overlaps protected path.'
        }
    }
    if ($outputRoot -eq $root -or
        $outputRoot.StartsWith((Join-Path $root '.git'),[StringComparison]::OrdinalIgnoreCase)) {
        throw 'Unsafe output root.'
    }
    # 日時＋ランダム値で衝突を避け、既存の実行結果を上書きしない。
    $id = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + ([guid]::NewGuid().ToString('N').Substring(0,8))
    $runPath = Join-Path (Join-Path $outputRoot $Mode) $id
    if (Test-Path -LiteralPath $runPath) {
        throw 'Run exists.'
    }
    New-Item -ItemType Directory -Path (Join-Path $runPath 'java') -Force | Out-Null
    $snapshot = Get-Snapshot $root $protected
    Write-Json $snapshot (Join-Path $runPath 'before-hashes.json')
    Write-Json ([ordered]@{
            schemaVersion = 1
            runner = 'powershell'
            project = $root
            config = $configPath
            mode = $Mode
            targets = @($Targets)
            spec = $specPath
            specSource = $specSource
            projectRules = @($projectRules)
            runId = $id
            runPath = $runPath
            protectedPaths = $protected
            startedAt = (Get-Date).ToUniversalTime().ToString('o')
    }) (Join-Path $runPath 'run.json')
    # 登録・ハッシュ確認とAIによる適用完了を混同しない。
    Write-Json ([ordered]@{
        schemaVersion = 1
        files = @($projectRules)
        status = $(if ($projectRules.Count) {
            'PENDING_AI_REVIEW'
        } else {
            'NOT_CONFIGURED'
        })
    }) (Join-Path $runPath 'project-rules.json')
    Write-Output $runPath
    exit 0
}

# execute: prepareで作成したrun.jsonを使い、対象や出力先の取り違えを検出する。
if (!$Run) {
    throw 'Run directory required.'
}
$runPath = (Resolve-Path -LiteralPath $Run).Path
$manifest = Get-Content -LiteralPath (Join-Path $runPath 'run.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if ($manifest.project -ne $root -or
    $manifest.config -ne $configPath -or
    $manifest.runPath -ne $runPath) {
    throw 'Run/project/config mismatch.'
}
if ($manifest.PSObject.Properties.Name -contains 'runner') {
    if ($manifest.runner -cne 'powershell') {
        throw 'Run runner mismatch; prepare a new run using the configured runner.'
    }
}
if (Test-Path -LiteralPath (Join-Path $runPath 'execution-started.json')) {
    throw 'Run already used. Prepare a new run; never reuse old coverage.'
}
$before = Get-Content -LiteralPath (Join-Path $runPath 'before-hashes.json') -Raw -Encoding UTF8 | ConvertFrom-Json

# 基準時点との差分を返す。書込みを禁止する仕組みではなく、変更を検出して報告する仕組み。
function Compare-Protected {
    $after = Get-Snapshot $root $manifest.protectedPaths
    $changes = @()
    foreach ($p in $before.PSObject.Properties) {
        if ($after[$p.Name] -ne $p.Value) {
            $changes += $p.Name
        }
    }
    foreach ($key in $after.Keys) {
        if (!$before.PSObject.Properties[$key]) {
            $changes += $key
        }
    }
    return $changes
}
$changes = @(Compare-Protected)
if ($changes.Count) {
    throw ('Protected files changed before execution: ' + ($changes -join ', '))
}

# UT候補を簡易な正規表現で抽出し、Surefireの-Dtestに渡す。Javaの完全な構文解析ではない。

# 生成UTはパッケージ宣言とTest末尾のファイル名、標準JUnitアノテーションを使う。
$javaRoot = Join-Path $runPath 'java'
$testFiles = @(Get-ChildItem -LiteralPath $javaRoot -Recurse -File -Filter '*.java')
if (!$testFiles.Count) {
    throw 'No generated tests; refusing zero-test success.'
}
$testNames = @()
foreach ($file in $testFiles) {
    $content = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
    if ($content -match '@(?:ParameterizedTest|Test|RepeatedTest|TestFactory|TestTemplate)\b') {
        if ($file.BaseName -notmatch '^.+Test$') {
            throw 'Generated test classes must end in Test.'
        }
        $package = [regex]::Match($content,'(?m)^\s*package\s+([\w.]+)\s*;').Groups[1].Value
        if (!$package) {
            throw 'Test package required.'
        }
        $testNames += $package + '.' + $file.BaseName
    }
}
if (!$testNames.Count) {
    throw 'No supported test annotations found.'
}
$maven = $settings.maven
if (!$maven) {
    throw 'Maven executable not configured.'
}

# JAVA_HOMEを一時的に切り替える。Maven引数は配列で渡し、空白を含むパスを保持する。
$envBefore = $env:JAVA_HOME
if ($settings.javaHome) {
    $env:JAVA_HOME = $settings.javaHome
}
$build = Join-Path $runPath 'build'
$argsBase = @(
    '-B',
    "-P$($settings.profile)",
    "-Dut.sources=$javaRoot",
    "-Dut.build=$build",
    "-Dtest=$($testNames -join ',')"
)
if ($settings.localRepository) {
    $argsBase += "-Dmaven.repo.local=$($settings.localRepository)"
}

# 実行開始マーカーを先に作る。一度開始したフォルダは失敗時も再利用しない。

# ut-hashesは実行前の生成UTの記録であり、生成UTの実行後照合はこのスクリプトでは行わない。
Write-Json @{
    startedAt = (Get-Date).ToUniversalTime().ToString('o')
    maven = $maven
    arguments = $argsBase
    testClasses = $testNames
} (Join-Path $runPath 'execution-started.json')
Write-Json (Get-Snapshot $runPath @('java')) (Join-Path $runPath 'ut-hashes.json')
$testExit = -1
$coverageExit = -1
$executionError = $null

# 各Maven呼出しだけを計測する。未着手はnullで、0秒や成功とは扱わない。
$mavenTiming = [ordered]@{
    testSeconds = $null
    coverageSeconds = $null
    totalSeconds = 0.0
}

# Mavenは対象モジュールを作業ディレクトリとして起動する。

# 通常のテストFAILは終了コードで受け、続いて同じ実行のJaCoCoを生成する。

# 起動失敗等で例外になった場合はcatchへ進み、未実行の終了コードは-1のまま残る。
Push-Location $root
try {
    $timer = [Diagnostics.Stopwatch]::StartNew()
    try {
        $testExit = Invoke-MavenLog $maven ($argsBase + @('test')) (Join-Path $runPath 'test.log')
    }
    finally {
        $timer.Stop()
        $mavenTiming.testSeconds = [math]::Round($timer.Elapsed.TotalSeconds, 3)
    }
    $timer = [Diagnostics.Stopwatch]::StartNew()
    try {
        $coverageExit = Invoke-MavenLog $maven ($argsBase + @('jacoco:report')) (Join-Path $runPath 'coverage.log')
    }
    finally {
        $timer.Stop()
        $mavenTiming.coverageSeconds = [math]::Round($timer.Elapsed.TotalSeconds, 3)
    }
} catch {
    $executionError = $_.Exception.Message
}
finally {
    $mavenTiming.totalSeconds = [math]::Round(
        ([double]$mavenTiming.testSeconds + [double]$mavenTiming.coverageSeconds), 3)
    Pop-Location
    $env:JAVA_HOME = $envBefore
}
$changes = @(Compare-Protected)
Write-Json @{
    unchanged = ($changes.Count -eq 0)
    changedPaths = $changes
} (Join-Path $runPath 'change-check.json')

# Surefire XMLの件数と各testcaseを集計。予期しないsuiteは既存UT混入等の確認材料にする。

# 固定IDはsystem-out内に保持する。ID抽出・一意性・仕様との対応確認はAIが担当する。
$tests = 0
$failures = 0
$errors = 0
$skipped = 0
$cases = @()
$unexpected = @()
$surefire = Join-Path $build 'surefire-reports'
if (Test-Path -LiteralPath $surefire) {
    foreach ($file in Get-ChildItem -LiteralPath $surefire -Filter 'TEST-*.xml') {
        [xml]$xml = Read-Xml $file.FullName
        $suite = $xml.testsuite
        if ($suite.name -notin $testNames) {
            $unexpected += $suite.name
        }
        $tests += [int]$suite.tests
        $failures += [int]$suite.failures
        $errors += [int]$suite.errors
        $skipped += [int]$suite.skipped
        foreach ($case in $suite.testcase) {
            $cases += [ordered]@{
                name = $case.name
                class = $case.classname
                outcome = $(
                    if ($case.failure) {
                        'FAIL'
                    } elseif ($case.error) {
                        'ERROR'
                    } elseif ($case.skipped) {
                        'SKIP'
                    } else {
                        'PASS'
                    }
                )
                failure = [string]$case.failure.message
                error = [string]$case.error.message
                # CDATAを含むXmlElementも本文を保持する。文字列化だけだと型名になる。
                output = $(
                    if ($case.'system-out' -is [System.Xml.XmlNode]) {
                        $case.'system-out'.InnerText
                    } else {
                        [string]$case.'system-out'
                    }
                )
            }
        }
    }
}

# JaCoCo XMLから指定クラスのLINE/BRANCHだけを抽出。全プロジェクト集計は使わない。

# クラスがない場合は未計測、分母0ならpercent=null。内部クラスは自動的に合算しない。
$coverage = @()
$missing = @()
$xmlPath = Join-Path $build 'site/jacoco/jacoco.xml'
if (Test-Path -LiteralPath $xmlPath) {
    [xml]$jacoco = Read-Xml $xmlPath
    foreach ($target in $manifest.targets) {
        $class = @($jacoco.report.package.class | Where-Object name -eq $target.Replace('.','/'))
        if (!$class.Count) {
            $missing += $target
            continue
        }
        foreach ($counter in $class.counter | Where-Object type -in 'LINE','BRANCH') {
            $covered = [int]$counter.covered
            $missed = [int]$counter.missed
            $coverage += [ordered]@{
                target = $target
                metric = $counter.type
                covered = $covered
                missed = $missed
                percent = $(
                    if (($covered+$missed) -gt 0) {
                        [math]::Round(100*$covered/($covered+$missed),2)
                    } else {
                        $null
                    }
                )
            }
        }
    }
} else {
    $missing = @($manifest.targets)
}

# テスト実行状態を分類する。statusはカバレッジ成功を含まないため、終了コードと別に読む。

# 例: テストPASSでもJaCoCo未生成ならstatus=PASS、終了コード=2となる。
$status = if ($executionError -or
    $tests -eq 0 -or
    $tests -eq $skipped -or
    $unexpected.Count -or
    $changes.Count -or
    $testExit -notin 0,1 -or
    ($testExit -ne 0 -and
        ($failures+$errors) -eq 0)) {
    'EXECUTION_ERROR'
} elseif ($errors -gt 0) {
    'TEST_ERROR'
} elseif ($failures -gt 0) {
    'TEST_FAILURE'
} else {
    'PASS'
}
$summary = [ordered]@{
    runId = $manifest.runId
    mode = $manifest.mode
    status = $status
    testExit = $testExit
    coverageExit = $coverageExit
    tests = $tests
    passed = ($tests-$failures-$errors-$skipped)
    failures = $failures
    errors = $errors
    skipped = $skipped
    coverage = $coverage
    missingCoverage = $missing
    unexpectedSuites = $unexpected
    protectedChanges = $changes
    executionError = $executionError
    mavenTiming = $mavenTiming
    aiReportComplete = $false
}

# XML由来の証跡と機械集計を保存する。aiReportComplete=falseはAI工程が別であることを示す。
Write-Json $cases (Join-Path $runPath 'case-results.json')
Write-Json $summary (Join-Path $runPath 'summary.json')
Write-Output (Join-Path $runPath 'summary.json')

# 集計まで到達した場合の終了コード: 実行/計測不備=2、テストFAIL/ERROR=1、成功=0。

# 事前検査のthrowやXML読取り例外はここへ到達せず、summaryが作られない場合がある。
if ($status -eq 'EXECUTION_ERROR' -or $missing.Count -or $coverageExit -ne 0) {
    exit 2
}
if ($status -ne 'PASS') {
    exit 1
}
exit 0
