[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$BaseUrl,

    [Parameter(Mandatory)]
    [string]$Username,

    [Parameter(Mandatory)]
    [string]$Password,

    [Parameter(Mandatory)]
    [string]$OutputDirectory,

    [string[]]$Modes = @(
        "STRUCTURE_CLEANUP",
        "MODERN_TO_CLASSICAL",
        "ENGLISH_TO_CHINESE",
        "CHINESE_TO_ENGLISH",
        "LIGHT_NOVELIZATION"
    ),

    [int]$MaxCalls = 5
)

$ErrorActionPreference = "Stop"

$script:BaseUrl = $BaseUrl.TrimEnd("/")
$script:ExitCode = 0
$script:TotalCalls = 0
$script:TotalInputTokens = 0
$script:TotalOutputTokens = 0
$script:TotalDurationMs = 0
$script:StoppedOnRateLimit = $false
$script:StartupError = $null

$outputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
$casesPath = Join-Path $PSScriptRoot "evaluation\cases.json"
$resultsPath = Join-Path $outputDirectory "results.json"
$reviewPath = Join-Path $outputDirectory "review.md"

function ConvertTo-RequestJson {
    param([object]$Value)

    $Value | ConvertTo-Json -Depth 30 -Compress
}

function Invoke-Api {
    param(
        [Parameter(Mandatory)]
        [string]$Method,

        [Parameter(Mandatory)]
        [string]$Path,

        [object]$Body = $null,
        [string]$Token = ""
    )

    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $headers.Authorization = "Bearer $Token"
    }

    $request = @{
        Method = $Method
        Uri = "$script:BaseUrl$Path"
        Headers = $headers
        SkipHttpErrorCheck = $true
        ErrorAction = "Stop"
    }
    if ($null -ne $Body) {
        $request.ContentType = "application/json"
        $request.Body = ConvertTo-RequestJson -Value $Body
    }

    Invoke-WebRequest @request
}

function Read-ResponseJson {
    param([object]$Response)

    if ($null -eq $Response -or [string]::IsNullOrWhiteSpace($Response.Content)) {
        return $null
    }
    return $Response.Content | ConvertFrom-Json
}

function Get-ResponseErrorCode {
    param([object]$Response)

    $errorBody = Read-ResponseJson -Response $Response
    if ($null -eq $errorBody) {
        return $null
    }
    return $errorBody.code
}

function New-CaseRecord {
    param(
        [object]$Case,
        [string]$Status,
        [string]$Reason,
        [object]$LastResult
    )

    return [ordered]@{
        id = [string]$Case.id
        modeKey = [string]$Case.modeKey
        source = [string]$Case.source
        criteria = @($Case.criteria)
        status = $Status
        reason = $Reason
        attempts = 0
        lastResult = $LastResult
    }
}

function Get-NormalizedModes {
    param([string[]]$Value)

    return @($Value | ForEach-Object { $_.Split(",") } |
        ForEach-Object { $_.Trim() } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        Select-Object -Unique)
}

New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

$casesData = Get-Content -LiteralPath $casesPath -Raw | ConvertFrom-Json
$allCases = @($casesData.cases)
$requestedModeKeys = @(Get-NormalizedModes -Value $Modes)
$enabledModeKeys = @()
$caseRecords = New-Object System.Collections.Generic.List[object]

$priorSuccess = @{}
if (Test-Path -LiteralPath $resultsPath) {
    try {
        $prior = Get-Content -LiteralPath $resultsPath -Raw | ConvertFrom-Json
        foreach ($priorCase in @($prior.cases)) {
            if ($null -ne $priorCase -and $priorCase.status -eq "success") {
                $priorSuccess[[string]$priorCase.id] = $priorCase
            }
        }
    }
    catch {
        # A malformed or partial prior file must not stop a fresh evaluation.
    }
}

