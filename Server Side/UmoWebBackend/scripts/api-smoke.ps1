[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "admin",
    [string]$Password = "admin123"
)

$ErrorActionPreference = "Stop"
$script:BaseUrl = $BaseUrl.TrimEnd("/")
$script:StepCount = 0
$script:LastResponse = $null

function Invoke-Checked {
    param(
        [Parameter(Mandatory)]
        [string]$Method,
        [Parameter(Mandatory)]
        [string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{},
        [int]$ExpectedStatus = 200
    )

    $request = @{
        Method = $Method
        Uri = "$script:BaseUrl$Path"
        Headers = $Headers
        SkipHttpErrorCheck = $true
    }
    if ($PSBoundParameters.ContainsKey("Body")) {
        $request.ContentType = "application/json"
        $request.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }

    $response = Invoke-WebRequest @request
    if ($response.StatusCode -ne $ExpectedStatus) {
        throw "$Method $Path expected HTTP $ExpectedStatus but returned $($response.StatusCode): $($response.Content)"
    }
    $script:LastResponse = $response
    return $response
}

function Get-Json {
    param([object]$Response)

    if ([string]::IsNullOrWhiteSpace($Response.Content)) {
        return $null
    }
    return $Response.Content | ConvertFrom-Json
}

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw "Assertion failed: $Message"
    }
}

function Step {
    param(
        [int]$Number,
        [string]$Name
    )

    $script:StepCount++
    if ($script:StepCount -ne $Number) {
        throw "Smoke step ordering error: expected $Number but got $($script:StepCount)"
    }
    Write-Host ("[{0}/27] PASS {1}" -f $Number, $Name)
}

function Invoke-ImageUpload {
    param(
        [string]$Token,
        [string]$ImagePath
    )

    $responseFile = [IO.Path]::GetTempFileName()
    try {
        $statusText = & curl.exe -sS -o $responseFile -w "%{http_code}" `
            -H "Authorization: Bearer $Token" `
            -F "file=@$ImagePath;type=image/png" `
            "$script:BaseUrl/api/admin/images/upload"
        $status = [int]$statusText
        $content = Get-Content -LiteralPath $responseFile -Raw
        if ($status -ne 200) {
            throw "POST /api/admin/images/upload expected HTTP 200 but returned $status`: $content"
        }
        return $content | ConvertFrom-Json
    }
    finally {
        Remove-Item -LiteralPath $responseFile -Force -ErrorAction SilentlyContinue
    }
}

