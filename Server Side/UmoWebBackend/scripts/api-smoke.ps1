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
$originalPassword = $Password
$newPassword = $null
$passwordChangePending = $false

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
    Write-Host ("[{0}/29] PASS {1}" -f $Number, $Name)
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

function Remove-TestResource {
    param(
        [string]$Path,
        [hashtable]$Headers
    )

    if ([string]::IsNullOrWhiteSpace($Path) -or
        $Path -match '/$' -or
        $null -eq $Headers -or
        $Headers.Count -eq 0) {
        return
    }

    try {
        $response = Invoke-WebRequest -Method DELETE -Uri "$script:BaseUrl$Path" `
            -Headers $Headers -SkipHttpErrorCheck
        if ($response.StatusCode -notin @(204, 404)) {
            Write-Warning "Cleanup DELETE $Path returned HTTP $($response.StatusCode)"
        }
    }
    catch {
        Write-Warning "Cleanup DELETE $Path failed: $($_.Exception.Message)"
    }
}

$runId = [DateTime]::UtcNow.ToString("yyyyMMddHHmmssfff")
$imagePath = Join-Path $env:TEMP "umoweb-smoke-$runId.png"
$pngBytes = [Convert]::FromBase64String(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9Z9h8AAAAASUVORK5CYII=")
[IO.File]::WriteAllBytes($imagePath, $pngBytes)

$token = $null
$authHeaders = @{}
$categoryId = $null
$childCategoryId = $null
$tagId = $null
$contentId = $null
$previousContentId = $null
$referenceContentId = $null
$imageId = $null

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
    Assert-True ($null -ne $publicCategories) "public category response must be an array"
    Step 4 "GET /api/public/categories"

    $publicTags = Get-Json (Invoke-Checked -Method GET -Path "/api/public/tags")
    Assert-True ($null -ne $publicTags) "public tag response must be an array"
    Step 5 "GET /api/public/tags"

    $publicContents = Get-Json (Invoke-Checked -Method GET -Path "/api/public/contents?page=1&size=10")
    Assert-True ($null -ne $publicContents.items) "public content page must contain items"
    Assert-True (($publicContents.items | Where-Object status -ne "PUBLISHED").Count -eq 0) "public content list must only expose PUBLISHED status"
    Step 6 "GET /api/public/contents"

    $invalidHeaders = @{ Authorization = "Bearer invalid" }
    Invoke-Checked -Method GET -Path "/api/admin/contents" -Headers $invalidHeaders -ExpectedStatus 401 | Out-Null

    $loginBody = @{ username = $Username; password = $Password }
    $login = Get-Json (Invoke-Checked -Method POST -Path "/api/admin/login" -Body $loginBody)
    Assert-True (-not [string]::IsNullOrWhiteSpace($login.token)) "login must return token"
    $token = $login.token
    $authHeaders = @{ Authorization = "Bearer $token" }
    Step 7 "POST /api/admin/login"

    $newPassword = "SmokePass$runId"
    $passwordChangePending = $true
    Invoke-Checked -Method PUT -Path "/api/admin/change-password" -Headers $authHeaders `
        -Body @{ oldPassword = $Password; newPassword = $newPassword } -ExpectedStatus 204 | Out-Null
    Invoke-Checked -Method GET -Path "/api/admin/contents" -Headers $authHeaders -ExpectedStatus 401 | Out-Null

    $newLogin = Get-Json (Invoke-Checked -Method POST -Path "/api/admin/login" `
            -Body @{ username = $Username; password = $newPassword })
    $newToken = $newLogin.token
    $newAuthHeaders = @{ Authorization = "Bearer $newToken" }
    Invoke-Checked -Method PUT -Path "/api/admin/change-password" -Headers $newAuthHeaders `
        -Body @{ oldPassword = $newPassword; newPassword = $Password } -ExpectedStatus 204 | Out-Null
    $passwordChangePending = $false

    $restoredLogin = Get-Json (Invoke-Checked -Method POST -Path "/api/admin/login" -Body $loginBody)
    $token = $restoredLogin.token
    $authHeaders = @{ Authorization = "Bearer $token" }
    Step 8 "PUT /api/admin/change-password"

    $adminCategories = Get-Json (Invoke-Checked -Method GET -Path "/api/admin/categories" -Headers $authHeaders)
    Assert-True ($null -ne $adminCategories) "admin category response must be an array"
    Step 9 "GET /api/admin/categories"

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
    Step 10 "POST /api/admin/categories"

    $categoryDetail = Get-Json (Invoke-Checked -Method GET -Path "/api/admin/categories/$categoryId" -Headers $authHeaders)
    Assert-True ($categoryDetail.slug -eq $categorySlug) "category detail must match created slug"
    Step 11 "GET /api/admin/categories/{id}"

    $updatedCategorySlug = "$categorySlug-updated"
    $categoryUpdate = Get-Json (Invoke-Checked -Method PUT -Path "/api/admin/categories/$categoryId" -Headers $authHeaders `
            -Body @{
                name = "Smoke Category Updated"
                slug = $updatedCategorySlug
                type = "NOTE"
                sortOrder = 100
            })
    Assert-True ($categoryUpdate.slug -eq $updatedCategorySlug) "category update must change slug"
    Step 12 "PUT /api/admin/categories/{id}"

    $childCategorySlug = "smoke-child-category-$runId"
    $childCategory = Get-Json (Invoke-Checked -Method POST -Path "/api/admin/categories" -Headers $authHeaders `
            -Body @{
                name = "Smoke Child Category $runId"
                slug = $childCategorySlug
                parentId = $categoryId
                type = "NOTE"
                sortOrder = 101
            })
    Assert-True ($childCategory.id -gt 0) "created child category must have id"
    $childCategoryId = $childCategory.id

    $adminTags = Get-Json (Invoke-Checked -Method GET -Path "/api/admin/tags" -Headers $authHeaders)
    Assert-True ($null -ne $adminTags) "admin tag response must be an array"
    Step 13 "GET /api/admin/tags"

    $tagSlug = "smoke-tag-$runId"
    $tag = Get-Json (Invoke-Checked -Method POST -Path "/api/admin/tags" -Headers $authHeaders `
            -Body @{ name = "Smoke Tag $runId"; slug = $tagSlug })
    Assert-True ($tag.id -gt 0) "created tag must have id"
    $tagId = $tag.id
    Step 14 "POST /api/admin/tags"

    $updatedTagSlug = "$tagSlug-updated"
    $tagUpdate = Get-Json (Invoke-Checked -Method PUT -Path "/api/admin/tags/$tagId" -Headers $authHeaders `
            -Body @{ name = "Smoke Tag Updated"; slug = $updatedTagSlug })
    Assert-True ($tagUpdate.slug -eq $updatedTagSlug) "tag update must change slug"
    Step 15 "PUT /api/admin/tags/{id}"

    $previousContentSlug = "smoke-previous-$runId"
    $previousContent = Get-Json (Invoke-Checked -Method POST -Path "/api/admin/contents" -Headers $authHeaders `
            -Body @{
                title = "Smoke Previous $runId"
                slug = $previousContentSlug
                body = "# Smoke Previous`n`nEarlier body"
                summary = "Smoke previous $runId"
                type = "NOTE"
                status = "DRAFT"
                categoryIds = @($categoryId)
                tagIds = @($tagId)
                metadata = '{"source":"api-smoke","order":"previous"}'
            })
    Assert-True ($previousContent.id -gt 0) "created previous content must have id"
    $previousContentId = $previousContent.id
    Invoke-Checked -Method PUT -Path "/api/admin/contents/$previousContentId" -Headers $authHeaders `
        -Body @{
            title = "Smoke Previous $runId"
            slug = $previousContentSlug
            body = "# Smoke Previous`n`nEarlier body"
            summary = "Smoke previous $runId"
            type = "NOTE"
            status = "PUBLISHED"
            categoryIds = @($categoryId)
            tagIds = @($tagId)
            metadata = '{"source":"api-smoke","order":"previous"}'
        } | Out-Null

    $adminContents = Get-Json (Invoke-Checked -Method GET -Path "/api/admin/contents?page=1&size=100" -Headers $authHeaders)
    Assert-True ($null -ne $adminContents.items) "admin content page must contain items"
    Step 16 "GET /api/admin/contents"

    $contentSlug = "smoke-content-$runId"
    $content = Get-Json (Invoke-Checked -Method POST -Path "/api/admin/contents" -Headers $authHeaders `
            -Body @{
                title = "Smoke Content $runId"
                slug = $contentSlug
                body = "# Smoke`n`nInitial body"
                summary = "Smoke search token $runId"
                type = "NOTE"
                status = "DRAFT"
                categoryIds = @($childCategoryId)
                tagIds = @($tagId)
                metadata = '{"source":"api-smoke"}'
            })
    Assert-True ($content.id -gt 0) "created content must have id"
    Assert-True ($content.status -eq "DRAFT") "created content must expose DRAFT status"
    $contentId = $content.id
    Step 17 "POST /api/admin/contents"

    Invoke-Checked -Method GET -Path "/api/public/contents/$contentSlug" -ExpectedStatus 404 | Out-Null

    $contentDetail = Get-Json (Invoke-Checked -Method GET -Path "/api/admin/contents/$contentId" -Headers $authHeaders)
    Assert-True ($contentDetail.body -eq "# Smoke`n`nInitial body") "admin content detail must load body"
    Assert-True ($contentDetail.status -eq "DRAFT") "admin content detail must expose DRAFT status"
    Assert-True ($contentDetail.categories[0].id -eq $childCategoryId) "content detail must include created child category"
    Assert-True ($contentDetail.tags[0].id -eq $tagId) "admin content detail must include created tag"
    Step 18 "GET /api/admin/contents/{id}"

    $contentUpdate = Get-Json (Invoke-Checked -Method PUT -Path "/api/admin/contents/$contentId" -Headers $authHeaders `
            -Body @{
                title = "Smoke Content Updated"
                slug = $contentSlug
                body = "# Smoke`n`nUpdated body"
                summary = "Smoke search token $runId"
                type = "NOTE"
                status = "PUBLISHED"
                categoryIds = @($childCategoryId)
                tagIds = @($tagId)
                metadata = '{"source":"api-smoke","updated":true}'
            })
    Assert-True ($contentUpdate.body -eq "# Smoke`n`nUpdated body") "content update must persist body"
    Assert-True ($contentUpdate.status -eq "PUBLISHED") "content update must expose PUBLISHED status"
    Step 19 "PUT /api/admin/contents/{id}"

    $publishedContents = Get-Json (Invoke-Checked -Method GET -Path "/api/public/contents?page=1&size=100")
    Assert-True (($publishedContents.items | Where-Object slug -eq $contentSlug).Count -eq 1) "published content list must contain the smoke content"
    Assert-True (($publishedContents.items | Where-Object status -ne "PUBLISHED").Count -eq 0) "public content list must only expose PUBLISHED status"

    $publicDetail = Get-Json (Invoke-Checked -Method GET -Path "/api/public/contents/$contentSlug")
    Assert-True ($publicDetail.slug -eq $contentSlug) "public detail must return the published smoke content"
    Assert-True ($publicDetail.body -eq "# Smoke`n`nUpdated body") "public detail must load Markdown body"
    Assert-True ($publicDetail.categories[0].id -eq $childCategoryId) "public detail must include the smoke child category"
    Assert-True ($publicDetail.tags[0].id -eq $tagId) "public detail must include the smoke tag"
    Assert-True ($publicDetail.previous.slug -eq $previousContentSlug) "public detail must include the previous smoke content"
    Assert-True ($null -eq $publicDetail.next) "public detail must have no next content at the latest boundary"

    $noteContents = Get-Json (Invoke-Checked -Method GET -Path "/api/public/contents?type=NOTE&page=1&size=100")
    Assert-True (($noteContents.items | Where-Object type -ne "NOTE").Count -eq 0) "public type filter must only return NOTE content"
    $categoryContents = Get-Json (Invoke-Checked -Method GET -Path "/api/public/contents?categoryId=$categoryId&page=1&size=100")
    $expectedSlugs = @($contentSlug, $previousContentSlug)
    Assert-True ($categoryContents.items.Count -eq 1 -and $categoryContents.items[0].slug -eq $previousContentSlug) `
        "exact parent category filter must return only parent content"
    $descendantCategoryContents = Get-Json (Invoke-Checked -Method GET `
            -Path "/api/public/contents?categoryId=$categoryId&includeDescendants=true&page=1&size=100")
    Assert-True ($descendantCategoryContents.items.Count -eq 2) `
        "descendant category filter must return parent and child content"
    Assert-True (($descendantCategoryContents.items | Where-Object { $_.slug -notin $expectedSlugs }).Count -eq 0) `
        "descendant category filter must only return smoke content"
    $adminDescendantContents = Get-Json (Invoke-Checked -Method GET -Headers $authHeaders `
            -Path "/api/admin/contents?categoryId=$categoryId&includeDescendants=true&page=1&size=100")
    Assert-True ($adminDescendantContents.items.Count -eq 2) `
        "admin descendant category filter must return parent and child content"
    $tagContents = Get-Json (Invoke-Checked -Method GET -Path "/api/public/contents?tagId=$tagId&page=1&size=100")
    Assert-True (($tagContents.items | Where-Object { $_.slug -notin $expectedSlugs }).Count -eq 0) "public tag filter must only return smoke content"
    $previousDetail = Get-Json (Invoke-Checked -Method GET -Path "/api/public/contents/$previousContentSlug")
    Assert-True ($previousDetail.next.slug -eq $contentSlug) "previous smoke content must return the current content as next"
    Step 20 "GET /api/public/contents/{slug}"

    $search = Get-Json (Invoke-Checked -Method GET -Path "/api/public/contents/search?q=$runId&page=1&size=10")
    Assert-True (($search.items | Where-Object slug -eq $contentSlug).Count -eq 1) "search must match the smoke content"
    $limited = Invoke-Checked -Method GET -Path "/api/public/contents/search?q=$runId&page=1&size=10" -ExpectedStatus 429
    Assert-True ((Get-Json $limited).code -eq 429) "rate-limited search must return error code 429"
    Step 21 "GET /api/public/contents/search"

    Invoke-Checked -Method DELETE -Path "/api/admin/contents/$contentId" -Headers $authHeaders -ExpectedStatus 204 | Out-Null
    Invoke-Checked -Method GET -Path "/api/admin/contents/$contentId" -Headers $authHeaders -ExpectedStatus 404 | Out-Null
    $contentId = $null
    Invoke-Checked -Method DELETE -Path "/api/admin/contents/$previousContentId" -Headers $authHeaders -ExpectedStatus 204 | Out-Null
    Invoke-Checked -Method GET -Path "/api/admin/contents/$previousContentId" -Headers $authHeaders -ExpectedStatus 404 | Out-Null
    $previousContentId = $null
    Step 22 "DELETE /api/admin/contents/{id}"

    Invoke-Checked -Method DELETE -Path "/api/admin/tags/$tagId" -Headers $authHeaders -ExpectedStatus 204 | Out-Null
    $tagId = $null
    Step 23 "DELETE /api/admin/tags/{id}"

    Invoke-Checked -Method DELETE -Path "/api/admin/categories/$childCategoryId" -Headers $authHeaders -ExpectedStatus 204 | Out-Null
    $childCategoryId = $null
    Invoke-Checked -Method DELETE -Path "/api/admin/categories/$categoryId" -Headers $authHeaders -ExpectedStatus 204 | Out-Null
    $categoryId = $null
    Step 24 "DELETE /api/admin/categories/{id}"

    $image = Invoke-ImageUpload -Token $token -ImagePath $imagePath
    Assert-True ($image.id -gt 0) "uploaded image must have id"
    Assert-True ($image.url -match "^/images/\d{4}/\d{2}/[0-9a-f-]+\.png$") "uploaded image URL must use the configured storage path"
    $imageId = $image.id
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

    $orphanImages = Get-Json (Invoke-Checked -Method GET -Headers $authHeaders `
            -Path "/api/admin/images?usage=ORPHANED&page=1&size=100")
    Assert-True (($orphanImages.items | Where-Object id -eq $imageId).Count -eq 1) `
        "uploaded image must appear as an orphan"
    Step 28 "GET /api/admin/images"

    $referenceContent = Get-Json (Invoke-Checked -Method POST -Path "/api/admin/contents" `
            -Headers $authHeaders -Body @{
                title = "Smoke Image Reference $runId"
                slug = "smoke-image-reference-$runId"
                body = "![smoke]($($image.url))"
                summary = "Temporary image reference"
                type = "NOTE"
                status = "DRAFT"
                categoryIds = @()
                tagIds = @()
            })
    $referenceContentId = $referenceContent.id
    Assert-True ($referenceContentId -gt 0) "reference content must have id"

    $conflict = Invoke-Checked -Method DELETE -Path "/api/admin/images/$imageId" `
        -Headers $authHeaders -ExpectedStatus 409
    Assert-True ((Get-Json $conflict).code -eq 409) "referenced image deletion must return 409"

    Invoke-Checked -Method DELETE -Path "/api/admin/contents/$referenceContentId" `
        -Headers $authHeaders -ExpectedStatus 204 | Out-Null
    $referenceContentId = $null
    Invoke-Checked -Method DELETE -Path "/api/admin/images/$imageId" `
        -Headers $authHeaders -ExpectedStatus 204 | Out-Null
    $imageId = $null
    Invoke-Checked -Method GET -Path $image.url -ExpectedStatus 404 | Out-Null
    $remainingImages = Get-Json (Invoke-Checked -Method GET -Headers $authHeaders `
            -Path "/api/admin/images?usage=ORPHANED&page=1&size=100")
    Assert-True (($remainingImages.items | Where-Object id -eq $image.id).Count -eq 0) `
        "deleted image must leave the list"
    Step 29 "DELETE /api/admin/images/{id}"

    if ($script:StepCount -ne 29) {
        throw "Expected 29 endpoints but covered $($script:StepCount)"
    }
    Write-Host "API smoke passed: 29/29 endpoints, authentication guard, draft isolation, public filters, content associations, previous/next navigation, password invalidation, image lifecycle, and search rate limit."
}
finally {
    if ($passwordChangePending) {
        try {
            $recoveryLogin = Get-Json (Invoke-Checked -Method POST -Path "/api/admin/login" `
                    -Body @{ username = $Username; password = $newPassword })
            $recoveryToken = $recoveryLogin.token
            Invoke-Checked -Method PUT -Path "/api/admin/change-password" `
                -Headers @{ Authorization = "Bearer $recoveryToken" } `
                -Body @{ oldPassword = $newPassword; newPassword = $originalPassword } `
                -ExpectedStatus 204 | Out-Null
        }
        catch {
            Write-Warning "Administrator password recovery request failed: $($_.Exception.Message)"
        }

        try {
            $restoredLogin = Get-Json (Invoke-Checked -Method POST -Path "/api/admin/login" `
                    -Body @{ username = $Username; password = $originalPassword })
            $token = $restoredLogin.token
            $authHeaders = @{ Authorization = "Bearer $token" }
            $passwordChangePending = $false
        }
        catch {
            $token = $null
            $authHeaders = @{}
            Write-Warning "Original administrator password could not be confirmed after failure."
        }
    }

    Remove-TestResource -Path "/api/admin/contents/$contentId" -Headers $authHeaders
    Remove-TestResource -Path "/api/admin/contents/$previousContentId" -Headers $authHeaders
    Remove-TestResource -Path "/api/admin/tags/$tagId" -Headers $authHeaders
    Remove-TestResource -Path "/api/admin/categories/$childCategoryId" -Headers $authHeaders
    Remove-TestResource -Path "/api/admin/categories/$categoryId" -Headers $authHeaders
    Remove-TestResource -Path "/api/admin/contents/$referenceContentId" -Headers $authHeaders
    Remove-TestResource -Path "/api/admin/images/$imageId" -Headers $authHeaders
    Remove-Item -LiteralPath $imagePath -Force -ErrorAction SilentlyContinue
}
