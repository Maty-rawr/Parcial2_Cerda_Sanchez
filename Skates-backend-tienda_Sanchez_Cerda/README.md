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

Comprueba Java desde PowerShell:

```powershell
java -version
```

## Ejecutar el proyecto

Desde la raíz del repositorio (`Skates-backend`):

```powershell
./mvnw.cmd spring-boot:run
```

La aplicación quedará disponible en:

- API: `http://localhost:8080`
- Consola H2: `http://localhost:8080/h2-console/`


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

Los dos frontends usan AWS Amplify Auth con Amazon Cognito Hosted UI mediante Authorization Code y PKCE; no se necesita un client secret en el navegador. La API valida firma, emisor, expiración, tipo de token y `client_id`. Los scopes `skate-api/read` y `skate-api/write` autorizan lectura y escritura. Además, las operaciones que modifican productos requieren que el usuario pertenezca al grupo Cognito `ADMIN`.

### Configuración de Cognito

1. Usa el User Pool `us-east-1_AOH2j7aTl` y el dominio `https://us-east-1aoh2j7atl.auth.us-east-1.amazoncognito.com`.
2. Usa los App Clients públicos de tienda `7vge2gthmumgohfn84aepg41qh` y admin `2ndve0vjmlau9as43sapcmermb` (sin client secret); habilita OAuth 2.0 Authorization Code Grant.
3. Registra en cada App Client las callback y logout URLs exactas de su frontend. En desarrollo, la tienda usa `https://localhost:5173/` y administración usa `https://localhost:5174/`; agrega también las URLs HTTPS de producción cuando estén disponibles.
4. Crea el grupo `ADMIN` en el User Pool y agrega a él las cuentas que administrarán el inventario. Las demás cuentas pueden iniciar sesión en la tienda, pero no modificar productos.
5. Configura el resource server y agrega `skate-api/read` y `skate-api/write` a ambos App Clients. El script conserva los demás scopes y opciones existentes de cada cliente:

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
   $env:H2_CONSOLE_ENABLED="false"
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

La plantilla [api-gateway-microservices.yaml](./infra/api-gateway-microservices.yaml) enruta `/api/skate/**` al servicio de catálogo y `/api/inventory/**`, `/api/payments/**` y `/api/pago/**` al servicio de inventario. Para desplegarla, los dos servicios deben estar ejecutándose y accesibles por HTTPS:

```powershell
.\scripts\deploy-api-gateway-microservices.ps1 `
  -CatalogBackendUrl "https://<url-catalogo>" `
  -InventoryBackendUrl "https://<url-inventario>" `
  -StorefrontOrigin "https://<url-tienda>" `
  -AdminOrigin "https://<url-admin>"
```

Esta plantilla solo configura API Gateway; no crea ni separa aplicaciones Spring Boot. El backend de este repositorio continúa siendo una aplicación monolítica en `8080`, por lo que todavía no se debe desplegar la plantilla de dos integraciones hasta disponer de servicios de catálogo e inventario separados. La plantilla anterior `api-gateway-ngrok.yaml` sigue siendo la opción para probar ese backend monolítico.

Esta configuración cubre Cognito y autorización. La persistencia continúa usando H2 por ahora; EC2 es infraestructura de cómputo, no un motor de base de datos. La migración de base de datos requiere elegir un motor compatible y configurar sus credenciales fuera del código fuente.

Las solicitudes de lectura requieren un access token con el scope `skate-api/read`. Las operaciones de escritura requieren `skate-api/write`; además, crear, actualizar o eliminar skates requiere pertenecer al grupo Cognito `ADMIN`. La API responde `401` si falta un token válido y `403` si el token no tiene el scope o rol requerido.

En Postman, agrega el encabezado a las operaciones protegidas:

```text
Authorization: Bearer <token>
```

Los `GET` de la API requieren `skate-api/read`. Las operaciones `POST`, `PUT` y `DELETE` de skates requieren `skate-api/write` y el grupo `ADMIN`.

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
5. El backend confirma la transacción y valida que la orden y el monto coincidan con lo registrado en H2.

