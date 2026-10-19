param(
    [string]$UserPoolId = "us-east-1_AOH2j7aTl",
    [string[]]$AppClientIds = @(
        "7vge2gthmumgohfn84aepg41qh",
        "2ndve0vjmlau9as43sapcmermb"
    ),
    [string]$ResourceServerIdentifier = "skate-api"
)

$ErrorActionPreference = "Stop"
$aws = Get-Command aws -ErrorAction SilentlyContinue
if (-not $aws) {
    throw "AWS CLI no está en PATH. Instálalo y configura localmente la región y las credenciales."
}

function Invoke-AwsJson {
    param([string[]]$Arguments)

    $output = & $aws.Source @Arguments --output json
    if ($LASTEXITCODE -ne 0) {
        throw "Falló AWS CLI: aws $($Arguments -join ' ')"
    }
    return ($output | ConvertFrom-Json)
}

$resourceServers = Invoke-AwsJson -Arguments @(
    "cognito-idp", "list-resource-servers",
    "--user-pool-id", $UserPoolId,
    "--max-results", "50"
)
$resourceServer = $resourceServers.ResourceServers |
    Where-Object { $_.Identifier -eq $ResourceServerIdentifier } |
    Select-Object -First 1

$scopes = @()
if ($resourceServer) {
    $scopes = @($resourceServer.Scopes | Where-Object {
        $_.ScopeName -notin @("read", "write", "purchase")
    })
}
$scopes += @(
    @{ ScopeName = "read"; ScopeDescription = "Read the skate catalog and inventory" },
    @{ ScopeName = "write"; ScopeDescription = "Create, update, or delete catalog and messaging resources" },
    @{ ScopeName = "purchase"; ScopeDescription = "Create and confirm customer purchases" }
)

$resourceServerInput = @{
    UserPoolId = $UserPoolId
    Identifier = $ResourceServerIdentifier
    Name = if ($resourceServer) { $resourceServer.Name } else { "Skate API" }
    Scopes = $scopes
}
$resourceServerJson = $resourceServerInput | ConvertTo-Json -Depth 8 -Compress
$resourceServerFile = Join-Path $env:TEMP ("skate-resource-server-{0}.json" -f [guid]::NewGuid())
[System.IO.File]::WriteAllText($resourceServerFile, $resourceServerJson, [System.Text.UTF8Encoding]::new($false))
try {
    $operation = if ($resourceServer) { "update-resource-server" } else { "create-resource-server" }
    & $aws.Source cognito-idp $operation --cli-input-json "file://$resourceServerFile"
    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo crear o actualizar el resource server de Cognito."
    }
}
finally {
    Remove-Item -LiteralPath $resourceServerFile -Force -ErrorAction SilentlyContinue
}

$preservedClientFields = @(
    "ClientName",
    "RefreshTokenValidity",
    "AccessTokenValidity",
    "IdTokenValidity",
    "TokenValidityUnits",
    "ReadAttributes",
    "WriteAttributes",
    "ExplicitAuthFlows",
    "SupportedIdentityProviders",
    "CallbackURLs",
    "LogoutURLs",
    "AllowedOAuthFlows",
    "AllowedOAuthFlowsUserPoolClient",
    "AnalyticsConfiguration",
    "PreventUserExistenceErrors",
    "EnableTokenRevocation",
    "EnablePropagateAdditionalUserContextData",
    "AuthSessionValidity"
)

foreach ($clientId in $AppClientIds) {
    if ([string]::IsNullOrWhiteSpace($clientId)) {
        throw "La lista AppClientIds no puede contener valores vacíos."
    }

    $clientResponse = Invoke-AwsJson -Arguments @(
        "cognito-idp", "describe-user-pool-client",
        "--user-pool-id", $UserPoolId,
        "--client-id", $clientId
    )
    $client = $clientResponse.UserPoolClient
    if (-not $client) {
        throw "No se encontró el App Client $clientId en el User Pool $UserPoolId."
    }

    $clientScopeNames = @()
    if ($clientId -eq "7vge2gthmumgohfn84aepg41qh") {
        $clientScopeNames = @(
            "$ResourceServerIdentifier/read",
            "$ResourceServerIdentifier/purchase"
        )
    } elseif ($clientId -eq "2ndve0vjmlau9as43sapcmermb") {
        $clientScopeNames = @(
            "$ResourceServerIdentifier/read",
            "$ResourceServerIdentifier/write"
        )
    } else {
        throw "App Client ID no reconocido: $clientId. Configura explícitamente si corresponde a tienda o administración."
    }
    $unrelatedScopes = @($client.AllowedOAuthScopes | Where-Object {
        $_ -notin @(
            "$ResourceServerIdentifier/read",
            "$ResourceServerIdentifier/write",
            "$ResourceServerIdentifier/purchase"
        )
    })
    $updatedScopes = @($unrelatedScopes + $clientScopeNames | Select-Object -Unique)

    $clientInput = @{
        ClientId = $clientId
        UserPoolId = $UserPoolId
        AllowedOAuthScopes = $updatedScopes
    }
    foreach ($field in $preservedClientFields) {
        $property = $client.PSObject.Properties[$field]
        if ($property -and $null -ne $property.Value) {
            $clientInput[$field] = $property.Value
        }
    }

    $clientJson = $clientInput | ConvertTo-Json -Depth 12 -Compress
    $clientFile = Join-Path $env:TEMP ("skate-app-client-{0}.json" -f [guid]::NewGuid())
    [System.IO.File]::WriteAllText($clientFile, $clientJson, [System.Text.UTF8Encoding]::new($false))
    try {
        & $aws.Source cognito-idp update-user-pool-client --cli-input-json "file://$clientFile"
        if ($LASTEXITCODE -ne 0) {
            throw "No se pudieron agregar los scopes al App Client $clientId."
        }
    }
    finally {
        Remove-Item -LiteralPath $clientFile -Force -ErrorAction SilentlyContinue
    }

    Write-Host "Scopes Cognito configurados para App Client $clientId`: $($clientScopeNames -join ', ')."
}
