param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern("^https://[A-Za-z0-9.-]+(:[0-9]+)?$")]
    [string]$NgrokBackendUrl,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^https://[A-Za-z0-9.-]+(:[0-9]+)?$")]
    [string]$StorefrontOrigin,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^https://[A-Za-z0-9.-]+(:[0-9]+)?$")]
    [string]$AdminOrigin,

    [string]$StackName = "skates-api-gateway-ngrok-test"
)

$ErrorActionPreference = "Stop"
$aws = Get-Command aws -ErrorAction SilentlyContinue
if (-not $aws) {
    throw "No se encontro AWS CLI en PATH. Instala AWS CLI y configura credenciales para la cuenta y region objetivo."
}

$templatePath = Join-Path $PSScriptRoot "..\infra\api-gateway-ngrok.yaml"
$templatePath = (Resolve-Path -LiteralPath $templatePath).Path

& $aws.Source cloudformation deploy `
    --template-file $templatePath `
    --stack-name $StackName `
    --parameter-overrides `
        "NgrokBackendUrl=$NgrokBackendUrl" `
        "StorefrontOrigin=$StorefrontOrigin" `
        "AdminOrigin=$AdminOrigin"
if ($LASTEXITCODE -ne 0) {
    throw "CloudFormation no pudo desplegar el API Gateway."
}

$apiUrl = & $aws.Source cloudformation describe-stacks `
    --stack-name $StackName `
    --query "Stacks[0].Outputs[?OutputKey=='ApiUrl'].OutputValue | [0]" `
    --output text
if ($LASTEXITCODE -ne 0 -or -not $apiUrl -or $apiUrl -eq "None") {
    throw "No se pudo obtener el URL publicado por API Gateway."
}

Write-Host "API Gateway: $apiUrl"
Write-Host "Inicia cada frontend con VITE_API_URL='$apiUrl' para que las peticiones usen API Gateway."
Write-Host "Antes de iniciar Spring Boot, configura CORS_ALLOWED_ORIGINS='$StorefrontOrigin,$AdminOrigin'."
