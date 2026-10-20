# Skates Backend

	"sessionId": "cliente-123",
	"items": [
		{
			"skateId": 1,
			"quantity": 2
		}
	]

## Requisitos

- JDK 17 o superior.
- No es necesario instalar Maven: el proyecto incluye Maven Wrapper.
- Puerto `8080` disponible.
- Una base MySQL en AWS RDS y variables `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`.

Comprueba Java desde PowerShell:

```powershell
java -version
```

## Ejecutar el proyecto

Desde la raíz del repositorio (`Skates-backend`):

```powershell
$env:DB_URL="jdbc:mysql://<endpoint-rds>:3306/skates?sslMode=REQUIRED&serverTimezone=UTC"
$env:DB_USERNAME="<usuario-aplicacion>"
$env:DB_PASSWORD="<contraseña-aplicacion>"
./mvnw.cmd spring-boot:run
```

La API quedará disponible en `http://localhost:8080`. No hay una base de datos local embebida: el backend requiere conexión a MySQL.

La infraestructura de RDS, SNS, las colas y sus DLQ se declara en [aws-skates-platform.yaml](./infra/aws-skates-platform.yaml). Limita el parámetro `DatabaseClientCidr` a la IP pública `/32` del backend; no abras MySQL a `0.0.0.0/0`.


### Empaquetar y ejecutar el JAR

```powershell
./mvnw.cmd clean package
java -jar target/backend-skates-app-0.0.1-SNAPSHOT.jar
```

### Usar otro puerto

Si el puerto `8080` está ocupado:

```powershell
$env:SERVER_PORT="8081"
./mvnw.cmd spring-boot:run
```

En ese caso, se usa `http://localhost:8081` en todas las URLs.

## Visualizar y probar la API

Los endpoints `GET` se pueden abrir directamente en el navegador:

- `http://localhost:8080/api/skate`
- `http://localhost:8080/api/skate/modelos`
- `http://localhost:8080/api/skate/modelo/Street`
- `http://localhost:8080/api/skate/1`

También puedes probar todos los métodos con Postman usando la URL base `http://localhost:8080`.

## Autenticación

Los dos frontends usan AWS Amplify Auth con Amazon Cognito Hosted UI mediante Authorization Code y PKCE; no se necesita un client secret en el navegador. La API valida firma, emisor, expiración, tipo de token y `client_id`. Se aplican scopes de mínimo privilegio: tienda usa `skate-api/read` y `skate-api/purchase`; administración usa `skate-api/read` y `skate-api/write`. Además, las operaciones administrativas requieren el grupo Cognito `ADMIN`.

### Configuración de Cognito

1. Usa el User Pool `us-east-1_AOH2j7aTl` y el dominio `https://us-east-1aoh2j7atl.auth.us-east-1.amazoncognito.com`.
2. Usa los App Clients públicos de tienda `7vge2gthmumgohfn84aepg41qh` y admin `2ndve0vjmlau9as43sapcmermb` (sin client secret); habilita OAuth 2.0 Authorization Code Grant.
3. Registra en cada App Client las callback y logout URLs exactas de su frontend. En desarrollo, la tienda usa `https://localhost:5173/` y administración usa `https://localhost:5174/`; agrega también las URLs HTTPS de producción cuando estén disponibles.
4. Crea el grupo `ADMIN` en el User Pool y agrega a él las cuentas que administrarán el inventario. Las demás cuentas pueden iniciar sesión en la tienda, pero no modificar productos.
5. Configura el resource server y los App Clients con scopes separados: tienda recibe `skate-api/read` y `skate-api/purchase`; administración recibe `skate-api/read` y `skate-api/write`. El script conserva scopes ajenos a `skate-api/*` y el resto de las opciones existentes:

```powershell
.\scripts\configure-cognito-scopes.ps1
```

El script requiere AWS CLI con región y credenciales configuradas localmente, y permisos para listar resource servers, describir App Clients y crear o actualizar la configuración Cognito. No imprime ni almacena credenciales.

6. Configura el backend antes de ejecutarlo:

```powershell
$env:COGNITO_ISSUER_URI="https://cognito-idp.us-east-1.amazonaws.com/us-east-1_AOH2j7aTl"
$env:COGNITO_CLIENT_IDS="7vge2gthmumgohfn84aepg41qh,2ndve0vjmlau9as43sapcmermb"
$env:CORS_ALLOWED_ORIGINS="https://localhost:5173,https://localhost:5174"
./mvnw.cmd spring-boot:run
```

