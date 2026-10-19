# Acceso a las aplicaciones Skates

## URLs disponibles actualmente

Estas direcciones corresponden a los servidores de desarrollo de Vite en el equipo donde se ejecuta el proyecto:

| Aplicación | URL |
| --- | --- |
| Tienda para clientes | <https://localhost:5173/> |
| Panel de administración | <https://localhost:5174/> |

No son URLs públicas ni significan que los frontends estén desplegados en AWS. Para abrirlas, los servidores de desarrollo deben estar ejecutándose en el equipo local.

## Iniciar los frontends en PowerShell

En dos ventanas de PowerShell, ejecuta un comando en cada una:

```powershell
Set-Location 'Skates-tienda_Sanchez_Cerda'
npm run dev -- --host 127.0.0.1
```

```powershell
Set-Location 'Skates-tienda-admin_Sanchez_Cerda'
npm run dev -- --host 127.0.0.1
```

Luego abre las URLs de la tabla. Vite usa un certificado HTTPS local autofirmado; acepta la advertencia del navegador para continuar en este entorno de desarrollo.

## Inicio de sesión

- La tienda usa su propio App Client de Cognito y requiere los scopes `openid`, `email`, `skate-api/read` y `skate-api/purchase`.
- El panel admin usa un App Client independiente. La cuenta debe pertenecer al grupo Cognito `ADMIN` y tener los scopes `openid`, `email`, `skate-api/read` y `skate-api/write`.
- Las URLs de callback y cierre de sesión de cada App Client deben coincidir exactamente con la URL correspondiente de la tabla.

## Estado de la integración cloud

La base de datos RDS y el backend Spring Boot están en AWS. Para que el catálogo funcione sin depender del equipo local, aún hace falta publicar los frontends y enrutar sus llamadas al backend mediante API Gateway. No se debe abrir el puerto `8080` del backend a Internet como sustituto de esa integración.
