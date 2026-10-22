
# Stop immediately if a request or assertion fails.
$ErrorActionPreference = "Stop"

# Default addresses for the four running services.
$ingestionUrl = "http://localhost:7050"
$hubUrl = "http://localhost:7051"
$delayUrl = "http://localhost:7052"
$transitUrl = "http://localhost:7053"

# Step 1: Confirm that Ingestion is running.
Write-Host "1. Checking Ingestion..."
$ingestionHealth = Invoke-RestMethod "$ingestionUrl/health"
if ($null -eq $ingestionHealth) {
    throw "Ingestion health check failed."
}

# Step 2: Confirm that Hub is running and has loaded its hub data.
Write-Host "2. Checking Hub Service..."
$hubHealth = Invoke-RestMethod "$hubUrl/health"
if ($hubHealth.status -ne "UP") {
    throw "Hub Service is not UP."
}

$hubs = Invoke-RestMethod "$hubUrl/hubs"
if ($hubs.Count -lt 1) {
    throw "Hub Service returned no hubs."
}

# Check that hub IDs are matched without regard to case.
# H-500 is present in the supplied project dataset.
$hub = Invoke-RestMethod "$hubUrl/hubs/h-500"
if ($hub.hubId -ne "H-500") {
    throw "Case-insensitive hub lookup failed."
}

# Step 3: Confirm that Delay Stage is running.
Write-Host "3. Checking Delay Stage Service..."
$delayHealth = Invoke-RestMethod "$delayUrl/health"
if ($delayHealth.status -ne "UP") {
    throw "Delay Stage Service is not UP."
}

# A known hub with no saved stage should start at stage zero.
$initialStage = Invoke-RestMethod "$delayUrl/delay-stage/H-500"
if ($initialStage.stage -ne 0) {
    throw "Expected initial stage 0, received $($initialStage.stage)."
}

# Update the stage to three using the POST endpoint.
$body = @{ stage = 3 } | ConvertTo-Json
$updatedStage = Invoke-RestMethod `
    -Uri "$delayUrl/delay-stage/H-500" `
    -Method Post `
    -ContentType "application/json" `
    -Body $body

if ($updatedStage.stage -ne 3) {
    throw "Delay stage update failed."
}

# Step 4: Confirm that Transit is running.
Write-Host "4. Checking Transit Service..."
$transitHealth = Invoke-RestMethod "$transitUrl/health"
if ($transitHealth.status -ne "UP") {
    throw "Transit Service is not UP."
}

# Transit should read stage three and calculate 120 + (3 * 15) = 165 minutes.
$eta = Invoke-RestMethod "$transitUrl/transit/H-500"

if ($eta.delayStage -ne 3) {
    throw "Expected delay stage 3, received $($eta.delayStage)."
}

if ($eta.etaMinutes -ne 165) {
    throw "Expected ETA 165 minutes, received $($eta.etaMinutes)."
}

# Check that the duration is returned in ISO-8601 format.
if ($eta.etaDuration -ne "PT2H45M") {
    throw "Expected PT2H45M, received $($eta.etaDuration)."
}

# The response should also include an estimated UTC arrival timestamp.
if ([string]::IsNullOrWhiteSpace($eta.etaAt)) {
    throw "ETA response did not include etaAt."
}

# Reaching this point means every check above succeeded.
Write-Host ""
Write-Host "PASS: Iteration 2 REST flow works." -ForegroundColor Green
Write-Host "Hub: $($eta.hubId)"
Write-Host "Stage: $($eta.delayStage)"
Write-Host "ETA: $($eta.etaMinutes) minutes ($($eta.etaDuration))"
Write-Host "ETA timestamp (UTC): $($eta.etaAt)"
