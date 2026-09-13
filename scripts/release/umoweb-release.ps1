#!/usr/bin/env pwsh
[CmdletBinding()]
param(
    [Parameter(Mandatory, Position = 0)]
    [ValidateSet("CaptureBaseline", "Publish", "Rollback", "Verify")]
    [string]$Action,
    [string]$Version,
    [string]$Artifact,
    [string]$Name,
    [string]$InstanceId = $env:UMOWEB_INSTANCE_ID,
    [string]$PublicBaseUrl = $env:UMOWEB_PUBLIC_BASE_URL,
    [string]$Workbench = $(if ($env:UMOWEB_WORKBENCH) { $env:UMOWEB_WORKBENCH } else { "C:\Program Files\workbench\workbench.exe" }),
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path,
    [string]$ArtifactRoot = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path "Downloads/releases"),
    [string]$RemoteRoot = "/opt/umoweb",
    [int]$RetentionCount = 2,
    [int]$MaxArchiveBytes = 1000000000
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "lib/release-common.ps1")

$RemoteReleaseScript = "$RemoteRoot/scripts/release/remote-release.sh"
$RemoteIncomingRoot = "$RemoteRoot/releases/incoming"

function Invoke-NativeCapture {
    param(
        [Parameter(Mandatory)]
        [string]$FilePath,
        [Parameter(Mandatory)]
        [string[]]$Arguments,
        [Parameter(Mandatory)]
        [string]$Label,
        [string]$WorkingDirectory
    )

    $previousLocation = Get-Location
    try {
        if (-not [string]::IsNullOrWhiteSpace($WorkingDirectory)) {
            Set-Location -LiteralPath $WorkingDirectory
        }
        $output = & $FilePath @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        Set-Location -LiteralPath $previousLocation
    }
    $text = ($output | Out-String).TrimEnd()
    if ($exitCode -ne 0) {
        throw "$Label failed with exit code $exitCode`n$text"
    }
    return $text
}

function Invoke-NativeStreaming {
    param(
        [Parameter(Mandatory)]
        [string]$FilePath,
        [Parameter(Mandatory)]
        [string[]]$Arguments,
        [Parameter(Mandatory)]
        [string]$Label,
        [string]$WorkingDirectory
    )

    $previousLocation = Get-Location
    try {
        if (-not [string]::IsNullOrWhiteSpace($WorkingDirectory)) {
            Set-Location -LiteralPath $WorkingDirectory
        }
        & $FilePath @Arguments
        $exitCode = $LASTEXITCODE
    } finally {
        Set-Location -LiteralPath $previousLocation
    }
    if ($exitCode -ne 0) {
        throw "$Label failed with exit code $exitCode"
    }
}

function Assert-ReleaseInputs {
    if ([string]::IsNullOrWhiteSpace($InstanceId) -or $InstanceId -notmatch '^i-[a-z0-9]+$') {
        throw "A valid -InstanceId or UMOWEB_INSTANCE_ID is required"
    }
    if (-not (Test-Path -LiteralPath $Workbench -PathType Leaf)) {
        throw "Workbench CLI not found: $Workbench"
    }
    if (-not (Test-Path -LiteralPath $RepositoryRoot -PathType Container)) {
        throw "Repository root not found: $RepositoryRoot"
    }
    if ($RemoteRoot -notmatch '^/[A-Za-z0-9._/-]+$') {
        throw "RemoteRoot contains unsupported characters: $RemoteRoot"
    }
    if ($RetentionCount -lt 1) {
        throw "RetentionCount must be positive"
    }
    if ($MaxArchiveBytes -lt 1 -or $MaxArchiveBytes -gt 1073741824) {
        throw "MaxArchiveBytes must be between 1 and 1 GiB"
    }
    if (-not [string]::IsNullOrWhiteSpace($PublicBaseUrl) -and $PublicBaseUrl -notmatch '^https?://[A-Za-z0-9._:-]+(?:/[A-Za-z0-9._/-]*)?$') {
        throw "PublicBaseUrl must be an http(s) URL without a query string"
    }
}