$runId = [DateTime]::UtcNow.ToString("yyyyMMddHHmmssfff")
$imagePath = Join-Path $env:TEMP "umoweb-smoke-$runId.png"
$pngBytes = [Convert]::FromBase64String(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9Z9h8AAAAASUVORK5CYII=")
[IO.File]::WriteAllBytes($imagePath, $pngBytes)

try {
    $site = Get-Json (Invoke-Checked -Method GET -Path "/api/public/site-info")
    Assert-True (-not [string]::IsNullOrWhiteSpace($site.siteTitle)) "site-info must contain siteTitle"
    Step 1 "GET /api/public/site-info"

    $about = Get-Json (Invoke-Checked -Method GET -Path "/api/public/pages/about")
    Assert-True ($null -ne $about.content) "about page must contain content"
    Step 2 "GET /api/public/pages/about"

    $project = Get-Json (Invoke-Checked -Method GET -Path "/api/public/pages/project")
    Assert-True ($null -ne $project.content) "project page must contain content"
    Step 3 "GET /api/public/pages/project"

    $publicCategories = Get-Json (Invoke-Checked -Method GET -Path "/api/public/categories?type=NOTE")
    Assert-True ($publicCategories.Count -gt 0) "public category tree must not be empty"
    Step 4 "GET /api/public/categories"

    $publicTags = Get-Json (Invoke-Checked -Method GET -Path "/api/public/tags")
    Assert-True ($publicTags.Count -gt 0) "public tag list must not be empty"
    Step 5 "GET /api/public/tags"

    $publicContents = Get-Json (Invoke-Checked -Method GET -Path "/api/public/contents?page=1&size=10")
    Assert-True ($publicContents.total -ge 5) "published content list must contain seed data"
    Assert-True (($publicContents.items | Where-Object type -eq "NOTE").Count -gt 0) "published content list must support NOTE items"
    Step 6 "GET /api/public/contents"

    $publicDetail = Get-Json (Invoke-Checked -Method GET -Path "/api/public/contents/spring-boot-quickstart")
    Assert-True (-not [string]::IsNullOrWhiteSpace($publicDetail.body)) "public detail must load Markdown body"
    Assert-True ($publicDetail.categories.Count -gt 0) "public detail must include categories"
    Assert-True ($publicDetail.tags.Count -gt 0) "public detail must include tags"
    Assert-True ($null -eq $publicDetail.previous) "oldest public detail must not have a previous article"
    Assert-True ($publicDetail.next.slug -eq "java-collections") "oldest public detail must point to the next published article"
    $middleDetail = Get-Json (Invoke-Checked -Method GET -Path "/api/public/contents/java-collections")
    Assert-True ($middleDetail.previous.slug -eq "spring-boot-quickstart") "middle detail previous must be older"
    Assert-True ($middleDetail.next.slug -eq "vue3-composition-api") "middle detail next must be newer"
    Step 7 "GET /api/public/contents/{slug}"

    $headers = @{ Authorization = "Bearer invalid" }
    Invoke-Checked -Method GET -Path "/api/admin/contents" -Headers $headers -ExpectedStatus 401 | Out-Null

    $search = Get-Json (Invoke-Checked -Method GET -Path "/api/public/contents/search?q=java&page=1&size=10")
    Assert-True ($search.total -ge 1) "search must match seeded Java content"
    $limited = Invoke-Checked -Method GET -Path "/api/public/contents/search?q=java&page=1&size=10" -ExpectedStatus 429
    Assert-True ((Get-Json $limited).code -eq 429) "rate-limited search must return error code 429"
    Step 8 "GET /api/public/contents/search"

    $loginBody = @{ username = $Username; password = $Password }
    $login = Get-Json (Invoke-Checked -Method POST -Path "/api/admin/login" -Body $loginBody)
    Assert-True (-not [string]::IsNullOrWhiteSpace($login.token)) "login must return token"
    $token = $login.token
    Step 9 "POST /api/admin/login"

    $newPassword = "SmokePass$runId"
    $authHeaders = @{ Authorization = "Bearer $token" }
    Invoke-Checked -Method PUT -Path "/api/admin/change-password" -Headers $authHeaders `
        -Body @{ oldPassword = $Password; newPassword = $newPassword } -ExpectedStatus 204 | Out-Null
    Invoke-Checked -Method GET -Path "/api/admin/contents" -Headers $authHeaders -ExpectedStatus 401 | Out-Null

    $newLogin = Get-Json (Invoke-Checked -Method POST -Path "/api/admin/login" `
            -Body @{ username = $Username; password = $newPassword })
    $newToken = $newLogin.token
    $newAuthHeaders = @{ Authorization = "Bearer $newToken" }
    Invoke-Checked -Method PUT -Path "/api/admin/change-password" -Headers $newAuthHeaders `
        -Body @{ oldPassword = $newPassword; newPassword = $Password } -ExpectedStatus 204 | Out-Null

    $restoredLogin = Get-Json (Invoke-Checked -Method POST -Path "/api/admin/login" -Body $loginBody)
    $token = $restoredLogin.token
    $authHeaders = @{ Authorization = "Bearer $token" }
    Step 10 "PUT /api/admin/change-password"

    $adminCategories = Get-Json (Invoke-Checked -Method GET -Path "/api/admin/categories" -Headers $authHeaders)
    Assert-True ($adminCategories.Count -gt 0) "admin category tree must not be empty"
    Step 11 "GET /api/admin/categories"

    $categorySlug = "smoke-category-$runId"
    $category = Get-Json (Invoke-Checked -Method POST -Path "/api/admin/categories" -Headers $authHeaders `
            -Body @{
                name = "Smoke Category $runId"
                slug = $categorySlug
                type = "NOTE"
                sortOrder = 99
            })
    Assert-True ($category.id -gt 0) "created category must have id"
    $categoryId = $category.id
    Step 12 "POST /api/admin/categories"

    $categoryDetail = Get-Json (Invoke-Checked -Method GET -Path "/api/admin/categories/$categoryId" -Headers $authHeaders)
    Assert-True ($categoryDetail.slug -eq $categorySlug) "category detail must match created slug"
    Step 13 "GET /api/admin/categories/{id}"

    $updatedCategorySlug = "$categorySlug-updated"
    $categoryUpdate = Get-Json (Invoke-Checked -Method PUT -Path "/api/admin/categories/$categoryId" -Headers $authHeaders `
            -Body @{
                name = "Smoke Category Updated"
                slug = $updatedCategorySlug
                type = "NOTE"
                sortOrder = 100
            })
    Assert-True ($categoryUpdate.slug -eq $updatedCategorySlug) "category update must change slug"
    Step 14 "PUT /api/admin/categories/{id}"

    $adminTags = Get-Json (Invoke-Checked -Method GET -Path "/api/admin/tags" -Headers $authHeaders)
    Assert-True ($adminTags.Count -gt 0) "admin tag list must not be empty"
    Step 15 "GET /api/admin/tags"

    $tagSlug = "smoke-tag-$runId"
    $tag = Get-Json (Invoke-Checked -Method POST -Path "/api/admin/tags" -Headers $authHeaders `
            -Body @{ name = "Smoke Tag $runId"; slug = $tagSlug })
    Assert-True ($tag.id -gt 0) "created tag must have id"
    $tagId = $tag.id
    Step 16 "POST /api/admin/tags"

    $updatedTagSlug = "$tagSlug-updated"
    $tagUpdate = Get-Json (Invoke-Checked -Method PUT -Path "/api/admin/tags/$tagId" -Headers $authHeaders `
            -Body @{ name = "Smoke Tag Updated"; slug = $updatedTagSlug })
    Assert-True ($tagUpdate.slug -eq $updatedTagSlug) "tag update must change slug"
    Step 17 "PUT /api/admin/tags/{id}"

    $adminContents = Get-Json (Invoke-Checked -Method GET -Path "/api/admin/contents?page=1&size=100" -Headers $authHeaders)
    Assert-True ($adminContents.total -ge 6) "admin content list must include drafts and published content"
    Step 18 "GET /api/admin/contents"

    $contentSlug = "smoke-content-$runId"
    $content = Get-Json (Invoke-Checked -Method POST -Path "/api/admin/contents" -Headers $authHeaders `
            -Body @{
                title = "Smoke Content $runId"
                slug = $contentSlug
                body = "# Smoke`n`nInitial body"
                summary = "Smoke summary"
                type = "NOTE"
                status = "DRAFT"
                categoryIds = @($categoryId)
                tagIds = @($tagId)
                metadata = '{"source":"api-smoke"}'
            })
    Assert-True ($content.id -gt 0) "created content must have id"
    $contentId = $content.id
    Step 19 "POST /api/admin/contents"

    $contentDetail = Get-Json (Invoke-Checked -Method GET -Path "/api/admin/contents/$contentId" -Headers $authHeaders)
    Assert-True ($contentDetail.body -eq "# Smoke`n`nInitial body") "admin content detail must load body"
    Assert-True ($contentDetail.categories[0].id -eq $categoryId) "content detail must include created category"
    Step 20 "GET /api/admin/contents/{id}"

    $contentUpdate = Get-Json (Invoke-Checked -Method PUT -Path "/api/admin/contents/$contentId" -Headers $authHeaders `
            -Body @{
                title = "Smoke Content Updated"
                slug = $contentSlug
                body = "# Smoke`n`nUpdated body"
                summary = "Smoke summary updated"
                type = "NOTE"
                status = "DRAFT"
                categoryIds = @($categoryId)
                tagIds = @($tagId)
                metadata = '{"source":"api-smoke","updated":true}'
            })
    Assert-True ($contentUpdate.body -eq "# Smoke`n`nUpdated body") "content update must persist body"
    Step 21 "PUT /api/admin/contents/{id}"

    Invoke-Checked -Method DELETE -Path "/api/admin/contents/$contentId" -Headers $authHeaders -ExpectedStatus 204 | Out-Null
    Invoke-Checked -Method GET -Path "/api/admin/contents/$contentId" -Headers $authHeaders -ExpectedStatus 404 | Out-Null
    Step 22 "DELETE /api/admin/contents/{id}"

    Invoke-Checked -Method DELETE -Path "/api/admin/tags/$tagId" -Headers $authHeaders -ExpectedStatus 204 | Out-Null
    Step 23 "DELETE /api/admin/tags/{id}"

    Invoke-Checked -Method DELETE -Path "/api/admin/categories/$categoryId" -Headers $authHeaders -ExpectedStatus 204 | Out-Null
    Step 24 "DELETE /api/admin/categories/{id}"

    $image = Invoke-ImageUpload -Token $token -ImagePath $imagePath
    Assert-True ($image.id -gt 0) "uploaded image must have id"
    Assert-True ($image.url -match "^/images/\d{4}/\d{2}/[0-9a-f-]+\.png$") "uploaded image URL must use the configured storage path"
    Step 25 "POST /api/admin/images/upload"

    $options = Get-Json (Invoke-Checked -Method GET -Path "/api/admin/options" -Headers $authHeaders)
    $originalTitle = $options.site_title
    Assert-True (-not [string]::IsNullOrWhiteSpace($originalTitle)) "site options must contain site_title"
    Step 26 "GET /api/admin/options"

    Invoke-Checked -Method PUT -Path "/api/admin/options/site_title" -Headers $authHeaders `
        -Body @{ value = "Umo Smoke Title" } -ExpectedStatus 204 | Out-Null
    $updatedOptions = Get-Json (Invoke-Checked -Method GET -Path "/api/admin/options" -Headers $authHeaders)
    Assert-True ($updatedOptions.site_title -eq "Umo Smoke Title") "site_title update must persist"
    Invoke-Checked -Method PUT -Path "/api/admin/options/site_title" -Headers $authHeaders `
        -Body @{ value = $originalTitle } -ExpectedStatus 204 | Out-Null
    Step 27 "PUT /api/admin/options/{key}"

    if ($script:StepCount -ne 27) {
        throw "Expected 27 endpoints but covered $script:StepCount"
    }
    Write-Host "API smoke passed: 27/27 endpoints, authentication guard, password invalidation, and search rate limit."
}
finally {
    Remove-Item -LiteralPath $imagePath -Force -ErrorAction SilentlyContinue
}