| Método | URL | Acceso | Descripción |
| --- | --- | --- | --- |
| `POST` | `/api/payments/webpay/transactions` | Scope `skate-api/write` | Crea una transacción Webpay. |
| `POST` | `/api/payments/webpay/return` | Público | Recibe el retorno de Webpay y confirma el pago. |
| `POST` | `/api/payments/webpay/commit?token_ws={token}` | Scope `skate-api/write` | Confirma manualmente una transacción. |
| `GET` | `/api/payments/webpay/status?token_ws={token}` | Scope `skate-api/read` | Consulta el estado en Transbank. |

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

## Base de datos H2

La aplicación usa H2 en modo archivo para desarrollo local:

- JDBC URL: `jdbc:h2:file:./data/skates;MODE=MySQL;AUTO_SERVER=TRUE`
- Usuario: `sa`
- Contraseña: vacía
- Archivos de datos: `data/skates.*`
- Consola: `http://localhost:8080/h2-console/`

En la pantalla de H2, completa los campos con esos valores y presiona **Connect**. Los cambios realizados allí permanecen después de reiniciar la aplicación porque la base está almacenada en archivos.

Hibernate usa `ddl-auto=update`, por lo que crea o actualiza la tabla sin borrar los datos existentes. Si la tabla está vacía, la aplicación carga automáticamente datos de ejemplo de los modelos `Street`, `Longboard` y `Downhill`.

No elimines los archivos de `data/` si necesitas conservar los datos locales.

### Simular una compra desde la consola H2

Para simular la compra de una unidad del skate con `ID = 1`, ejecuta en el editor SQL de H2:

```sql
UPDATE SKATES SET STOCK = STOCK - 1 WHERE ID = 1;
```

Esta consulta realiza lo siguiente:

- Busca el registro de la tabla `SKATES` cuyo `ID` sea `1`.
- Disminuye su valor de `STOCK` en una unidad.
- Si el stock actual era `8`, después de la compra quedará en `7`.
- Solo modifica ese registro; los demás productos no cambian.

Después de ejecutar la consulta, H2 mostrará la cantidad de filas afectadas. Lo esperado es `1` si existe el producto con `ID = 1`. Para verificar el nuevo stock, ejecuta:

```sql
SELECT ID, MODELO, MARCA, STOCK
FROM SKATES
WHERE ID = 1;
```

La consulta de simulación no valida que el stock sea mayor que cero. Si se ejecuta cuando `STOCK` vale `0`, podría quedar en `-1`; en una compra real conviene validar el stock antes de descontarlo.

## CORS

El backend permite solicitudes desde:

- `https://localhost:5173`
- `http://localhost:5174`

Los métodos permitidos son `GET`, `POST`, `PUT`, `DELETE` y `OPTIONS` para las rutas `/api/**`.

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

Las pruebas usan una base H2 en memoria (`jdbc:h2:mem:skates`), independiente de la base persistente de `data/`.

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

### El puerto 8080 está ocupado

Ejecuta la aplicación en otro puerto:

```powershell
$env:SERVER_PORT="8081"
./mvnw.cmd spring-boot:run
```

### H2 muestra `401` o `404`

Verifica que hayas ejecutado `./mvnw.cmd clean package` o `./mvnw.cmd spring-boot:run` después de descargar las dependencias y que estés usando `/h2-console/`. La dependencia `spring-boot-h2console` es necesaria con Spring Boot 4.

### No aparecen los datos de ejemplo

Comprueba que estás usando la URL `jdbc:h2:file:./data/skates` y que la tabla `skates` no contiene registros previos. Los datos iniciales solo se cargan cuando la tabla está vacía.

### Simular transsacción Webpay

Para simular el pago con la api Webpay se debe ingresar a la pagina:
`https://www.transbankdevelopers.cl/documentacion/como_empezar#tarjetas-de-prueba`

Usando el apartado de `Tarjetas de Prueba`

Correo de cliente: cliente@gmail.com Contraseña: Cliente123.

Correo de admin: admin@gmail.com Contraseña: Admin123.