function Assert-PublicBaseUrl {
    if ([string]::IsNullOrWhiteSpace($PublicBaseUrl)) {
        throw "This action requires -PublicBaseUrl or UMOWEB_PUBLIC_BASE_URL"
    }
}

function Invoke-WorkbenchCommand {
    param(
        [Parameter(Mandatory)]
        [string]$Command,
        [int]$TimeoutSeconds = 60
    )

    $arguments = @(
        "exec",
        "--instance-id", $InstanceId,
        "--command", $Command,
        "--timeout", $TimeoutSeconds.ToString(),
        "--output", "json"
    )
    $output = & $Workbench @arguments 2>&1
    $exitCode = $LASTEXITCODE
    $text = ($output | Out-String).Trim()
    if ($exitCode -ne 0) {
        throw "Workbench exec failed with exit code $exitCode`n$text"
    }

    $result = $text | ConvertFrom-Json
    if ([int]$result.exit_code -ne 0) {
        throw "Remote command failed with exit code $($result.exit_code)`n$($result.stderr)"
    }
    return [string]$result.stdout
}

function Invoke-WorkbenchUpload {
    param(
        [Parameter(Mandatory)]
        [string]$LocalPath,
        [Parameter(Mandatory)]
        [string]$RemotePath
    )

    requireLocalFile $LocalPath
    Invoke-NativeStreaming `
        -FilePath $Workbench `
        -Arguments @("upload", "-f", "--instance-id", $InstanceId, $LocalPath, $RemotePath) `
        -Label "Workbench upload"
}

function Invoke-WorkbenchDownload {
    param(
        [Parameter(Mandatory)]
        [string]$RemotePath,
        [Parameter(Mandatory)]
        [string]$LocalPath
    )

    $remoteArguments = @("download", "--instance-id", $InstanceId, $RemotePath)
    if (Test-Path -LiteralPath $LocalPath) {
        $remoteArguments += "-f"
    }
    $remoteArguments += $LocalPath
    Invoke-NativeStreaming `
        -FilePath $Workbench `
        -Arguments $remoteArguments `
        -Label "Workbench download"
}

