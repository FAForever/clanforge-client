# Tests LoginRateLimitFilter by sending repeated failed logins to localhost:8080/login,
# handling the session + CSRF token that Spring Security's default login page requires.
# Expects the app to be running locally (mvnw spring-boot:run) first.

$session = $null
Invoke-WebRequest -Uri "http://localhost:8080/login" -SessionVariable session -UseBasicParsing | Out-Null

for ($i = 1; $i -le 7; $i++) {
    $loginPage = Invoke-WebRequest -Uri "http://localhost:8080/login" -WebSession $session -UseBasicParsing
    $token = $null
    if ($loginPage.Content -match 'name="_csrf"[^>]*value="([^"]+)"') {
        $token = $matches[1]
    }

    $body = @{ username = "admin"; password = "wrongpass"; _csrf = $token }

    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/login" -Method POST -Body $body -WebSession $session -UseBasicParsing -MaximumRedirection 0 -ErrorAction Stop
        Write-Host "Attempt $i : HTTP $($response.StatusCode)"
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        Write-Host "Attempt $i : HTTP $status"
    }
}
