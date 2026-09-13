#!/usr/bin/env pwsh
[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$script:Failures = 0
$scriptRoot = Split-Path -Parent $PSScriptRoot
$commonPath = Join-Path $scriptRoot "lib/release-common.ps1"
$entryPath = Join-Path $scriptRoot "umoweb-release.ps1"

function Assert-Equal {
    param(
        [Parameter(Mandatory)]
        $Actual,
        [Parameter(Mandatory)]
        $Expected,
        [Parameter(Mandatory)]
        [string]$Message
    )

    if ($Actual -ne $Expected) {
        throw "$Message. Expected '$Expected', got '$Actual'."
    }
}

function Assert-True {
    param(
        [Parameter(Mandatory)]
        [bool]$Condition,
        [Parameter(Mandatory)]
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Assert-Throws {
    param(
        [Parameter(Mandatory)]
        [scriptblock]$Action,
        [Parameter(Mandatory)]
        [string]$Message
    )

    try {
        & $Action
    } catch {
        return
    }

    throw $Message
}

if (-not (Test-Path -LiteralPath $commonPath -PathType Leaf)) {
    throw "Missing release helper library: $commonPath"
}
if (-not (Test-Path -LiteralPath $entryPath -PathType Leaf)) {
    throw "Missing release entry point: $entryPath"
}

. $commonPath

try {
    foreach ($version in @("v1.0.0", "v1.2.3-rc.4")) {
        Assert-ReleaseVersion -Version $version
    }

    foreach ($version in @("1.0.0", "v1.0", "v1.0.0-rc", "v1.0.0/../../bad")) {
        Assert-Throws -Message "Invalid version was accepted: $version" -Action {
            Assert-ReleaseVersion -Version $version
        }
    }

    $invalidVersionOutput = & pwsh -NoProfile -File $entryPath `
        -Action Publish `
        -Version "v1.0" `
        -InstanceId "i-test" 2>&1
    Assert-True `
        -Condition ($LASTEXITCODE -ne 0) `
        -Message "Publish entry point accepted an invalid version: $invalidVersionOutput"

    $missingVersionOutput = & pwsh -NoProfile -File $entryPath `
        -Action Publish `
        -InstanceId "i-test" 2>&1
    Assert-True `
        -Condition ($LASTEXITCODE -ne 0) `
        -Message "Publish entry point accepted a missing version: $missingVersionOutput"

    $missingArtifactOutput = & pwsh -NoProfile -File $entryPath `
        -Action Rollback `
        -InstanceId "i-test" 2>&1
    Assert-True `
        -Condition ($LASTEXITCODE -ne 0) `
        -Message "Rollback entry point accepted a missing artifact: $missingArtifactOutput"

    $runs = @'
[
  {
    "databaseId": 10,
    "status": "completed",
    "conclusion": "success",
    "headSha": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "createdAt": "2026-09-13T01:00:00Z",
    "url": "https://example.invalid/runs/10",
    "event": "push"
  },
  {
    "databaseId": 20,
    "status": "completed",
    "conclusion": "failure",
    "headSha": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
    "createdAt": "2026-09-13T02:00:00Z",
    "url": "https://example.invalid/runs/20",
    "event": "pull_request"
  },
  {
    "databaseId": 30,
    "status": "completed",
    "conclusion": "success",
    "headSha": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
    "createdAt": "2026-09-13T03:00:00Z",
    "url": "https://example.invalid/runs/30",
    "event": "pull_request"
  },
  {
    "databaseId": 40,
    "status": "completed",
    "conclusion": "success",
    "headSha": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
    "createdAt": "2026-09-13T04:00:00Z",
    "url": "https://example.invalid/runs/40",
    "event": "push"
  },
  {
    "databaseId": 50,
    "status": "completed",
    "conclusion": "success",
    "headSha": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
    "createdAt": "2026-09-13T05:00:00Z",
    "url": "https://example.invalid/runs/50",
    "event": "pull_request"
  }
]
'@

    $selected = Select-SuccessfulCiRun -RunsJson $runs -Commit "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    Assert-Equal -Actual $selected.databaseId -Expected 40 -Message "CI selector did not choose the latest successful push run"

    Assert-Throws -Message "CI selector accepted a commit without a successful run" -Action {
        Select-SuccessfulCiRun -RunsJson $runs -Commit "cccccccccccccccccccccccccccccccccccccccc"
    }

    $adminPath = "/private-admin"
    $manifest = New-ReleaseManifest `
        -ReleaseId "v1.0.0-rc.1" `
        -Kind "release" `
        -Version "v1.0.0-rc.1" `
        -GitCommit "922849cbf7b4fd14764bb29ce765607a6e6681d9" `
        -CiRunId 34736666387 `
        -BuiltAtUtc "2026-09-13T04:00:00Z" `
        -BackendTag "umoweb-backend:v1.0.0-rc.1" `
        -BackendCommitTag "umoweb-backend:sha-922849cbf7b4" `
        -BackendImageId "sha256:1111111111111111111111111111111111111111111111111111111111111111" `
        -FrontendTag "umoweb-frontend:v1.0.0-rc.1" `
        -FrontendCommitTag "umoweb-frontend:sha-922849cbf7b4" `
        -FrontendImageId "sha256:2222222222222222222222222222222222222222222222222222222222222222" `
        -ArchiveFileName "umoweb-images-v1.0.0-rc.1.tar" `
        -ArchiveSha256 "3333333333333333333333333333333333333333333333333333333333333333" `
        -ArchiveSize 123456 `
        -AdminPath $adminPath `
        -AccessRawRetentionDays 30 `
        -AccessAggregateRetentionDays 180

    Assert-Equal -Actual $manifest.schemaVersion -Expected 1 -Message "Unexpected manifest schema version"
    Assert-Equal -Actual $manifest.releaseId -Expected "v1.0.0-rc.1" -Message "Unexpected release ID"
    Assert-Equal -Actual $manifest.backendImage.tag -Expected "umoweb-backend:v1.0.0-rc.1" -Message "Unexpected backend tag"
    Assert-Equal -Actual $manifest.backendImage.commitTag -Expected "umoweb-backend:sha-922849cbf7b4" -Message "Unexpected backend commit tag"
    Assert-Equal -Actual $manifest.backendImage.imageId.Length -Expected 71 -Message "Unexpected image ID length"
    Assert-True -Condition ($manifest.adminPathSha256 -ne $adminPath) -Message "Manifest leaked the admin path"
    Assert-Equal `
        -Actual $manifest.adminPathSha256 `
        -Expected "55b15c306754cf0b831e9d4ea80403c98b6bac5266597e9afc0121a35f475fce" `
        -Message "Unexpected admin path hash"
    Assert-Equal -Actual $manifest.accessPolicy.rawRetentionDays -Expected 30 -Message "Unexpected raw access retention"
    Assert-Equal -Actual $manifest.accessPolicy.aggregateRetentionDays -Expected 180 -Message "Unexpected aggregate access retention"

    Assert-Throws -Message "Invalid raw access retention was accepted" -Action {
        New-ReleaseManifest `
            -ReleaseId "v1.0.0-rc.2" `
            -Kind "release" `
            -Version "v1.0.0-rc.2" `
            -GitCommit "922849cbf7b4fd14764bb29ce765607a6e6681d9" `
            -CiRunId 34736666387 `
            -BuiltAtUtc "2026-09-13T04:00:00Z" `
            -BackendTag "umoweb-backend:v1.0.0-rc.2" `
            -BackendCommitTag "umoweb-backend:sha-922849cbf7b4" `
            -BackendImageId "sha256:1111111111111111111111111111111111111111111111111111111111111111" `
            -FrontendTag "umoweb-frontend:v1.0.0-rc.2" `
            -FrontendCommitTag "umoweb-frontend:sha-922849cbf7b4" `
            -FrontendImageId "sha256:2222222222222222222222222222222222222222222222222222222222222222" `
            -ArchiveFileName "umoweb-images-v1.0.0-rc.2.tar" `
            -ArchiveSha256 "3333333333333333333333333333333333333333333333333333333333333333" `
            -ArchiveSize 123456 `
            -AdminPath $adminPath `
            -AccessRawRetentionDays 6 `
            -AccessAggregateRetentionDays 180
    }

    Assert-TagCanBePublished `
        -Tag "v1.0.0-rc.1" `
        -Commit "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" `
        -LocalTagCommit "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" `
        -RemoteTagCommit "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    Assert-Throws -Message "Local tag collision was accepted" -Action {
        Assert-TagCanBePublished `
            -Tag "v1.0.0-rc.1" `
            -Commit "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" `
            -LocalTagCommit "cccccccccccccccccccccccccccccccccccccccc"
    }
    Assert-Throws -Message "Remote tag collision was accepted" -Action {
        Assert-TagCanBePublished `
            -Tag "v1.0.0-rc.1" `
            -Commit "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" `
            -RemoteTagCommit "cccccccccccccccccccccccccccccccccccccccc"
    }

    $remoteTagOutput = @"
$(("d" * 40))`trefs/tags/v1.0.0-rc.1
$(("e" * 40))`trefs/tags/v1.0.0-rc.1^{}
"@
    Assert-Equal `
        -Actual (Select-RemoteTagCommit -LsRemoteOutput $remoteTagOutput -Tag "v1.0.0-rc.1") `
        -Expected ("e" * 40) `
        -Message "Remote tag parser did not select the peeled commit"

    Assert-True `
        -Condition (Test-ReleaseStateMatchesManifest -State $manifest -Manifest $manifest) `
        -Message "Matching release state was rejected"
    $differentState = $manifest | ConvertTo-Json -Depth 10 | ConvertFrom-Json
    $differentState.gitCommit = "ffffffffffffffffffffffffffffffffffffffff"
    Assert-True `
        -Condition (-not (Test-ReleaseStateMatchesManifest -State $differentState -Manifest $manifest)) `
        -Message "Release state with a different commit was accepted"

    $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("umoweb-release-test-" + [guid]::NewGuid())
    New-Item -ItemType Directory -Path $tempRoot | Out-Null
    try {
        foreach ($entry in @(
            @{ Name = "old"; Timestamp = "2026-09-01T00:00:00Z" },
            @{ Name = "middle"; Timestamp = "2026-09-10T00:00:00Z" },
            @{ Name = "latest"; Timestamp = "2026-09-12T00:00:00Z" }
        )) {
            $directory = Join-Path $tempRoot $entry.Name
            New-Item -ItemType Directory -Path $directory | Out-Null
            [ordered]@{
                releaseId = $entry.Name
                builtAtUtc = $entry.Timestamp
            } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $directory "manifest.json")
        }

        Remove-OldReleaseArtifacts -ArtifactRoot $tempRoot -RetentionCount 2
        Assert-True -Condition (-not (Test-Path -LiteralPath (Join-Path $tempRoot "old"))) -Message "Old release was not pruned"
        Assert-True -Condition (Test-Path -LiteralPath (Join-Path $tempRoot "middle")) -Message "Middle release was pruned"
        Assert-True -Condition (Test-Path -LiteralPath (Join-Path $tempRoot "latest")) -Message "Latest release was pruned"

        $stateFile = Join-Path $tempRoot "current-state.json"
        [ordered]@{
            schemaVersion = 1
            releaseId = "v1.0.0-rc.1"
            kind = "release"
            version = "v1.0.0-rc.1"
            gitCommit = "922849cbf7b4fd14764bb29ce765607a6e6681d9"
            ciRunId = 34736666387
            builtAtUtc = "2026-09-13T04:00:00Z"
            backendImage = [ordered]@{
                tag = "umoweb-backend:v1.0.0-rc.1"
                commitTag = "umoweb-backend:sha-922849cbf7b4"
                imageId = "sha256:1111111111111111111111111111111111111111111111111111111111111111"
            }
            frontendImage = [ordered]@{
                tag = "umoweb-frontend:v1.0.0-rc.1"
                commitTag = "umoweb-frontend:sha-922849cbf7b4"
                imageId = "sha256:2222222222222222222222222222222222222222222222222222222222222222"
            }
            archive = [ordered]@{
                fileName = "umoweb-images-v1.0.0-rc.1.tar"
                sha256 = "3333333333333333333333333333333333333333333333333333333333333333"
                size = 123456
            }
            adminPathSha256 = "55b15c306754cf0b831e9d4ea80403c98b6bac5266597e9afc0121a35f475fce"
            operation = "deploy"
        } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $stateFile

        $fakeWorkbench = Join-Path $tempRoot "fake-workbench.ps1"
        @'
if ($args.Count -gt 0 -and $args[0] -eq "upload") {
    Write-Output "uploaded"
    exit 0
}

if ($args.Count -gt 0 -and $args[0] -eq "exec") {
    $command = ""
    for ($index = 0; $index -lt $args.Count - 1; $index++) {
        if ($args[$index] -eq "--command") {
            $command = [string]$args[$index + 1]
            break
        }
    }

    $stdout = ""
    if ($command -match "verify-current") {
        $stdout = (Get-Content -Raw -LiteralPath $env:FAKE_STATE_FILE).Trim()
    }

    @{
        instance_id = "i-test"
        session_id = "s-test"
        request_id = "r-test"
        command = $command
        exit_code = 0
        stdout = $stdout
        stderr = ""
    } | ConvertTo-Json -Compress
    exit 0
}

exit 1
'@ | Set-Content -LiteralPath $fakeWorkbench

        $previousStateFile = $env:FAKE_STATE_FILE
        try {
            $env:FAKE_STATE_FILE = $stateFile
            $missingPublicUrlOutput = & pwsh -NoProfile -File $entryPath `
                -Action Verify `
                -InstanceId "i-test" `
                -Workbench $fakeWorkbench `
                -RepositoryRoot (Resolve-Path (Join-Path $PSScriptRoot "../../..")).Path `
                -ArtifactRoot $tempRoot 2>&1
            $missingPublicUrlExitCode = $LASTEXITCODE
            $verifyOutput = & pwsh -NoProfile -File $entryPath `
                -Action Verify `
                -InstanceId "i-test" `
                -PublicBaseUrl "https://example.invalid" `
                -Workbench $fakeWorkbench `
                -RepositoryRoot (Resolve-Path (Join-Path $PSScriptRoot "../../..")).Path `
                -ArtifactRoot $tempRoot 2>&1
            $verifyExitCode = $LASTEXITCODE
        } finally {
            $env:FAKE_STATE_FILE = $previousStateFile
        }
        Assert-True `
            -Condition ($missingPublicUrlExitCode -ne 0) `
            -Message "Verify entry point accepted a missing public URL: $missingPublicUrlOutput"
        Assert-True `
            -Condition ($verifyExitCode -eq 0) `
            -Message "Verify entry point failed: $verifyOutput"
        Assert-True `
            -Condition (($verifyOutput | Out-String) -match "v1\.0\.0-rc\.1") `
            -Message "Verify entry point did not report the current release"

        $captureArchive = Join-Path $tempRoot "umoweb-images-baseline-test.tar"
        [System.IO.File]::WriteAllBytes($captureArchive, [byte[]](1..100))
        $captureArchiveHash = (Get-FileHash -LiteralPath $captureArchive -Algorithm SHA256).Hash.ToLowerInvariant()
        $captureManifest = Join-Path $tempRoot "capture-manifest.json"
        [ordered]@{
            schemaVersion = 1
            releaseId = "baseline-test"
            kind = "baseline"
            version = $null
            gitCommit = $null
            ciRunId = $null
            builtAtUtc = "2026-09-13T04:00:00Z"
            backendImage = [ordered]@{
                tag = "umoweb-backend:baseline-test"
                commitTag = "umoweb-backend:baseline-test"
                imageId = "sha256:1111111111111111111111111111111111111111111111111111111111111111"
            }
            frontendImage = [ordered]@{
                tag = "umoweb-frontend:baseline-test"
                commitTag = "umoweb-frontend:baseline-test"
                imageId = "sha256:2222222222222222222222222222222222222222222222222222222222222222"
            }
            archive = [ordered]@{
                fileName = "umoweb-images-baseline-test.tar"
                sha256 = $captureArchiveHash
                size = 100
            }
            adminPathSha256 = "55b15c306754cf0b831e9d4ea80403c98b6bac5266597e9afc0121a35f475fce"
        } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $captureManifest

        $fakeCaptureWorkbench = Join-Path $tempRoot "fake-capture-workbench.ps1"
        @'
if ($args.Count -gt 0 -and $args[0] -eq "upload") {
    Write-Output "uploaded"
    exit 0
}

if ($args.Count -gt 0 -and $args[0] -eq "download") {
    $localPath = [string]$args[$args.Count - 1]
    Copy-Item -LiteralPath $env:FAKE_ARCHIVE -Destination $localPath -Force
    Write-Output "downloaded"
    exit 0
}

if ($args.Count -gt 0 -and $args[0] -eq "exec") {
    $command = ""
    for ($index = 0; $index -lt $args.Count - 1; $index++) {
        if ($args[$index] -eq "--command") {
            $command = [string]$args[$index + 1]
            break
        }
    }

    $stdout = ""
    if ($command -match "capture" -and $command -match "--name") {
        $stdout = (Get-Content -Raw -LiteralPath $env:FAKE_MANIFEST).Trim()
    }

    @{
        instance_id = "i-test"
        session_id = "s-test"
        request_id = "r-test"
        command = $command
        exit_code = 0
        stdout = $stdout
        stderr = ""
    } | ConvertTo-Json -Compress
    exit 0
}

exit 1
'@ | Set-Content -LiteralPath $fakeCaptureWorkbench

        $previousArchive = $env:FAKE_ARCHIVE
        $previousManifest = $env:FAKE_MANIFEST
        try {
            $env:FAKE_ARCHIVE = $captureArchive
            $env:FAKE_MANIFEST = $captureManifest
            $captureOutput = & pwsh -NoProfile -File $entryPath `
                -Action CaptureBaseline `
                -Name "baseline-test" `
                -InstanceId "i-test" `
                -Workbench $fakeCaptureWorkbench `
                -RepositoryRoot (Resolve-Path (Join-Path $PSScriptRoot "../../..")).Path `
                -ArtifactRoot (Join-Path $tempRoot "captured") 2>&1
            $captureExitCode = $LASTEXITCODE
        } finally {
            $env:FAKE_ARCHIVE = $previousArchive
            $env:FAKE_MANIFEST = $previousManifest
        }
        Assert-True `
            -Condition ($captureExitCode -eq 0) `
            -Message "CaptureBaseline entry point failed: $captureOutput"
        Assert-True `
            -Condition (Test-Path -LiteralPath (Join-Path $tempRoot "captured/baseline-test/umoweb-images-baseline-test.tar")) `
            -Message "CaptureBaseline did not save the archive locally"
    } finally {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
    }

    Write-Host "PowerShell release tests passed."
} catch {
    Write-Error $_
    exit 1
}