function requireLocalFile {
    param(
        [Parameter(Mandatory)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Local file not found: $Path"
    }
}

function Sync-RemoteReleaseControl {
    Invoke-WorkbenchCommand `
        -Command "mkdir -p '$RemoteRoot/scripts/release' '$RemoteIncomingRoot' && chmod 0700 '$RemoteRoot/releases' '$RemoteIncomingRoot'" |
        Out-Null
    Invoke-WorkbenchUpload `
        -LocalPath (Join-Path $PSScriptRoot "remote-release.sh") `
        -RemotePath $RemoteReleaseScript
}

function Invoke-RemoteRelease {
    param(
        [Parameter(Mandatory)]
        [string[]]$Arguments,
        [int]$TimeoutSeconds = 300
    )

    $escapedArguments = $Arguments | ForEach-Object {
        "'" + $_.Replace("'", "'\''") + "'"
    }
    $environmentPrefix = ""
    if (-not [string]::IsNullOrWhiteSpace($PublicBaseUrl)) {
        $escapedPublicUrl = $PublicBaseUrl.Replace("'", "'\''")
        $environmentPrefix = "RELEASE_PUBLIC_BASE_URL='$escapedPublicUrl' "
    }
    $command = "${environmentPrefix}bash '$RemoteReleaseScript' $($escapedArguments -join ' ')"
    return Invoke-WorkbenchCommand -Command $command -TimeoutSeconds $TimeoutSeconds
}

function Get-GitCommit {
    return Invoke-NativeCapture `
        -FilePath "git" `
        -Arguments @("-C", $RepositoryRoot, "rev-parse", "HEAD") `
        -Label "Read Git commit"
}

function Get-ShortGitCommit {
    param(
        [Parameter(Mandatory)]
        [string]$Commit
    )

    return $Commit.Substring(0, 12)
}

function Assert-ReleaseWorkspace {
    $branch = Invoke-NativeCapture `
        -FilePath "git" `
        -Arguments @("-C", $RepositoryRoot, "branch", "--show-current") `
        -Label "Read Git branch"
    if ($branch -ne "master") {
        throw "Publish must run from master, current branch is $branch"
    }

    $dirty = Invoke-NativeCapture `
        -FilePath "git" `
        -Arguments @("-C", $RepositoryRoot, "status", "--porcelain") `
        -Label "Check Git status"
    if (-not [string]::IsNullOrWhiteSpace($dirty)) {
        throw "Publish requires a clean worktree"
    }
}

function Get-LocalTagCommit {
    param(
        [Parameter(Mandatory)]
        [string]$Tag
    )

    $output = & git -C $RepositoryRoot rev-parse --verify --quiet "$Tag^{commit}" 2>$null
    if ($LASTEXITCODE -ne 0) {
        return ""
    }
    return ([string]($output | Select-Object -First 1)).Trim()
}

function Get-RemoteTagCommit {
    param(
        [Parameter(Mandatory)]
        [string]$Tag
    )

    $output = Invoke-NativeCapture `
        -FilePath "git" `
        -Arguments @("-C", $RepositoryRoot, "ls-remote", "--tags", "origin", "refs/tags/$Tag") `
        -Label "Check remote Git tag"
    return Select-RemoteTagCommit -LsRemoteOutput $output -Tag $Tag
}

function Publish-ReleaseTag {
    param(
        [Parameter(Mandatory)]
        [string]$Tag,
        [Parameter(Mandatory)]
        [string]$Commit
    )

    $localTagCommit = Get-LocalTagCommit -Tag $Tag
    $remoteTagCommit = Get-RemoteTagCommit -Tag $Tag
    Assert-TagCanBePublished `
        -Tag $Tag `
        -Commit $Commit `
        -LocalTagCommit $localTagCommit `
        -RemoteTagCommit $remoteTagCommit

    if ([string]::IsNullOrWhiteSpace($localTagCommit)) {
        Invoke-NativeCapture `
            -FilePath "git" `
            -Arguments @("-C", $RepositoryRoot, "tag", "-a", $Tag, "-m", "UmoWeb $Tag") `
            -Label "Create Git tag" |
            Out-Null
        $localTagCommit = Get-LocalTagCommit -Tag $Tag
    }
    if ([string]::IsNullOrWhiteSpace($remoteTagCommit)) {
        Invoke-NativeCapture `
            -FilePath "git" `
            -Arguments @("-C", $RepositoryRoot, "push", "origin", $Tag) `
            -Label "Push Git tag" |
            Out-Null
    }
}

function Get-SuccessfulCiRun {
    param(
        [Parameter(Mandatory)]
        [string]$Commit
    )

    $runsJson = Invoke-NativeCapture `
        -FilePath "gh" `
        -Arguments @(
            "run", "list",
            "--workflow", "CI",
            "--commit", $Commit,
            "--status", "completed",
            "--limit", "20",
            "--json", "databaseId,status,conclusion,headSha,createdAt,url,event"
        ) `
        -Label "Check GitHub CI" `
        -WorkingDirectory $RepositoryRoot
    return Select-SuccessfulCiRun -RunsJson $runsJson -Commit $Commit
}

function Get-RemoteAdminPath {
    $command = "grep '^VITE_ADMIN_PATH=' '$RemoteRoot/.env.docker' | cut -d= -f2- | tr -d '\r\n'"
    $value = Invoke-WorkbenchCommand -Command $command
    if ($value -notmatch '^/[A-Za-z0-9._/-]+$') {
        throw "Remote VITE_ADMIN_PATH is missing or invalid"
    }
    return $value
}

function Get-DockerImageId {
    param(
        [Parameter(Mandatory)]
        [string]$Tag
    )

    return Invoke-NativeCapture `
        -FilePath "docker" `
        -Arguments @("image", "inspect", "--format", "{{.Id}}", $Tag) `
        -Label "Inspect Docker image $Tag"
}

function Write-JsonFile {
    param(
        [Parameter(Mandatory)]
        $Value,
        [Parameter(Mandatory)]
        [string]$Path
    )

    $json = $Value | ConvertTo-Json -Depth 10
    [System.IO.File]::WriteAllText($Path, "$json`n", [System.Text.UTF8Encoding]::new($false))
}

function Resolve-ReleaseArtifact {
    param(
        [Parameter(Mandatory)]
        [string]$Path
    )

    $resolved = Resolve-Path -LiteralPath $Path -ErrorAction Stop
    if (Test-Path -LiteralPath $resolved.Path -PathType Container) {
        $directory = $resolved.Path
        $archives = @(Get-ChildItem -LiteralPath $directory -File -Filter "*.tar")
        if ($archives.Count -ne 1) {
            throw "Release directory must contain exactly one .tar archive: $directory"
        }
        $archivePath = $archives[0].FullName
        $manifestPath = Join-Path $directory "manifest.json"
    } elseif (Test-Path -LiteralPath $resolved.Path -PathType Leaf) {
        if ([System.IO.Path]::GetExtension($resolved.Path) -ne ".tar") {
            throw "Rollback artifact must be a .tar archive or release directory"
        }
        $archivePath = $resolved.Path
        $manifestPath = Join-Path ([System.IO.Path]::GetDirectoryName($archivePath)) "manifest.json"
    } else {
        throw "Release artifact not found: $Path"
    }

    $manifest = Read-ReleaseManifest -Path $manifestPath
    if ($manifest.archive.fileName -ne [System.IO.Path]::GetFileName($archivePath)) {
        throw "Manifest archive name does not match $([System.IO.Path]::GetFileName($archivePath))"
    }
    $archive = Get-Item -LiteralPath $archivePath
    if ($archive.Length -ne [long]$manifest.archive.size) {
        throw "Release archive size does not match manifest"
    }
    $hash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -ne $manifest.archive.sha256.ToLowerInvariant()) {
        throw "Release archive SHA-256 does not match manifest"
    }

    return [pscustomobject]@{
        ArchivePath  = $archivePath
        ManifestPath = $manifestPath
        Manifest     = $manifest
    }
}