`COGNITO_ISSUER_URI` es el issuer del User Pool, no el dominio Hosted UI. No incluyas secretos en el código fuente.

7. En cada frontend, copia `.env.example` a `.env.local`. Amplify usa `VITE_COGNITO_USER_POOL_ID`, `VITE_COGNITO_CLIENT_ID`, `VITE_COGNITO_DOMAIN`, `VITE_COGNITO_REDIRECT_URI` y `VITE_COGNITO_LOGOUT_URI`; las URLs de retorno deben coincidir exactamente con las registradas para ese App Client.

El access token se envía automáticamente como `Authorization: Bearer <token>` a la API. Para probar endpoints protegidos desde Postman, inicia sesión desde uno de los frontends y usa el access token de Cognito. La API no emite tokens locales ni acepta el antiguo endpoint de credenciales de demostración.

### Pruebas públicas con ngrok y API Gateway

Esta configuración crea un HTTP API de API Gateway en AWS que reenvía las solicitudes a la API Spring Boot local a través de su túnel ngrok. **No despliega Spring Boot en AWS**: el backend debe seguir ejecutándose en tu equipo y ngrok debe permanecer activo. No uses este túnel de desarrollo como alojamiento de producción.

Requisitos: ngrok CLI autenticado, AWS CLI configurado con una región y permisos para crear/actualizar APIs HTTP y stacks de CloudFormation. El uso de API Gateway puede generar cargos según la cuenta y el tráfico.

1. Configura ngrok una vez con tu authtoken: `ngrok config add-authtoken <TOKEN>`. Para publicar tienda, admin y API con hostnames separados, necesitas dominios ngrok reservados distintos y configurar cada `domain` en [ngrok.yml](./infra/ngrok.yml); ngrok puede asignar el mismo dominio gratuito a todos los túneles y el script se detendrá si detecta esa colisión. Como alternativa para pruebas, inicia solo el túnel `tienda` y usa el proxy Vite configurado para publicar el backend en `/api/**` y el panel en `/admin/**` bajo ese mismo hostname; consulta los README de los frontends para iniciar ambos modos ngrok. Inicia Spring Boot en `8080` y los frontends en `5173` y `5174`. El modo `ngrok` sirve HTTP local mientras ngrok termina HTTPS, y `NGROK_HOST` debe contener el hostname exacto permitido por Vite.
2. Desde PowerShell, inicia los túneles:

   ```powershell
   .\scripts\start-ngrok.ps1
   ```

   El script imprime las URLs públicas HTTPS `api`, `tienda` y `admin`, y deja activo el agente ngrok.
3. Reinicia Spring Boot con CORS limitado a los dos orígenes públicos y los datos actuales de Cognito:

   ```powershell
   $env:COGNITO_ISSUER_URI="https://cognito-idp.us-east-1.amazonaws.com/us-east-1_AOH2j7aTl"
   $env:COGNITO_CLIENT_IDS="7vge2gthmumgohfn84aepg41qh,2ndve0vjmlau9as43sapcmermb"
   $env:CORS_ALLOWED_ORIGINS="<URL-TIENDA>,<URL-ADMIN>"
   $env:DB_URL="jdbc:mysql://<endpoint-rds>:3306/skates?sslMode=REQUIRED&serverTimezone=UTC"
   $env:DB_USERNAME="<usuario-aplicacion>"
   $env:DB_PASSWORD="<contraseña-aplicacion>"
   .\mvnw.cmd spring-boot:run
   ```