try {
    $loginBody = @{
        username = $Username
        password = $Password
    }
    $loginResponse = Invoke-Api -Method POST -Path "/api/admin/login" -Body $loginBody
    if ($loginResponse.StatusCode -ne 200) {
        throw "Login request returned HTTP $($loginResponse.StatusCode)."
    }
    $login = Read-ResponseJson -Response $loginResponse
    if ($null -eq $login -or [string]::IsNullOrWhiteSpace($login.token)) {
        throw "Login response did not contain a token."
    }
    $token = [string]$login.token

    $capabilitiesResponse = Invoke-Api `
        -Method GET `
        -Path "/api/admin/ai/capabilities" `
        -Token $token
    if ($capabilitiesResponse.StatusCode -ne 200) {
        throw "Capabilities request returned HTTP $($capabilitiesResponse.StatusCode)."
    }
    $capabilities = Read-ResponseJson -Response $capabilitiesResponse
    $capabilityModes = @()
    if ($null -ne $capabilities -and $null -ne $capabilities.modes) {
        $capabilityModes = @($capabilities.modes)
    }
    $enabledModeKeys = @($capabilityModes | ForEach-Object { $_.modeKey })

    foreach ($case in $allCases) {
        $id = [string]$case.id
        if ($priorSuccess.ContainsKey($id)) {
            $priorCase = $priorSuccess[$id]
            $attempts = 0
            if ($null -ne $priorCase -and $null -ne $priorCase.attempts) {
                $attempts = [int]$priorCase.attempts
            }
            $record = [ordered]@{
                id = $id
                modeKey = [string]$case.modeKey
                source = [string]$case.source
                criteria = @($case.criteria)
                status = "success"
                reason = "from_previous_run"
                attempts = $attempts
                lastResult = $priorCase.lastResult
            }
        }
        elseif ($requestedModeKeys -notcontains $case.modeKey) {
            $record = New-CaseRecord -Case $case -Status "skipped" `
                -Reason "not_requested" -LastResult $null
        }
        elseif ($enabledModeKeys -notcontains $case.modeKey) {
            $record = New-CaseRecord -Case $case -Status "skipped" `
                -Reason "mode_disabled" -LastResult $null
        }
        else {
            $record = New-CaseRecord -Case $case -Status "pending" `
                -Reason $null -LastResult $null
        }
        $caseRecords.Add($record)
    }

    $pending = @($caseRecords | Where-Object { $_.status -eq "pending" })
    foreach ($case in $pending) {
        if ($script:TotalCalls -ge $MaxCalls) {
            break
        }

        $script:TotalCalls++
        $case.attempts = [int]$case.attempts + 1
        $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

        try {
            $transformResponse = Invoke-Api `
                -Method POST `
                -Path "/api/admin/ai/transform" `
                -Token $token `
                -Body @{
                    modeKey = $case.modeKey
                    content = $case.source
                }
        }
        catch {
            $stopwatch.Stop()
            $case.status = "failed"
            $case.reason = "request_error"
            $case.lastResult = [ordered]@{
                statusCode = $null
                code = $null
                durationMs = [int]$stopwatch.ElapsedMilliseconds
            }
            $script:ExitCode = 1
            break
        }

        $stopwatch.Stop()
        $elapsedMs = [int]$stopwatch.ElapsedMilliseconds

        if ($transformResponse.StatusCode -eq 429) {
            $case.status = "failed"
            $case.reason = "rate_limited"
            $case.lastResult = [ordered]@{
                statusCode = 429
                code = Get-ResponseErrorCode -Response $transformResponse
                durationMs = $elapsedMs
            }
            $script:StoppedOnRateLimit = $true
            $script:ExitCode = 2
            break
        }

        if ($transformResponse.StatusCode -ne 200) {
            $case.status = "failed"
            $case.reason = "request_failed"
            $case.lastResult = [ordered]@{
                statusCode = [int]$transformResponse.StatusCode
                code = Get-ResponseErrorCode -Response $transformResponse
                durationMs = $elapsedMs
            }
            $script:ExitCode = 1
            break
        }

        $transform = Read-ResponseJson -Response $transformResponse
        if ($null -eq $transform -or
            [string]::IsNullOrWhiteSpace($transform.requestId) -or
            $null -eq $transform.usage) {
            $case.status = "failed"
            $case.reason = "invalid_response"
            $case.lastResult = [ordered]@{
                statusCode = 200
                code = $null
                durationMs = $elapsedMs
            }
            $script:ExitCode = 1
            break
        }

        $output = [string]$transform.content
        $usage = $transform.usage
        $inputTokens = if ($null -ne $usage.inputTokens) { [int]$usage.inputTokens } else { 0 }
        $outputTokens = if ($null -ne $usage.outputTokens) { [int]$usage.outputTokens } else { 0 }
        $totalTokens = if ($null -ne $usage.totalTokens) { [int]$usage.totalTokens } else { 0 }

        $case.status = "success"
        $case.reason = $null
        $case.lastResult = [ordered]@{
            requestId = [string]$transform.requestId
            modeKey = [string]$transform.modeKey
            modeVersion = if ($null -ne $transform.modeVersion) { [int]$transform.modeVersion } else { 0 }
            model = [string]$transform.model
            usage = [ordered]@{
                inputTokens = $inputTokens
                outputTokens = $outputTokens
                totalTokens = $totalTokens
            }
            durationMs = $elapsedMs
            inputChars = [int]$case.source.Length
            outputChars = [int]$output.Length
            output = $output
        }

        $script:TotalInputTokens += $inputTokens
        $script:TotalOutputTokens += $outputTokens
        $script:TotalDurationMs += $elapsedMs
    }
}
catch {
    $script:ExitCode = 1
    $script:StartupError = "Evaluation failed: $($_.Exception.Message)"
}

$successCount = @($caseRecords | Where-Object { $_.status -eq "success" }).Count
$failedCount = @($caseRecords | Where-Object { $_.status -eq "failed" }).Count
$skippedCount = @($caseRecords | Where-Object { $_.status -eq "skipped" }).Count
$pendingCount = @($caseRecords | Where-Object { $_.status -eq "pending" }).Count
$totalTokens = $script:TotalInputTokens + $script:TotalOutputTokens
$requestedCaseCount = @($caseRecords | Where-Object {
    $requestedModeKeys -contains $_.modeKey
}).Count
$enabledCaseCount = @($caseRecords | Where-Object {
    $requestedModeKeys -contains $_.modeKey -and
    $enabledModeKeys -contains $_.modeKey
}).Count
$skippedModeKeys = @($requestedModeKeys | Where-Object {
    $enabledModeKeys -notcontains $_
})