function Upload-ReleaseArtifact {
    param(
        [Parameter(Mandatory)]
        [string]$ReleaseId,
        [Parameter(Mandatory)]
        [string]$ArchivePath,
        [Parameter(Mandatory)]
        [string]$ManifestPath
    )

    if ($ReleaseId -notmatch '^[A-Za-z0-9][A-Za-z0-9_.-]*$') {
        throw "Invalid release ID: $ReleaseId"
    }

    $remoteDirectory = "$RemoteIncomingRoot/$ReleaseId"
    Invoke-WorkbenchCommand -Command "mkdir -p '$remoteDirectory' && chmod 0700 '$remoteDirectory'" | Out-Null
    Invoke-WorkbenchUpload -LocalPath $ArchivePath -RemotePath "$remoteDirectory/"
    Invoke-WorkbenchUpload -LocalPath $ManifestPath -RemotePath "$remoteDirectory/"

    return [pscustomobject]@{
        Archive  = "$remoteDirectory/$([System.IO.Path]::GetFileName($ArchivePath))"
        Manifest = "$remoteDirectory/manifest.json"
    }
}

function Invoke-CaptureBaseline {
    Assert-ReleaseInputs
    if ([string]::IsNullOrWhiteSpace($Name) -or $Name -notmatch '^[A-Za-z0-9][A-Za-z0-9_.-]*$') {
        throw "CaptureBaseline requires a valid -Name"
    }
    if (Test-Path -LiteralPath (Join-Path $ArtifactRoot $Name)) {
        throw "Local baseline artifact already exists: $Name"
    }

    Sync-RemoteReleaseControl
    $manifestJson = Invoke-RemoteRelease -Arguments @("capture", "--name", $Name)
    $manifest = $manifestJson | ConvertFrom-Json
    Assert-ReleaseManifest -Manifest $manifest
    if ($manifest.kind -ne "baseline" -or $manifest.releaseId -ne $Name) {
        throw "Remote baseline capture returned an unexpected manifest"
    }

    $partialDirectory = Join-Path $ArtifactRoot ".$Name.partial"
    $finalDirectory = Join-Path $ArtifactRoot $Name
    if (Test-Path -LiteralPath $partialDirectory) {
        Remove-Item -LiteralPath $partialDirectory -Recurse -Force
    }
    New-Item -ItemType Directory -Path $partialDirectory -Force | Out-Null
    try {
        $remoteArchive = "$RemoteIncomingRoot/$Name/$($manifest.archive.fileName)"
        $localArchive = Join-Path $partialDirectory $manifest.archive.fileName
        Invoke-WorkbenchDownload -RemotePath $remoteArchive -LocalPath $localArchive

        $archive = Get-Item -LiteralPath $localArchive
        if ($archive.Length -ne [long]$manifest.archive.size) {
            throw "Downloaded baseline archive size does not match manifest"
        }
        $hash = (Get-FileHash -LiteralPath $localArchive -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($hash -ne $manifest.archive.sha256.ToLowerInvariant()) {
            throw "Downloaded baseline archive SHA-256 does not match manifest"
        }

        Write-JsonFile -Value $manifest -Path (Join-Path $partialDirectory "manifest.json")
        Move-Item -LiteralPath $partialDirectory -Destination $finalDirectory
        Invoke-RemoteRelease -Arguments @("cleanup-capture", "--name", $Name) -TimeoutSeconds 120 | Out-Null
        Remove-OldReleaseArtifacts -ArtifactRoot $ArtifactRoot -RetentionCount $RetentionCount
        Write-Host "Baseline captured: $finalDirectory"
    } finally {
        if (Test-Path -LiteralPath $partialDirectory) {
            Remove-Item -LiteralPath $partialDirectory -Recurse -Force
        }
    }
}

function Invoke-Publish {
    Assert-ReleaseInputs
    if ([string]::IsNullOrWhiteSpace($Version)) {
        throw "Publish requires -Version"
    }
    Assert-ReleaseVersion -Version $Version
    Assert-PublicBaseUrl
    Assert-ReleaseWorkspace

    $commit = Get-GitCommit
    $shortCommit = Get-ShortGitCommit -Commit $commit
    $localTagCommit = Get-LocalTagCommit -Tag $Version
    $remoteTagCommit = Get-RemoteTagCommit -Tag $Version
    Assert-TagCanBePublished `
        -Tag $Version `
        -Commit $commit `
        -LocalTagCommit $localTagCommit `
        -RemoteTagCommit $remoteTagCommit

    $ciRun = Get-SuccessfulCiRun -Commit $commit
    Write-Host "CI gate passed: run $($ciRun.databaseId) for $shortCommit"

    Sync-RemoteReleaseControl
    $finalDirectory = Join-Path $ArtifactRoot $Version
    $reuseFinal = $false
    $archivePath = $null
    $manifestPath = $null
    $manifest = $null
    if (Test-Path -LiteralPath $finalDirectory) {
        $existingRelease = Resolve-ReleaseArtifact -Path $finalDirectory
        if ($existingRelease.Manifest.kind -ne "release" -or
            $existingRelease.Manifest.version -ne $Version -or
            $existingRelease.Manifest.gitCommit -ne $commit) {
            throw "Existing local release artifact does not match $Version at $shortCommit"
        }

        $stateJson = Invoke-RemoteRelease -Arguments @("verify-current") -TimeoutSeconds 120
        $state = $stateJson | ConvertFrom-Json
        if (Test-ReleaseStateMatchesManifest -State $state -Manifest $existingRelease.Manifest) {
            Publish-ReleaseTag -Tag $Version -Commit $commit
            Remove-OldReleaseArtifacts -ArtifactRoot $ArtifactRoot -RetentionCount $RetentionCount
            Write-Host "Release already deployed and verified: $Version"
            Write-Host "Artifact: $finalDirectory"
            return
        }

        Write-Host "Reusing verified local release artifact to redeploy $Version"
        $reuseFinal = $true
        $archivePath = $existingRelease.ArchivePath
        $manifestPath = $existingRelease.ManifestPath
        $manifest = $existingRelease.Manifest
    }

    $partialDirectory = Join-Path $ArtifactRoot ".$Version.partial"
    $resumePartial = $reuseFinal
    if (-not $reuseFinal -and (Test-Path -LiteralPath $partialDirectory)) {
        try {
            $partialRelease = Resolve-ReleaseArtifact -Path $partialDirectory
            if ($partialRelease.Manifest.kind -eq "release" -and
                $partialRelease.Manifest.version -eq $Version -and
                $partialRelease.Manifest.gitCommit -eq $commit) {
                $resumePartial = $true
                $archivePath = $partialRelease.ArchivePath
                $manifestPath = $partialRelease.ManifestPath
                $manifest = $partialRelease.Manifest
                Write-Host "Reusing verified partial release artifact for $Version"
            } else {
                Remove-Item -LiteralPath $partialDirectory -Recurse -Force
            }
        } catch {
            Write-Warning "Discarding incomplete partial release artifact: $partialDirectory"
            Remove-Item -LiteralPath $partialDirectory -Recurse -Force
        }
    }

    $adminPath = Get-RemoteAdminPath
    $adminPathHash = Get-Sha256String -Value $adminPath
    Write-Host "Using ECS frontend build configuration (admin path SHA-256: $adminPathHash)"

    if ($resumePartial) {
        if ($manifest.adminPathSha256 -ne $adminPathHash) {
            throw "Partial release artifact was built with a different frontend admin path"
        }
    } else {
        New-Item -ItemType Directory -Path $partialDirectory -Force | Out-Null
        $backendVersionTag = "umoweb-backend:$Version"
        $frontendVersionTag = "umoweb-frontend:$Version"
        $backendCommitTag = "umoweb-backend:sha-$shortCommit"
        $frontendCommitTag = "umoweb-frontend:sha-$shortCommit"

        Write-Host "Building backend image..."
        Invoke-NativeStreaming `
            -FilePath "docker" `
            -Arguments @(
                "build",
                "-t", $backendVersionTag,
                "-t", $backendCommitTag,
                "-f", "docker/backend/Dockerfile",
                "."
            ) `
            -Label "Backend image build" `
            -WorkingDirectory $RepositoryRoot

        Write-Host "Building frontend image..."
        Invoke-NativeStreaming `
            -FilePath "docker" `
            -Arguments @(
                "build",
                "--build-arg", "VITE_ADMIN_PATH=$adminPath",
                "-t", $frontendVersionTag,
                "-t", $frontendCommitTag,
                "-f", "docker/frontend/Dockerfile",
                "."
            ) `
            -Label "Frontend image build" `
            -WorkingDirectory $RepositoryRoot

        $backendImageId = Get-DockerImageId -Tag $backendVersionTag
        $frontendImageId = Get-DockerImageId -Tag $frontendVersionTag
        $archiveName = "umoweb-images-$Version-$shortCommit.tar"
        $archivePath = Join-Path $partialDirectory $archiveName

        Write-Host "Saving versioned images..."
        Invoke-NativeStreaming `
            -FilePath "docker" `
            -Arguments @(
                "save",
                "-o", $archivePath,
                $backendVersionTag,
                $frontendVersionTag,
                $backendCommitTag,
                $frontendCommitTag
            ) `
            -Label "Docker image archive"
        $archive = Get-Item -LiteralPath $archivePath
        if ($archive.Length -gt $MaxArchiveBytes) {
            throw "Release archive is $($archive.Length) bytes, above the $MaxArchiveBytes byte upload limit"
        }
        $archiveHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
        $manifest = New-ReleaseManifest `
            -ReleaseId $Version `
            -Kind "release" `
            -Version $Version `
            -GitCommit $commit `
            -CiRunId $ciRun.databaseId `
            -BuiltAtUtc ([datetime]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ")) `
            -BackendTag $backendVersionTag `
            -BackendCommitTag $backendCommitTag `
            -BackendImageId $backendImageId `
            -FrontendTag $frontendVersionTag `
            -FrontendCommitTag $frontendCommitTag `
            -FrontendImageId $frontendImageId `
            -ArchiveFileName $archiveName `
            -ArchiveSha256 $archiveHash `
            -ArchiveSize $archive.Length `
            -AdminPath $adminPath
        $manifestPath = Join-Path $partialDirectory "manifest.json"
        Write-JsonFile -Value $manifest -Path $manifestPath
    }

    $remote = Upload-ReleaseArtifact `
        -ReleaseId $Version `
        -ArchivePath $archivePath `
        -ManifestPath $manifestPath

    Write-Host "Deploying $Version to ECS..."
    Invoke-RemoteRelease `
        -Arguments @("deploy", $remote.Archive, $remote.Manifest) `
        -TimeoutSeconds 300 |
        Write-Host

    if (-not $reuseFinal) {
        Move-Item -LiteralPath $partialDirectory -Destination $finalDirectory
    }
    Publish-ReleaseTag -Tag $Version -Commit $commit

    Remove-OldReleaseArtifacts -ArtifactRoot $ArtifactRoot -RetentionCount $RetentionCount
    Write-Host "Release completed: $Version"
    Write-Host "Artifact: $finalDirectory"
}

function Invoke-Rollback {
    Assert-ReleaseInputs
    Assert-PublicBaseUrl
    if ([string]::IsNullOrWhiteSpace($Artifact)) {
        throw "Rollback requires -Artifact"
    }

    $release = Resolve-ReleaseArtifact -Path $Artifact
    Sync-RemoteReleaseControl
    $remote = Upload-ReleaseArtifact `
        -ReleaseId $release.Manifest.releaseId `
        -ArchivePath $release.ArchivePath `
        -ManifestPath $release.ManifestPath
    Write-Host "Rolling back to $($release.Manifest.releaseId)..."
    Invoke-RemoteRelease `
        -Arguments @("rollback", $remote.Archive, $remote.Manifest) `
        -TimeoutSeconds 300 |
        Write-Host
    Write-Host "Rollback completed: $($release.Manifest.releaseId)"
}

function Invoke-Verify {
    Assert-ReleaseInputs
    Assert-PublicBaseUrl
    Sync-RemoteReleaseControl
    $stateJson = Invoke-RemoteRelease -Arguments @("verify-current") -TimeoutSeconds 120
    $state = $stateJson | ConvertFrom-Json
    Assert-ReleaseManifest -Manifest $state
    Write-Host "Current release: $($state.releaseId)"
    Write-Host "Backend: $($state.backendImage.tag) $($state.backendImage.imageId)"
    Write-Host "Frontend: $($state.frontendImage.tag) $($state.frontendImage.imageId)"
    Write-Host "Operation: $($state.operation)"
}

switch ($Action) {
    "CaptureBaseline" { Invoke-CaptureBaseline }
    "Publish" { Invoke-Publish }
    "Rollback" { Invoke-Rollback }
    "Verify" { Invoke-Verify }
}
