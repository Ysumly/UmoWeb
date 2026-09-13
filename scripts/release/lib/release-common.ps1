Set-StrictMode -Version Latest

function Assert-ReleaseVersion {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$Version
    )

    if ($Version -notmatch '^v(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)(?:-rc\.(?:[1-9][0-9]*))?$') {
        throw "Release version must look like v1.0.0 or v1.0.0-rc.1: $Version"
    }
}

function Get-Sha256String {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [AllowEmptyString()]
        [string]$Value
    )

    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
    return [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
}

function Select-SuccessfulCiRun {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$RunsJson,
        [Parameter(Mandatory)]
        [string]$Commit
    )

    $runs = @($RunsJson | ConvertFrom-Json)
    $selected = $runs |
        Where-Object {
            $_.status -eq "completed" -and
            $_.conclusion -eq "success" -and
            $_.event -eq "push" -and
            $_.headSha -eq $Commit
        } |
        Sort-Object -Property createdAt -Descending |
        Select-Object -First 1

    if ($null -eq $selected) {
        throw "No successful CI run found for commit $Commit"
    }

    return $selected
}

function Select-RemoteTagCommit {
    [CmdletBinding()]
    param(
        [AllowEmptyString()]
        [string]$LsRemoteOutput,
        [Parameter(Mandatory)]
        [string]$Tag
    )

    $lines = @($LsRemoteOutput -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    $peeledSuffix = "refs/tags/$Tag^{}"
    foreach ($line in $lines) {
        $parts = $line -split "\s+", 2
        if ($parts.Count -eq 2 -and $parts[1] -eq $peeledSuffix) {
            return $parts[0]
        }
    }
    foreach ($line in $lines) {
        $parts = $line -split "\s+", 2
        if ($parts.Count -eq 2 -and $parts[1] -eq "refs/tags/$Tag") {
            return $parts[0]
        }
    }
    return ""
}

function Assert-TagCanBePublished {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$Tag,
        [Parameter(Mandatory)]
        [string]$Commit,
        [AllowNull()]
        [string]$LocalTagCommit,
        [AllowNull()]
        [string]$RemoteTagCommit
    )

    if (-not [string]::IsNullOrWhiteSpace($LocalTagCommit) -and $LocalTagCommit -ne $Commit) {
        throw "Local Git tag $Tag points to $LocalTagCommit, not $Commit"
    }
    if (-not [string]::IsNullOrWhiteSpace($RemoteTagCommit) -and $RemoteTagCommit -ne $Commit) {
        throw "Remote Git tag $Tag points to $RemoteTagCommit, not $Commit"
    }
}

function Test-ReleaseStateMatchesManifest {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        $State,
        [Parameter(Mandatory)]
        $Manifest
    )

    foreach ($property in @("releaseId", "version", "gitCommit")) {
        if ([string]$State.$property -ne [string]$Manifest.$property) {
            return $false
        }
    }
    foreach ($image in @("backendImage", "frontendImage")) {
        foreach ($property in @("tag", "imageId")) {
            if ([string]$State.$image.$property -ne [string]$Manifest.$image.$property) {
                return $false
            }
        }
    }
    return $true
}

