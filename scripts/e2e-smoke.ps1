param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "admin",
    [string]$Password = "ChangeMe123!"
)

$ErrorActionPreference = "Stop"

function Invoke-AmlJson {
    param([string]$Method, [string]$Path, $Body = $null)
    $parameters = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = @{ Authorization = "Bearer $script:Token" }
    }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = $Body | ConvertTo-Json -Depth 12 -Compress
    }
    Invoke-RestMethod @parameters
}

function Invoke-AmlUpload {
    param([string]$Path, [string]$FilePath)
    $raw = & curl.exe --silent --show-error --fail-with-body -X POST `
        -H "Authorization: Bearer $script:Token" `
        -F "file=@$FilePath" "$BaseUrl$Path"
    if ($LASTEXITCODE -ne 0) { throw "File upload failed: $Path" }
    $raw | ConvertFrom-Json
}

$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" -ContentType "application/json" `
    -Body (@{username=$Username; password=$Password} | ConvertTo-Json -Compress)
$script:Token = $login.token
if ([string]::IsNullOrWhiteSpace($script:Token)) { throw "Login response has no token" }

$suffix = (Get-Date).ToUniversalTime().ToString("yyyyMMddHHmmssfff")
$customerNo = "E2E-CUS-$suffix"
$accountNo = "E2E-ACC-$suffix"
$transactionNo = "E2E-TX-$suffix"
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) "java-aml-e2e-$suffix"
[IO.Directory]::CreateDirectory($tempRoot) | Out-Null

