[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$productionScript = Join-Path $repoRoot "scripts\ai\evaluate-ai-modes.ps1"
$testRoot = Join-Path $env:TEMP ("umo-ai-eval-test-" + [guid]::NewGuid().ToString("N"))
$pwsh = (Get-Command pwsh -ErrorAction Stop).Source

$script:Failures = New-Object System.Collections.Generic.List[string]
$script:SecretPassword = "S3cret-Eval-Password-Should-Not-Leak"
$script:MockToken = "mock-eval-token-should-not-leak"

Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Threading;

public sealed class EvaluationMockServer : IDisposable
{
    private readonly TcpListener listener;
    private Thread worker;
    private volatile bool stopping;
    private readonly object gate = new object();
    private int transformCalls;
    private readonly List<string> seenSources = new List<string>();
    private readonly List<string> seenModes = new List<string>();

    public int Port { get; private set; }
    public string Scenario { get; set; }
    public int FailOnCall { get; set; }
    public int RateLimitOnCall { get; set; }
    public string[] EnabledModes { get; set; }

    public string BaseUrl
    {
        get { return "http://127.0.0.1:" + Port; }
    }

    public int TransformCalls
    {
        get { lock (gate) { return transformCalls; } }
    }

    public string[] SeenSources
    {
        get { lock (gate) { return seenSources.ToArray(); } }
    }

    public string[] SeenModes
    {
        get { lock (gate) { return seenModes.ToArray(); } }
    }

    public EvaluationMockServer()
    {
        listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        Port = ((IPEndPoint)listener.LocalEndpoint).Port;
        Scenario = "success";
        EnabledModes = new string[]
        {
            "STRUCTURE_CLEANUP",
            "MODERN_TO_CLASSICAL",
            "ENGLISH_TO_CHINESE",
            "CHINESE_TO_ENGLISH",
            "LIGHT_NOVELIZATION"
        };
    }

    public void Start()
    {
        if (worker != null)
        {
            return;
        }
        worker = new Thread(Run);
        worker.IsBackground = true;
        worker.Start();
    }

    private void Run()
    {
        while (!stopping)
        {
            TcpClient client;
            try
            {
                client = listener.AcceptTcpClient();
            }
            catch
            {
                return;
            }
            ThreadPool.QueueUserWorkItem(_ => Handle(client));
        }
    }

    private void Handle(TcpClient client)
    {
        using (client)
        using (NetworkStream stream = client.GetStream())
        {
            try
            {
                string method;
                string path;
                string body;
                if (!TryReadRequest(stream, out method, out path, out body))
                {
                    return;
                }

                int callNumber = 0;
                string modeKey = null;
                string content = null;
                if (path == "/api/admin/ai/transform" && method == "POST")
                {
                    using (JsonDocument document = JsonDocument.Parse(body))
                    {
                        JsonElement root = document.RootElement;
                        if (root.TryGetProperty("modeKey", out JsonElement modeElement))
                        {
                            modeKey = modeElement.GetString();
                        }
                        if (root.TryGetProperty("content", out JsonElement contentElement))
                        {
                            content = contentElement.GetString();
                        }
                    }
                    lock (gate)
                    {
                        transformCalls++;
                        callNumber = transformCalls;
                        seenModes.Add(modeKey ?? "");
                        seenSources.Add(content ?? "");
                    }
                }

                WriteResponse(stream, BuildResponse(method, path, modeKey, callNumber));
            }
            catch
            {
                WriteResponse(stream, 500, "Internal Server Error",
                    "{\"code\":500,\"message\":\"mock server error\"}");
            }
        }
    }

    private static bool TryReadRequest(
        NetworkStream stream,
        out string method,
        out string path,
        out string body)
    {
        method = null;
        path = null;
        body = "";

        byte[] buffer = new byte[8192];
        using (MemoryStream data = new MemoryStream())
        {
            int headerEnd = -1;
            while (headerEnd < 0)
            {
                int read = stream.Read(buffer, 0, buffer.Length);
                if (read <= 0)
                {
                    return false;
                }
                data.Write(buffer, 0, read);
                headerEnd = IndexOf(data.GetBuffer(), 0, (int)data.Length,
                    new byte[] { 13, 10, 13, 10 });
            }

            byte[] all = data.GetBuffer();
            string headerText = Encoding.UTF8.GetString(all, 0, headerEnd);
            int bodyStart = headerEnd + 4;
            int contentLength = 0;

            string[] lines = headerText.Replace("\r\n", "\n").Split('\n');
            string[] requestLine = lines[0].Split(' ');
            if (requestLine.Length < 2)
            {
                return false;
            }
            method = requestLine[0];
            path = requestLine[1];

            for (int i = 1; i < lines.Length; i++)
            {
                string line = lines[i];
                int colon = line.IndexOf(':');
                if (colon <= 0)
                {
                    continue;
                }
                string name = line.Substring(0, colon).Trim();
                string value = line.Substring(colon + 1).Trim();
                if (string.Equals(name, "Content-Length", StringComparison.OrdinalIgnoreCase))
                {
                    int.TryParse(value, out contentLength);
                }
            }

            int available = (int)data.Length - bodyStart;
            while (available < contentLength)
            {
                int read = stream.Read(buffer, 0, buffer.Length);
                if (read <= 0)
                {
                    break;
                }
                data.Write(buffer, 0, read);
                all = data.GetBuffer();
                available = (int)data.Length - bodyStart;
            }

            if (contentLength > 0 && available >= contentLength)
            {
                body = Encoding.UTF8.GetString(all, bodyStart, contentLength);
            }
            return true;
        }
    }

    private static int IndexOf(byte[] data, int start, int length, byte[] pattern)
    {
        if (pattern.Length == 0 || length < pattern.Length)
        {
            return -1;
        }
        for (int i = start; i <= start + length - pattern.Length; i++)
        {
            bool match = true;
            for (int j = 0; j < pattern.Length; j++)
            {
                if (data[i + j] != pattern[j])
                {
                    match = false;
                    break;
                }
            }
            if (match)
            {
                return i;
            }
        }
        return -1;
    }

    private (int Status, string Reason, string Body) BuildResponse(
        string method,
        string path,
        string modeKey,
        int callNumber)
    {
        if (path == "/api/admin/login" && method == "POST")
        {
            return (200, "OK",
                "{\"token\":\"mock-eval-token-should-not-leak\",\"expiresAt\":\"2026-09-18T12:00:00\"}");
        }

        if (path == "/api/admin/ai/capabilities" && method == "GET")
        {
            StringBuilder modes = new StringBuilder();
            for (int i = 0; i < EnabledModes.Length; i++)
            {
                if (i > 0)
                {
                    modes.Append(",");
                }
                modes.Append("{\"modeKey\":\"")
                    .Append(EnabledModes[i])
                    .Append("\",\"name\":\"")
                    .Append(EnabledModes[i])
                    .Append("\",\"description\":\"\"}");
            }
            string json = "{\"enabled\":true,\"maxInputChars\":20000,\"modes\":["
                + modes.ToString() + "]}";
            return (200, "OK", json);
        }

        if (path == "/api/admin/ai/transform" && method == "POST")
        {
            if (Scenario == "rate_limit" && callNumber == RateLimitOnCall)
            {
                return (429, "Too Many Requests",
                    "{\"code\":429,\"message\":\"AI 服务请求过于频繁\"}");
            }
            if (Scenario == "failure" && callNumber == FailOnCall)
            {
                return (500, "Internal Server Error",
                    "{\"code\":500,\"message\":\"mock transform failure\"}");
            }

            var result = new
            {
                requestId = "req-" + callNumber,
                modeKey = modeKey ?? "",
                modeVersion = 2,
                content = "MOCK-OUTPUT-" + callNumber,
                model = "mock-model",
                usage = new
                {
                    inputTokens = 100 + callNumber,
                    outputTokens = 50 + callNumber,
                    totalTokens = 150 + (2 * callNumber)
                }
            };
            return (200, "OK", JsonSerializer.Serialize(result));
        }

        return (404, "Not Found", "{\"code\":404,\"message\":\"not found\"}");
    }

    private static void WriteResponse(
        NetworkStream stream,
        (int Status, string Reason, string Body) response)
    {
        WriteResponse(stream, response.Status, response.Reason, response.Body);
    }

    private static void WriteResponse(
        NetworkStream stream,
        int status,
        string reason,
        string body)
    {
        byte[] bodyBytes = Encoding.UTF8.GetBytes(body);
        string header = "HTTP/1.1 " + status + " " + reason + "\r\n"
            + "Content-Type: application/json; charset=utf-8\r\n"
            + "Content-Length: " + bodyBytes.Length + "\r\n"
            + "Connection: close\r\n\r\n";
        byte[] headerBytes = Encoding.UTF8.GetBytes(header);
        stream.Write(headerBytes, 0, headerBytes.Length);
        stream.Write(bodyBytes, 0, bodyBytes.Length);
        stream.Flush();
    }

    public void Dispose()
    {
        stopping = true;
        try
        {
            listener.Stop();
        }
        catch
        {
        }
        if (worker != null && worker.IsAlive)
        {
            worker.Join(1000);
        }
    }
}
'@

function Assert-Condition {
    param(
        [bool]$Condition,
        [string]$Name
    )

    if ($Condition) {
        Write-Host "PASS $Name"
    }
    else {
        $script:Failures.Add($Name)
        Write-Host "FAIL $Name"
    }
}

function New-TestServer {
    param(
        [string]$Scenario = "success",
        [int]$FailOnCall = 0,
        [int]$RateLimitOnCall = 0
    )

    $server = New-Object EvaluationMockServer
    $server.Scenario = $Scenario
    $server.FailOnCall = $FailOnCall
    $server.RateLimitOnCall = $RateLimitOnCall
    $server.Start()
    return $server
}

function Invoke-Evaluator {
    param(
        [EvaluationMockServer]$Server,
        [string]$OutputDirectory,
        [string[]]$Modes,
        [int]$MaxCalls
    )

    $arguments = @(
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-File", $productionScript,
        "-BaseUrl", $Server.BaseUrl,
        "-Username", "admin",
        "-Password", $script:SecretPassword,
        "-OutputDirectory", $OutputDirectory,
        "-Modes", ($Modes -join ","),
        "-MaxCalls", [string]$MaxCalls
    )

    $combined = & $pwsh @arguments 2>&1
    return [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Output = ($combined | Out-String)
    }
}

function Read-Results {
    param([string]$OutputDirectory)

    $resultsPath = Join-Path $OutputDirectory "results.json"
    if (-not (Test-Path -LiteralPath $resultsPath)) {
        return $null
    }
    return (Get-Content -LiteralPath $resultsPath -Raw | ConvertFrom-Json)
}

New-Item -ItemType Directory -Path $testRoot -Force | Out-Null

$allModes = @(
    "STRUCTURE_CLEANUP",
    "MODERN_TO_CLASSICAL",
    "ENGLISH_TO_CHINESE",
    "CHINESE_TO_ENGLISH",
    "LIGHT_NOVELIZATION"
)

$servers = New-Object System.Collections.Generic.List[EvaluationMockServer]

try {
    # Success path: no disclosure, one request per pending sample, output and metadata.
    $server1 = New-TestServer -Scenario "success"
    $servers.Add($server1)
    $out1 = Join-Path $testRoot "success"
    $run1 = Invoke-Evaluator -Server $server1 -OutputDirectory $out1 `
        -Modes @("STRUCTURE_CLEANUP") -MaxCalls 5

    Assert-Condition ($run1.ExitCode -eq 0) "success run exits zero"
    Assert-Condition ($run1.Output -notmatch [regex]::Escape($script:SecretPassword)) `
        "password is not printed"
    Assert-Condition ($run1.Output -notmatch [regex]::Escape($script:MockToken)) `
        "token is not printed"
    Assert-Condition ($run1.Output -notmatch "环境准备") "source text is not printed"
    Assert-Condition ($run1.Output -match "totalTokens") "summary prints total tokens"
    Assert-Condition ($server1.TransformCalls -eq 2) `
        "one transform call per pending sample"
    Assert-Condition ((@($server1.SeenSources | Select-Object -Unique)).Count -eq 2) `
        "transform sources are distinct"

    $results1 = Read-Results -OutputDirectory $out1
    Assert-Condition ($null -ne $results1) "success results.json exists"
    if ($null -ne $results1) {
        $successCases = @($results1.cases | Where-Object {
            $_.modeKey -eq "STRUCTURE_CLEANUP" -and $_.status -eq "success"
        })
        Assert-Condition ($successCases.Count -eq 2) "success records both samples"
        Assert-Condition ($results1.summary.totalCalls -eq 2) "success summary call count"
        if ($successCases.Count -gt 0) {
            $meta = $successCases[0].lastResult
            Assert-Condition ($null -ne $meta.requestId) "record requestId"
            Assert-Condition ($meta.modeVersion -eq 2) "record modeVersion"
            Assert-Condition ($meta.model -eq "mock-model") "record model"
            Assert-Condition ($meta.usage.totalTokens -eq `
                ($meta.usage.inputTokens + $meta.usage.outputTokens)) "record usage"
            Assert-Condition ($null -ne $meta.durationMs) "record duration"
            Assert-Condition ($null -ne $meta.inputChars) "record input length"
            Assert-Condition ($null -ne $meta.outputChars) "record output length"
            Assert-Condition (-not [string]::IsNullOrWhiteSpace($meta.output)) `
                "retain model output for review"
        }
    }
    $review1 = Join-Path $out1 "review.md"
    Assert-Condition (Test-Path -LiteralPath $review1) "success review.md exists"

    # Resume: second run skips the already-successful case.
    $server2 = New-TestServer -Scenario "success"
    $servers.Add($server2)
    $out2 = Join-Path $testRoot "resume"
    $run2a = Invoke-Evaluator -Server $server2 -OutputDirectory $out2 `
        -Modes @("STRUCTURE_CLEANUP") -MaxCalls 1
    $run2b = Invoke-Evaluator -Server $server2 -OutputDirectory $out2 `
        -Modes @("STRUCTURE_CLEANUP") -MaxCalls 1

    Assert-Condition ($run2a.ExitCode -eq 0) "resume first run exits zero"
    Assert-Condition ($run2b.ExitCode -eq 0) "resume second run exits zero"
    Assert-Condition ($server2.TransformCalls -eq 2) "resume skips completed case"
    Assert-Condition ((@($server2.SeenSources | Select-Object -Unique)).Count -eq 2) `
        "resume retries only the missing sample"
    $results2 = Read-Results -OutputDirectory $out2
    Assert-Condition ($null -ne $results2) "resume results.json exists"
    if ($null -ne $results2) {
        $resumeSuccesses = @($results2.cases | Where-Object {
            $_.modeKey -eq "STRUCTURE_CLEANUP" -and $_.status -eq "success"
        })
        Assert-Condition ($resumeSuccesses.Count -eq 2) "resume completes both samples"
    }

    # Partial persistence on a non-rate-limit failure.
    $server3 = New-TestServer -Scenario "failure" -FailOnCall 2
    $servers.Add($server3)
    $out3 = Join-Path $testRoot "failure"
    $run3 = Invoke-Evaluator -Server $server3 -OutputDirectory $out3 `
        -Modes @("STRUCTURE_CLEANUP") -MaxCalls 3

    Assert-Condition ($run3.ExitCode -ne 0) "failure run exits nonzero"
    Assert-Condition ($server3.TransformCalls -eq 2) "failure stops after failed request"
    $results3 = Read-Results -OutputDirectory $out3
    Assert-Condition ($null -ne $results3) "failure results.json exists"
    if ($null -ne $results3) {
        $first = @($results3.cases | Where-Object { $_.id -eq "structure-basic" })
        $second = @($results3.cases | Where-Object { $_.id -eq "structure-quote-table-link" })
        Assert-Condition ($first.Count -eq 1 -and $first[0].status -eq "success") `
            "failure preserves completed sample"
        Assert-Condition ($second.Count -eq 1 -and $second[0].status -eq "failed") `
            "failure records failed sample"
        Assert-Condition ($results3.summary.failed -ge 1) "failure summary records failure"
    }

    # 429 must stop immediately and not consume additional budget.
    $server4 = New-TestServer -Scenario "rate_limit" -RateLimitOnCall 1
    $servers.Add($server4)
    $out4 = Join-Path $testRoot "rate-limit"
    $run4 = Invoke-Evaluator -Server $server4 -OutputDirectory $out4 `
        -Modes @("STRUCTURE_CLEANUP") -MaxCalls 5

    Assert-Condition ($run4.ExitCode -ne 0) "rate-limit run exits nonzero"
    Assert-Condition ($server4.TransformCalls -eq 1) "rate-limit stops after first 429"
    $results4 = Read-Results -OutputDirectory $out4
    Assert-Condition ($null -ne $results4) "rate-limit results.json exists"
    if ($null -ne $results4) {
        Assert-Condition ($results4.summary.stoppedOnRateLimit -eq $true) `
            "rate-limit summary marks stop"
        $pending = @($results4.cases | Where-Object {
            $_.modeKey -eq "STRUCTURE_CLEANUP" -and $_.status -eq "pending"
        })
        Assert-Condition ($pending.Count -eq 1) "rate-limit leaves remaining sample pending"
    }
}
finally {
    foreach ($server in $servers) {
        try {
            $server.Dispose()
        }
        catch {
        }
    }
    Remove-Item -LiteralPath $testRoot -Recurse -Force -ErrorAction SilentlyContinue
}

if ($script:Failures.Count -gt 0) {
    Write-Host ""
    Write-Host "Failures: $($script:Failures.Count)"
    foreach ($failure in $script:Failures) {
        Write-Host "- $failure"
    }
    exit 1
}

Write-Host "All evaluate-ai-modes tests passed."
exit 0
