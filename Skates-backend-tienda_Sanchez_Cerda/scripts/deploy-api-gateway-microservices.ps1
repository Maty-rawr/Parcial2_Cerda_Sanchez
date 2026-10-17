param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern("^https://[A-Za-z0-9.-]+(:[0-9]+)?$")]
    [string]$CatalogBackendUrl,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^https://[A-Za-z0-9.-]+(:[0-9]+)?$")]
    [string]$InventoryBackendUrl,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^https://[A-Za-z0-9.-]+(:[0-9]+)?$")]
    [string]$StorefrontOrigin,

    [Parameter(Mandatory = $true)]
    [ValidatePattern("^https://[A-Za-z0-9.-]+(:[0-9]+)?$")]
    [string]$AdminOrigin,

    [string]$StackName = "skates-api-gateway-microservices"
)

$ErrorActionPreference = "Stop"
$aws = Get-Command aws -ErrorAction SilentlyContinue
if (-not $aws) {
    throw "AWS CLI no está en PATH. Instálalo y configura localmente la región y las credenciales."
}

$templatePath = Join-Path $PSScriptRoot "..\infra\api-gateway-microservices.yaml"
$templatePath = (Resolve-Path -LiteralPath $templatePath).Path

& $aws.Source cloudformation deploy `
    --template-file $templatePath `
    --stack-name $StackName `
    --parameter-overrides `
        "CatalogBackendUrl=$CatalogBackendUrl" `
        "InventoryBackendUrl=$InventoryBackendUrl" `
        "StorefrontOrigin=$StorefrontOrigin" `
        "AdminOrigin=$AdminOrigin"
if ($LASTEXITCODE -ne 0) {
    throw "CloudFormation no pudo desplegar el API Gateway de microservicios."
}

$apiUrl = & $aws.Source cloudformation describe-stacks `
    --stack-name $StackName `
    --query "Stacks[0].Outputs[?OutputKey=='ApiUrl'].OutputValue | [0]" `
    --output text
if ($LASTEXITCODE -ne 0 -or -not $apiUrl -or $apiUrl -eq "None") {
    throw "No se pudo obtener el URL publicado por API Gateway."
}

Write-Host "API Gateway: $apiUrl"
Write-Host "Configura VITE_API_URL con esta URL en ambos frontends."