$summary = [ordered]@{
    totalCases = $caseRecords.Count
    requestedCases = $requestedCaseCount
    enabledCases = $enabledCaseCount
    succeeded = $successCount
    failed = $failedCount
    skipped = $skippedCount
    pending = $pendingCount
    stoppedOnRateLimit = [bool]$script:StoppedOnRateLimit
    totalCalls = [int]$script:TotalCalls
    totalInputTokens = [int]$script:TotalInputTokens
    totalOutputTokens = [int]$script:TotalOutputTokens
    totalTokens = [int]$totalTokens
    totalDurationMs = [int]$script:TotalDurationMs
}

$resultObject = [ordered]@{
    generatedAt = (Get-Date).ToString("o")
    baseUrl = $script:BaseUrl
    casesPath = $casesPath
    requestedModes = @($requestedModeKeys)
    enabledModes = @($enabledModeKeys)
    skippedModes = @($skippedModeKeys)
    startupError = $script:StartupError
    summary = $summary
    cases = $caseRecords.ToArray()
}

$resultJson = $resultObject | ConvertTo-Json -Depth 30
[System.IO.File]::WriteAllText(
    $resultsPath,
    $resultJson,
    [System.Text.UTF8Encoding]::new($false))

$reviewLines = New-Object System.Collections.Generic.List[string]
$reviewLines.Add("# AI 模式人工评测")
$reviewLines.Add("")
$reviewLines.Add("- 生成时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
$reviewLines.Add("- 请求模式：$($requestedModeKeys -join ', ')")
$reviewLines.Add("- 已启用模式：$($enabledModeKeys -join ', ')")
$reviewLines.Add("- 总调用数：$($script:TotalCalls)")
$reviewLines.Add("- 总 Token：$totalTokens")
$reviewLines.Add("- 成功数：$successCount")
$reviewLines.Add("- 失败数：$failedCount")
$reviewLines.Add("")
$reviewLines.Add("## 评分口径")
$reviewLines.Add("")
$reviewLines.Add("5：完全满足模式要求，无事实问题；4：有轻微风格或措辞问题，事实和结构完整；")
$reviewLines.Add("3：可理解但存在明显遗漏、语气偏差或结构变化；2：关键信息丢失或表达不可用；")
$reviewLines.Add("1：完全失败、空结果或违反模式边界。")
$reviewLines.Add("")

foreach ($case in $caseRecords.ToArray()) {
    $reviewLines.Add("## $($case.id) ($($case.modeKey))")
    $reviewLines.Add("")
    $reviewLines.Add("- 状态：$($case.status)")
    $reviewLines.Add("- 标准：$( @($case.criteria) -join '；' )")

    if ($null -ne $case.lastResult) {
        $reviewLines.Add("- 请求 ID：$($case.lastResult.requestId)")
        $reviewLines.Add("- 模式版本：$($case.lastResult.modeVersion)")
        $reviewLines.Add("- 模型：$($case.lastResult.model)")
        $reviewLines.Add("- 耗时：$($case.lastResult.durationMs) ms")
        $reviewLines.Add("- 长度：输入 $($case.lastResult.inputChars)，输出 $($case.lastResult.outputChars)")
        if ($null -ne $case.lastResult.usage) {
            $reviewLines.Add("- Token：输入 $($case.lastResult.usage.inputTokens)，输出 $($case.lastResult.usage.outputTokens)，总计 $($case.lastResult.usage.totalTokens)")
        }
    }

    if ($case.status -eq "success") {
        $reviewLines.Add("- 评分：__ / 5")
        $reviewLines.Add("- 评语：")
    }
    $reviewLines.Add("")
    $reviewLines.Add("### 原文")
    $reviewLines.Add("````markdown")
    $reviewLines.Add([string]$case.source)
    $reviewLines.Add("````")

    if ($case.status -eq "success" -and $null -ne $case.lastResult) {
        $reviewLines.Add("")
        $reviewLines.Add("### 模型输出")
        $reviewLines.Add("````markdown")
        $reviewLines.Add([string]$case.lastResult.output)
        $reviewLines.Add("````")
    }
    $reviewLines.Add("")
}

[System.IO.File]::WriteAllText(
    $reviewPath,
    ($reviewLines -join [System.Environment]::NewLine),
    [System.Text.UTF8Encoding]::new($false))

if ($script:ExitCode -eq 2) {
    $statusText = "stopped on rate limit"
}
elseif ($script:ExitCode -ne 0) {
    $statusText = "failed"
}
else {
    $statusText = "completed"
}

Write-Host ("Evaluation {0}: totalTokens={1} totalCalls={2} failures={3} outputDirectory={4}" `
    -f $statusText, $totalTokens, $script:TotalCalls, $failedCount, $outputDirectory)

exit $script:ExitCode