try {
    $customer = Invoke-AmlJson Post "/api/customers" @{
        customerNo=$customerNo; customerType="INDIVIDUAL"; fullName="Synthetic E2E Customer $suffix"
        idType="DEMO"; idNumberMasked="SYNTHETIC-$suffix"; countryCode="CN"; riskLevel="HIGH"
    }
    $account = Invoke-AmlJson Post "/api/accounts" @{
        accountNo=$accountNo; currency="CNY"; customerId=$customer.id
        relationType="OWNER"; ownershipPercent=100
    }

    $csvPath = Join-Path $tempRoot "transactions.csv"
    $csv = "externalId,accountNo,counterpartyAccount,direction,amount,currency,transactionTime,transactionType,description`r`n" +
        "$transactionNo,$accountNo,E2E-COUNTERPARTY,IN,120000.00,CNY,$((Get-Date).ToUniversalTime().AddMinutes(-1).ToString('o')),TRANSFER,SYNTHETIC_E2E"
    [IO.File]::WriteAllText($csvPath, $csv, [Text.UTF8Encoding]::new($false))
    $import = Invoke-AmlUpload "/api/transactions/import" $csvPath
    if ($import.imported -ne 1) { throw "Unexpected import count: $($import | ConvertTo-Json -Compress)" }
    $duplicate = Invoke-AmlUpload "/api/transactions/import" $csvPath
    if ($duplicate.duplicates -ne 1) { throw "Transaction idempotency check failed" }

    $transactions = Invoke-AmlJson Get "/api/transactions?customerId=$($customer.id)&size=10"
    $transaction = $transactions.content | Where-Object externalId -eq $transactionNo | Select-Object -First 1
    if ($null -eq $transaction) { throw "Imported transaction was not found" }

    $screening = Invoke-AmlJson Post "/api/screenings" @{
        customerId=$customer.id; name="Demo Synthetic Candidate"; countryCode="CN"; provider="MOCK"; schema="Person"
    }
    if ($screening.request.dataMode -ne "SIMULATED") { throw "Mock screening is not marked SIMULATED" }
    if ($screening.matches.Count -gt 0) {
        $null = Invoke-AmlJson Post "/api/screenings/$($screening.request.id)/matches/$($screening.matches[0].id)/review" @{
            decision="CONFIRMED_MATCH"; comment="Synthetic match confirmed by E2E test only"
        }
    }

    $ruleRun = Invoke-AmlJson Post "/api/rules/execute" @{
        ruleCodes=@("AML-R001"); asOf=(Get-Date).ToUniversalTime().AddMinutes(1).ToString("o")
    }
    if ($ruleRun.hits -lt 1) { throw "AML-R001 did not hit the new transaction" }
    $secondRuleRun = Invoke-AmlJson Post "/api/rules/execute" @{
        ruleCodes=@("AML-R001"); asOf=(Get-Date).ToUniversalTime().AddMinutes(1).ToString("o")
    }
    if ($secondRuleRun.hits -ne 0) { throw "Rule hit idempotency check failed" }

    $customerRisk = Invoke-AmlJson Post "/api/risk-scores/customers/$($customer.id)/calculate"
    $transactionRisk = Invoke-AmlJson Post "/api/risk-scores/transactions/$($transaction.id)/calculate"
    $alerts = Invoke-AmlJson Get "/api/alerts?keyword=$customerNo&size=10"
    $alert = $alerts.content | Select-Object -First 1
    if ($null -eq $alert) { throw "Generated alert was not found" }
    $null = Invoke-AmlJson Post "/api/alerts/$($alert.id)/transition" @{status="IN_REVIEW"; comment="E2E manual review"}
    $case = Invoke-AmlJson Post "/api/alerts/$($alert.id)/escalate" @{title="Synthetic E2E investigation $suffix"}

    $investigators = Invoke-AmlJson Get "/api/cases/investigators"
    $investigator = $investigators | Where-Object username -eq $Username | Select-Object -First 1
    if ($null -eq $investigator) { $investigator = $investigators | Select-Object -First 1 }
    if ($null -eq $investigator) { throw "No eligible investigator" }
    $null = Invoke-AmlJson Post "/api/cases/$($case.caseId)/assign" @{userId=$investigator.id}
    $null = Invoke-AmlJson Post "/api/cases/$($case.caseId)/transition" @{status="INVESTIGATING"; comment="E2E investigation started"}
    $null = Invoke-AmlJson Post "/api/cases/$($case.caseId)/notes" @{content="Synthetic end-to-end investigation note."}

    $evidencePath = Join-Path $tempRoot "synthetic-evidence.txt"
    [IO.File]::WriteAllText($evidencePath, "synthetic evidence $suffix", [Text.UTF8Encoding]::new($false))
    $evidence = Invoke-AmlUpload "/api/cases/$($case.caseId)/evidence" $evidencePath
    if ($evidence.evidence.Count -lt 1) { throw "Evidence record was not returned" }
    $null = Invoke-AmlJson Post "/api/cases/$($case.caseId)/transition" @{status="PENDING_REVIEW"; comment="E2E submitted for review"}
    $closed = Invoke-AmlJson Post "/api/cases/$($case.caseId)/transition" @{status="CLOSED"; comment="E2E review completed"}
    if ($closed.caseInfo.status -ne "CLOSED") { throw "Case was not closed" }

    $dashboard = Invoke-AmlJson Get "/api/dashboard"
    $audit = Invoke-AmlJson Get "/api/audit-logs?action=EXECUTE&size=10"
    $reportPath = Join-Path $tempRoot "alerts.csv"
    $reportStatus = & curl.exe --silent --show-error --output $reportPath --write-out "%{http_code}" `
        -H "Authorization: Bearer $script:Token" "$BaseUrl/api/reports/ALERTS/csv"
    $reportBytes = (Get-Item -LiteralPath $reportPath).Length
    if ($LASTEXITCODE -ne 0 -or $reportStatus -ne "200" -or $reportBytes -eq 0) { throw "CSV report download failed" }

    [pscustomobject]@{
        result="PASS"; dataMode="SYNTHETIC_AND_SIMULATED"; customerId=$customer.id
        accountId=$account.id; transactionId=$transaction.id; alertId=$alert.id; caseId=$case.caseId
        caseStatus=$closed.caseInfo.status; firstRuleHits=$ruleRun.hits; duplicateRuleHits=$secondRuleRun.hits
        customerRisk=$customerRisk.riskLevel; transactionRisk=$transactionRisk.riskLevel
        dashboardCustomers=$dashboard.summary.customers; auditExecuteRecords=$audit.total
        csvReportBytes=$reportBytes
    } | ConvertTo-Json -Depth 4
} finally {
    if (Test-Path -LiteralPath $tempRoot) {
        $resolved = [IO.Path]::GetFullPath($tempRoot)
        $expectedPrefix = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
        if (-not $resolved.StartsWith($expectedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to clean a non-temporary path: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
