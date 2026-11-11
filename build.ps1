param(
    [Parameter(Mandatory = $true)][string]$JdkHome,
    [Parameter(Mandatory = $true)][string]$JavaFxHome
)

$ErrorActionPreference = 'Stop'
$javac = Join-Path $JdkHome 'bin\javac.exe'
$jar = Join-Path $JdkHome 'bin\jar.exe'
$javafxLib = Join-Path $JavaFxHome 'lib'
$sqlite = Join-Path $PSScriptRoot 'lib\sqlite-jdbc-3.54.1.0.jar'

foreach ($file in @($javac, $jar, (Join-Path $javafxLib 'javafx.controls.jar'), $sqlite)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        throw "Missing build dependency: $file"
    }
}

$buildDir = Join-Path $PSScriptRoot 'build'
$classes = Join-Path $buildDir 'classes'
$artifact = Join-Path $buildDir 'isa-workspace.jar'
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -File -Filter '*.java' |
    Sort-Object Name | ForEach-Object { $_.FullName })

$null = New-Item -ItemType Directory -Path $classes -Force
& $javac --release 21 -encoding UTF-8 --module-path $javafxLib --add-modules javafx.controls -classpath $sqlite -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw "javac failed (exit $LASTEXITCODE)." }

& $jar --create --file $artifact -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed (exit $LASTEXITCODE)." }

Write-Output $artifact