4. Despliega API Gateway desde otra consola PowerShell en la carpeta del backend:

   ```powershell
   .\scripts\deploy-api-gateway-ngrok.ps1 `
     -NgrokBackendUrl "<URL-API>" `
     -StorefrontOrigin "<URL-TIENDA>" `
     -AdminOrigin "<URL-ADMIN>"
   ```

   El script despliega [api-gateway-ngrok.yaml](./infra/api-gateway-ngrok.yaml) en la región activa de AWS CLI e imprime el endpoint de API Gateway.
5. Registra las URLs exactas `"<URL-TIENDA>/"` y `"<URL-ADMIN>/"` como callback y logout URLs en sus respectivos App Clients de Cognito. En cada frontend, establece `VITE_API_URL` con el endpoint que imprimió el script, y `VITE_COGNITO_REDIRECT_URI` y `VITE_COGNITO_LOGOUT_URI` con la URL ngrok de esa aplicación más `/`; luego inicia o reinicia Vite. En PowerShell, define cada `$env:VITE_...` en la consola del frontend antes de ejecutar `npm run dev`. Las URLs temporales ngrok cambian al reiniciar; actualiza Cognito y vuelve a desplegar API Gateway si cambian. Los dominios ngrok reservados evitan tener que actualizar las URLs en cada sesión.
6. Prueba el catálogo a través de API Gateway:

   ```powershell
   Invoke-RestMethod -Uri "<URL-API-GATEWAY>/api/skate" -Headers @{ Authorization = "Bearer <ACCESS_TOKEN>" }
   ```

   La lectura requiere un access token con `skate-api/read`; las operaciones de escritura requieren además `skate-api/write` y, para gestionar skates, el grupo `ADMIN`.

El API Gateway añade el encabezado requerido para reenviar solicitudes al túnel gratuito de ngrok y define CORS para los dos orígenes indicados al desplegar. No guardes authtokens de ngrok ni credenciales AWS en el repositorio.

### API Gateway con dos integraciones

La plantilla [api-gateway-microservices.yaml](./infra/api-gateway-microservices.yaml) enruta `/api/skate/**` y `/api/auth/**` al servicio de catálogo; `/api/inventory/**`, `/api/payments/**` y `/api/pago/**` al servicio de compras/inventario; y `/api/admin/messaging/**` al servicio administrador. Los tres servicios deben estar ejecutándose y accesibles por HTTPS:

```powershell
.\scripts\deploy-api-gateway-microservices.ps1 `
  -CatalogBackendUrl "https://<url-catalogo>" `
  -InventoryBackendUrl "https://<url-inventario>" `
  -AdminBackendUrl "https://<url-admin-api>" `
  -StorefrontOrigin "https://<url-tienda>" `
  -AdminOrigin "https://<url-admin>"
```

Las integraciones HTTP proxy reenvían el encabezado `Authorization` al backend, donde cada servicio valida el JWT de Cognito. La plantilla anterior [api-gateway-ngrok.yaml](./infra/api-gateway-ngrok.yaml) es solo para pruebas del backend monolítico local.

La base de datos es MySQL en Amazon RDS; sus credenciales se configuran mediante `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` y no se guardan en el repositorio. La plantilla crea la instancia cifrada y restringe el puerto 3306 al CIDR indicado.

Las lecturas del catálogo requieren `skate-api/read`. Crear y confirmar compras y consultar el estado Webpay requieren `skate-api/purchase`. La escritura del catálogo y la administración de mensajería requieren `skate-api/write` y el grupo Cognito `ADMIN`. La API responde `401` si falta un token válido y `403` si el token no tiene el scope o grupo requerido.

En Postman, agrega el encabezado a las operaciones protegidas:

```text
Authorization: Bearer <token>
```

El cliente tienda debe solicitar `skate-api/read` para consultar el catálogo y `skate-api/purchase` para pagos. El cliente admin debe solicitar `skate-api/read` y `skate-api/write`; las operaciones administrativas requieren además el grupo `ADMIN`.

## Endpoints

### Autenticación

| Método | URL | Acceso | Descripción |
| --- | --- | --- | --- |
| `GET` | `/api/auth/me` | Autenticado | Devuelve la identidad del usuario Cognito autenticado. |

### Skates

| Método | URL | Acceso | Descripción |
| --- | --- | --- | --- |
| `GET` | `/api/skate` | Scope `skate-api/read` | Lista todos los productos. |
| `GET` | `/api/skate/{id}` | Scope `skate-api/read` | Obtiene un producto por ID. |
| `GET` | `/api/skate/modelos` | Scope `skate-api/read` | Lista los modelos sin repetir. |
| `GET` | `/api/skate/modelo/{modelo}` | Scope `skate-api/read` | Filtra por modelo, sin distinguir mayúsculas. |
| `POST` | `/api/skate` | Scope `skate-api/write` + grupo `ADMIN` | Crea un producto. |
| `PUT` | `/api/skate/{id}` | Scope `skate-api/write` + grupo `ADMIN` | Actualiza un producto. |
| `DELETE` | `/api/skate/{id}` | Scope `skate-api/write` + grupo `ADMIN` | Elimina un producto. |

### Pagos con Webpay Plus

El backend integra Webpay Plus usando el SDK oficial de Transbank en ambiente de integración. El flujo es:

1. El frontend solicita una transacción al backend.
2. El backend devuelve `token`, `url` y `buyOrder`.
3. El frontend crea un formulario `POST` hacia `url` con el campo `token_ws` y lo envía a Webpay.
4. Webpay devuelve el resultado a `/api/payments/webpay/return`.
5. El backend confirma la transacción y valida que la orden y el monto coincidan con lo registrado en MySQL.

| Método | URL | Acceso | Descripción |
| --- | --- | --- | --- |
| `POST` | `/api/payments/webpay/transactions` | Scope `skate-api/purchase` | Crea una transacción Webpay. |
| `POST` | `/api/payments/webpay/return` | Público | Recibe el retorno de Webpay y confirma el pago. |
| `POST` | `/api/payments/webpay/commit?token_ws={token}` | Scope `skate-api/purchase` | Confirma manualmente una transacción. |
| `GET` | `/api/payments/webpay/status?token_ws={token}` | Scope `skate-api/purchase` | Consulta el estado en Transbank. |

El backend valida que los productos existan al crear la transacción, pero no modifica el stock. El descuento se realiza únicamente después de una confirmación Webpay con `status = AUTHORIZED` y `response_code = 0`.

Para iniciar un pago, envía:

```json
{
	"amount": 10000,
	"sessionId": "cliente-123"
}
```

El campo `sessionId` es opcional. La respuesta contiene un token y la URL de Webpay:

```json
{
	"buyOrder": "SK...",
	"sessionId": "cliente-123",
	"token": "...",
	"url": "https://webpay3gint.transbank.cl/webpayserver/initTransaction",
	"formAction": "https://webpay3gint.transbank.cl/webpayserver/initTransaction"
}
```

El frontend debe enviar el token mediante un formulario HTML, no como un encabezado JSON:

```html
<form method="post" action="{url}">
	<input type="hidden" name="token_ws" value="{token}">
	<button type="submit">Pagar</button>
</form>
```

Después del retorno de Webpay, la respuesta del backend incluye:

```json
{
	"buyOrder": "SK...",
	"token": "...",
	"paymentStatus": "COMPLETED",
	"completed": true,
	"stockUpdated": true,
	"authorizationCode": "123456",
	"message": "Compra autorizada y stock actualizado"
}
```

Si Transbank rechaza el pago, `paymentStatus` será `REJECTED` y el stock no cambiará. Si el pago fue autorizado pero no existe stock suficiente, será `STOCK_REVIEW`; la actualización completa se cancela y no se descuenta ningún producto. Las confirmaciones repetidas de un mismo token no vuelven a descontar stock.

Para pruebas de integración, Transbank documenta la tarjeta VISA aprobada `4051 8856 0044 6623`, CVV `123`, cualquier fecha de expiración, RUT `11.111.111-1` y clave `123`. Estas tarjetas solo deben usarse en el ambiente de integración.

Las credenciales y el callback se configuran mediante variables de entorno:

```powershell
$env:TRANSBANK_ENVIRONMENT="TEST"
$env:TRANSBANK_COMMERCE_CODE="597055555532"
$env:TRANSBANK_API_KEY_SECRET="<secreto configurado localmente desde Transbank>"
$env:TRANSBANK_RETURN_URL="http://localhost:8080/api/payments/webpay/return"
```

No guardes el secreto en `application.properties`, archivos `.env`, documentación ni control de versiones. Si no está configurado, el backend puede iniciar, pero las solicitudes de creación de pago devolverán un error explícito.

Si ejecutas el backend en otro puerto, actualiza también `TRANSBANK_RETURN_URL`. Nunca uses las credenciales de integración en producción.

## Crear y actualizar productos

`POST /api/skate` y `PUT /api/skate/{id}` reciben JSON con esta estructura:

```json
{
	"modelo": "Street",
	"marca": "DC",
	"medida": 8.0,
	"wheelbase": null,
	"stock": 10
}
```

Reglas de validación:

- `modelo` y `marca` son obligatorios y no pueden estar vacíos.
- `medida`, cuando se informa, debe ser mayor que `0`.
- `wheelbase`, cuando se informa, debe ser mayor que `0`.
- `stock` es obligatorio y debe ser mayor o igual a `0`.
- Para productos `Street` normalmente se informa `medida`.
- Para productos `Longboard` o `Downhill` normalmente se informa `wheelbase`.

Ejemplo de creación en PowerShell:

```powershell
$headers = @{ Authorization = "Bearer $token" }
$body = @{
	modelo = "Street"
	marca = "Independent"
	medida = 8.25
	wheelbase = $null
	stock = 12
} | ConvertTo-Json

Invoke-RestMethod `
	-Method Post `
	-Uri "http://localhost:8080/api/skate" `
	-Headers $headers `
	-ContentType "application/json" `
	-Body $body
```

Ejemplo de actualización:

```powershell
$body = @{
	modelo = "Street"
	marca = "Independent"
	medida = 8.25
	wheelbase = $null
	stock = 20
} | ConvertTo-Json

Invoke-RestMethod `
	-Method Put `
	-Uri "http://localhost:8080/api/skate/1" `
	-Headers $headers `
	-ContentType "application/json" `
	-Body $body
```

Ejemplo de eliminación:

```powershell
Invoke-RestMethod `
	-Method Delete `
	-Uri "http://localhost:8080/api/skate/1" `
	-Headers $headers
```

## Base de datos AWS RDS MySQL

H2 y su consola fueron eliminados. El backend conecta exclusivamente a MySQL mediante estas variables obligatorias:

- `DB_URL`: JDBC URL del endpoint RDS, con `sslMode=REQUIRED`.
- `DB_USERNAME` y `DB_PASSWORD`: usuario dedicado de aplicación; evita usar el usuario maestro de RDS.
- `JPA_DDL_AUTO`: opcional; por defecto `update` para conservar y evolucionar el esquema sin borrar datos.

La plantilla [aws-skates-platform.yaml](./infra/aws-skates-platform.yaml) crea una instancia MySQL cifrada, con copias de seguridad de siete días y acceso restringido por CIDR. Requiere seleccionar una VPC y al menos dos subredes en zonas de disponibilidad distintas. Para desarrollo local, limita `DatabaseClientCidr` a tu IP pública `/32`; para producción, aloja el backend en la VPC y limita el acceso a su grupo de seguridad.

Después de crear la instancia, crea un usuario SQL de aplicación con permisos solo sobre la base `skates` y configura las tres variables anteriores en el entorno de cada servicio. No subas la contraseña ni la URL con credenciales a Git.

Los datos de ejemplo de `Street`, `Longboard` y `Downhill` se insertan únicamente cuando la tabla de productos está vacía.

## CORS

El backend permite solicitudes desde:

- `https://localhost:5173`
- `http://localhost:5174`

Los métodos permitidos son `GET`, `POST`, `PUT`, `PATCH`, `DELETE` y `OPTIONS` para las rutas `/api/**`.

## Pruebas y calidad

Ejecutar todas las pruebas:

```powershell
./mvnw.cmd test
```

Limpiar, recompilar y probar:

```powershell
./mvnw.cmd clean test
```

Compilar sin ejecutar pruebas:

```powershell
./mvnw.cmd -q -DskipTests compile
```

Las pruebas de integración usan MySQL 8.4 con Testcontainers y se omiten cuando Docker no está disponible. Las pruebas unitarias no requieren AWS ni una instancia RDS.

## Respuestas de error

Los errores de validación y los productos inexistentes se devuelven como JSON con `timestamp` y `mensaje`.

- `400 Bad Request`: datos inválidos.
- `401 Unauthorized`: falta el token, el token expiró o el token de Cognito no es válido.
- `403 Forbidden`: el usuario autenticado no tiene rol `ADMIN`.
- `404 Not Found`: el producto solicitado no existe.

## Estructura principal

```
src/main/java/cl/duoc/backendskatesapp/
├── config/       Seguridad y CORS
├── controller/   Endpoints REST y manejo de errores
├── model/        Entidad Skate
├── repository/   Acceso a datos JPA
└── service/      Reglas de negocio y datos iniciales
```

## Solución rápida de problemas

### Arranque automático del backend en EC2

El backend debe ejecutarse como servicio `systemd`, no depender de una terminal SSH abierta. La configuración siguiente se realiza una sola vez en la EC2. Requiere que el JAR esté en `/home/ec2-user/app.jar` y que Java esté instalado.

1. Crea un archivo de entorno privado para las credenciales y configuración:

   ```bash
   sudo install -d -o root -g root -m 700 /etc/skates
   sudo nano /etc/skates/backend.env
   ```

   Incluye una variable por línea, con los valores actuales. No subas este archivo al repositorio ni compartas su contenido:

   ```ini
   DB_URL=jdbc:mysql://skates-db.cq2uked3iq8l.us-east-1.rds.amazonaws.com:3306/skates?sslMode=REQUIRED&serverTimezone=UTC
   DB_USERNAME=skates_app
   DB_PASSWORD=<contraseña-actual-de-skates_app>
   COGNITO_ISSUER_URI=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_AOH2j7aTl
   COGNITO_CLIENT_IDS=7vge2gthmumgohfn84aepg41qh,2ndve0vjmlau9as43sapcmermb
   CORS_ALLOWED_ORIGINS=https://main.d1o3gk7dbea0nw.amplifyapp.com,https://main.d3sasx7zpqdlhj.amplifyapp.com
   ```

   Sustituye el marcador de contraseña por la contraseña actual de RDS. Protege el archivo y verifica que solo root pueda leerlo:

   ```bash
   sudo chown root:root /etc/skates/backend.env
   sudo chmod 600 /etc/skates/backend.env
   ```

2. Crea `/etc/systemd/system/skates-backend.service`:

   ```ini
   [Unit]
   Description=Skates Spring Boot backend
   Wants=network-online.target
   After=network-online.target

   [Service]
   Type=simple
   User=ec2-user
   WorkingDirectory=/home/ec2-user
   EnvironmentFile=/etc/skates/backend.env
   ExecStart=/usr/bin/java -jar /home/ec2-user/app.jar
   Restart=on-failure
   RestartSec=15

   [Install]
   WantedBy=multi-user.target
   ```

   Confirma la ruta de Java con `command -v java`; si no es `/usr/bin/java`, actualiza `ExecStart` con la ruta que devuelve el comando.

3. Antes de iniciar el servicio, verifica si todavía hay una ejecución manual escuchando en el puerto 8080:

   ```bash
   sudo ss -ltnp | grep ':8080'
   ```

   Si hay un proceso, confirma con `ps -fp <PID>` que corresponde a `/home/ec2-user/app.jar` y detenlo de forma controlada con `kill -TERM <PID>`. No detengas un proceso que no hayas identificado.

4. Activa el servicio y haz que inicie automáticamente en cada arranque de EC2:

   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable --now skates-backend
   sudo systemctl status skates-backend --no-pager
   ```

   Comprueba que Spring Boot haya iniciado y que responde localmente:

   ```bash
   curl -i http://127.0.0.1:8080/api/skate
   ```

   Sin token, `401 Unauthorized` es esperado y confirma que la aplicación responde. Tras reiniciar la EC2, `systemd` volverá a iniciar el backend; si RDS aún no está disponible, el servicio reintentará cada 15 segundos.

### EC2 está encendida, pero el backend no responde (`HTTP 000`)

Comprueba primero el servicio, en vez de iniciar otra copia del JAR:

```bash
sudo systemctl status skates-backend --no-pager
sudo journalctl -u skates-backend -n 100 --no-pager
```

Si el servicio está detenido, intenta iniciarlo y valida su estado:

```bash
sudo systemctl restart skates-backend
sudo systemctl status skates-backend --no-pager
```

Si falla, revisa los registros, que RDS esté disponible, la conectividad al puerto 3306 y las variables del archivo `/etc/skates/backend.env`. No imprimas ni compartas el contenido de ese archivo, pues incluye la contraseña de base de datos. Si la contraseña de `skates_app` cambió, actualiza el archivo de entorno y reinicia el servicio:

```bash
sudo systemctl restart skates-backend
```

### El backend no conecta a RDS

Verifica que `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` estén definidos, que el Security Group permita el puerto 3306 desde el backend y que la URL JDBC use `sslMode=REQUIRED`.

### No aparecen los datos de ejemplo

Los datos de ejemplo solo se insertan cuando la tabla `skates` está vacía. Revisa que el servicio use la base de datos y el esquema RDS esperados.

### Simular transsacción Webpay

Para simular el pago con la api Webpay se debe ingresar a la pagina:
`https://www.transbankdevelopers.cl/documentacion/como_empezar#tarjetas-de-prueba`

Usando el apartado de `Tarjetas de Prueba`

Correo de cliente: cliente@gmail.com Contraseña: Cliente123.

Correo de admin: admin@gmail.com Contraseña: Admin123.
