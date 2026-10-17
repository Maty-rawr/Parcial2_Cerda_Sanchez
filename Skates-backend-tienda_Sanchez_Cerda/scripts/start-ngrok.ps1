$ErrorActionPreference = "Stop"

$ngrok = Get-Command ngrok -ErrorAction SilentlyContinue
if (-not $ngrok) {
    $ngrokLinksPath = Join-Path $env:LOCALAPPDATA "Microsoft\WinGet\Links\ngrok.exe"
    if (Test-Path -LiteralPath $ngrokLinksPath) {
        $ngrokPath = $ngrokLinksPath
    } else {
        $wingetPackagesPath = Join-Path $env:LOCALAPPDATA "Microsoft\WinGet\Packages"
        $ngrokPackage = Get-ChildItem -LiteralPath $wingetPackagesPath -Directory -Filter "Ngrok.Ngrok_*" -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($ngrokPackage) {
            $ngrokExecutable = Join-Path $ngrokPackage.FullName "ngrok.exe"
            if (Test-Path -LiteralPath $ngrokExecutable) {
                $ngrokPath = $ngrokExecutable
            }
        }
    }
    if (-not $ngrokPath) {
        throw "No se encontro ngrok en PATH ni en la instalacion de WinGet. Instala ngrok y configura el authtoken."
    }
} else {
    $ngrokPath = $ngrok.Source
}

$existingNgrokAgent = $false
try {
    Invoke-RestMethod -Uri "http://127.0.0.1:4040/api/tunnels" -TimeoutSec 2 | Out-Null
    $existingNgrokAgent = $true
} catch {
    Write-Verbose "No se encontro un agente existente de ngrok en localhost:4040."
}
if ($existingNgrokAgent) {
    throw "Ya hay una API local de ngrok en el puerto 4040. Cierra ese agente antes de iniciar estos tuneles."
}

$configPath = Join-Path $PSScriptRoot "..\infra\ngrok.yml"
$configPath = (Resolve-Path -LiteralPath $configPath).Path
$defaultConfigPath = Join-Path $env:LOCALAPPDATA "ngrok\ngrok.yml"
if (-not (Test-Path -LiteralPath $defaultConfigPath)) {
    throw "No se encontro la configuracion global de ngrok con el authtoken. Ejecuta 'ngrok config add-authtoken <TOKEN>' primero."
}
$agentOutput = Join-Path $env:TEMP "skates-ngrok-agent-output.log"
$agentError = Join-Path $env:TEMP "skates-ngrok-agent-error.log"
$process = Start-Process -FilePath $ngrokPath `
    -ArgumentList "start --all --config `"$defaultConfigPath`" --config `"$configPath`"" `
    -RedirectStandardOutput $agentOutput `
    -RedirectStandardError $agentError `
    -PassThru `
    -WindowStyle Hidden

try {
    $tunnels = $null
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($process.HasExited) {
            throw "ngrok termino al iniciar. Revisa el authtoken y el archivo de configuracion."
        }
        try {
            $response = Invoke-RestMethod -Uri "http://127.0.0.1:4040/api/tunnels" -TimeoutSec 2
            $tunnels = $response.tunnels
            $names = @($tunnels | ForEach-Object { $_.name })
            if (@("api", "tienda", "admin") | Where-Object { $_ -notin $names }) {
                $tunnels = $null
            } else {
                break
            }
        } catch {
            $tunnels = $null
        }
        Start-Sleep -Seconds 1
    }

    if (-not $tunnels) {
        throw "No aparecieron los tres tuneles en la API local de ngrok dentro del tiempo esperado."
    }

    $result = foreach ($name in @("api", "tienda", "admin")) {
        $tunnel = $tunnels | Where-Object { $_.name -eq $name -and $_.public_url.StartsWith("https://") } | Select-Object -First 1
        if (-not $tunnel) {
            throw "ngrok no publico el tunel HTTPS '$name'."
        }
        [PSCustomObject]@{
            Servicio = $name
            Url = $tunnel.public_url
        }
    }

    $uniqueUrls = @($result | Select-Object -ExpandProperty Url -Unique)
    if ($uniqueUrls.Count -ne $result.Count) {
        throw "ngrok asigno el mismo dominio publico a varios servicios. No es seguro usar las URLs como si fueran independientes. Reserva dominios diferentes o configura un proxy unico con rutas para tienda, admin y API."
    }

    $result | Format-Table -AutoSize
    Write-Host "ngrok sigue activo (PID $($process.Id)); detenlo con 'ngrok kill' cuando termines."
    Write-Host "Usa la URL api para NgrokBackendUrl y las otras dos como origenes exactos de CORS y callbacks Cognito."
} catch {
    if (-not $process.HasExited) {
        Stop-Process -Id $process.Id
    }
    if (Test-Path -LiteralPath $agentOutput) {
        Write-Host "Salida de ngrok:"
        Get-Content -LiteralPath $agentOutput -Tail 30
        Remove-Item -LiteralPath $agentOutput
    }
    if (Test-Path -LiteralPath $agentError) {
        Get-Content -LiteralPath $agentError -Tail 30
        Remove-Item -LiteralPath $agentError
    }
    throw
}