function New-ReleaseManifest {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$ReleaseId,
        [Parameter(Mandatory)]
        [ValidateSet("release", "baseline")]
        [string]$Kind,
        [AllowNull()]
        [string]$Version,
        [AllowNull()]
        [string]$GitCommit,
        [AllowNull()]
        [long]$CiRunId,
        [Parameter(Mandatory)]
        [string]$BuiltAtUtc,
        [Parameter(Mandatory)]
        [string]$BackendTag,
        [AllowNull()]
        [string]$BackendCommitTag,
        [Parameter(Mandatory)]
        [string]$BackendImageId,
        [Parameter(Mandatory)]
        [string]$FrontendTag,
        [AllowNull()]
        [string]$FrontendCommitTag,
        [Parameter(Mandatory)]
        [string]$FrontendImageId,
        [Parameter(Mandatory)]
        [string]$ArchiveFileName,
        [Parameter(Mandatory)]
        [string]$ArchiveSha256,
        [Parameter(Mandatory)]
        [long]$ArchiveSize,
        [Parameter(Mandatory)]
        [string]$AdminPath
    )

    if ($ReleaseId -notmatch '^[A-Za-z0-9][A-Za-z0-9_.-]*$') {
        throw "Invalid release ID: $ReleaseId"
    }
    foreach ($imageId in @($BackendImageId, $FrontendImageId)) {
        if ($imageId -notmatch '^sha256:[A-Fa-f0-9]{64}$') {
            throw "Invalid Docker image ID: $imageId"
        }
    }
    if ($ArchiveSha256 -notmatch '^[A-Fa-f0-9]{64}$') {
        throw "Invalid archive SHA-256: $ArchiveSha256"
    }
    if ($ArchiveSize -le 0) {
        throw "Archive size must be positive"
    }

    return [pscustomobject][ordered]@{
        schemaVersion   = 1
        releaseId       = $ReleaseId
        kind            = $Kind
        version         = $Version
        gitCommit       = $GitCommit
        ciRunId         = $CiRunId
        builtAtUtc      = $BuiltAtUtc
        backendImage    = [ordered]@{
            tag       = $BackendTag
            commitTag = $BackendCommitTag
            imageId   = $BackendImageId
        }
        frontendImage   = [ordered]@{
            tag       = $FrontendTag
            commitTag = $FrontendCommitTag
            imageId   = $FrontendImageId
        }
        archive         = [ordered]@{
            fileName = $ArchiveFileName
            sha256   = $ArchiveSha256
            size     = $ArchiveSize
        }
        adminPathSha256 = Get-Sha256String -Value $AdminPath
    }
}

function Read-ReleaseManifest {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Release manifest not found: $Path"
    }

    $manifest = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    Assert-ReleaseManifest -Manifest $manifest
    return $manifest
}

function Assert-ReleaseManifest {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        $Manifest
    )

    if ($Manifest.schemaVersion -ne 1) {
        throw "Unsupported release manifest schema: $($Manifest.schemaVersion)"
    }
    if ($Manifest.kind -notin @("release", "baseline")) {
        throw "Invalid release manifest kind: $($Manifest.kind)"
    }
    if ([string]::IsNullOrWhiteSpace([string]$Manifest.releaseId)) {
        throw "Release manifest is missing releaseId"
    }
    foreach ($property in @("backendImage", "frontendImage", "archive")) {
        if ($null -eq $Manifest.$property) {
            throw "Release manifest is missing $property"
        }
    }
    foreach ($image in @($Manifest.backendImage, $Manifest.frontendImage)) {
        if ([string]::IsNullOrWhiteSpace([string]$image.tag)) {
            throw "Release manifest contains an empty image tag"
        }
        if ([string]$image.imageId -notmatch '^sha256:[A-Fa-f0-9]{64}$') {
            throw "Release manifest contains an invalid image ID: $($image.imageId)"
        }
    }
    if ([string]$Manifest.archive.sha256 -notmatch '^[A-Fa-f0-9]{64}$') {
        throw "Release manifest contains an invalid archive SHA-256"
    }
    if ([long]$Manifest.archive.size -le 0) {
        throw "Release manifest contains an invalid archive size"
    }
    if ([string]$Manifest.adminPathSha256 -notmatch '^[A-Fa-f0-9]{64}$') {
        throw "Release manifest contains an invalid admin path SHA-256"
    }
}

function Remove-OldReleaseArtifacts {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$ArtifactRoot,
        [int]$RetentionCount = 2
    )

    if ($RetentionCount -lt 1) {
        throw "RetentionCount must be positive"
    }
    if (-not (Test-Path -LiteralPath $ArtifactRoot -PathType Container)) {
        return
    }

    $releases = Get-ChildItem -LiteralPath $ArtifactRoot -Directory |
        ForEach-Object {
            $manifestPath = Join-Path $_.FullName "manifest.json"
            if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
                return
            }

            try {
                $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
                [pscustomobject]@{
                    Directory = $_
                    BuiltAtUtc = [datetime]$manifest.builtAtUtc
                }
            } catch {
                Write-Warning "Ignoring unreadable release manifest: $manifestPath"
            }
        } |
        Sort-Object -Property BuiltAtUtc -Descending

    if ($releases.Count -le $RetentionCount) {
        return
    }

    foreach ($release in $releases[$RetentionCount..($releases.Count - 1)]) {
        Remove-Item -LiteralPath $release.Directory.FullName -Recurse -Force
    }
}
