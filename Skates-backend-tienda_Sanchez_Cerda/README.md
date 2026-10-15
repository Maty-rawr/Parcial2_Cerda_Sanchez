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

La API usa tokens JWT. El login es público y devuelve un token válido durante una hora.

### Usuarios de desarrollo

| Usuario | Contraseña | Rol |
| --- | --- | --- |
| `cliente` | `cliente123` | `CLIENTE` |
| `admin` | `admin123` | `ADMIN` |

Solicita un token:

```powershell
$login = Invoke-RestMethod `
	-Method Post `
	-Uri "http://localhost:8080/api/auth/login" `
	-ContentType "application/json" `
	-Body '{"username":"admin","password":"admin123"}'

$token = $login.token
$token
```

En Postman, agrega el encabezado a las operaciones protegidas:

```text
Authorization: Bearer <token>
```

Los `GET` de skates son públicos. Las operaciones `POST`, `PUT` y `DELETE` requieren el rol `ADMIN`.

## Endpoints

### Autenticación

| Método | URL | Acceso | Descripción |
| --- | --- | --- | --- |
| `POST` | `/api/auth/login` | Público | Genera un token JWT. |

### Skates

| Método | URL | Acceso | Descripción |
| --- | --- | --- | --- |
| `GET` | `/api/skate` | Público | Lista todos los productos. |
| `GET` | `/api/skate/{id}` | Público | Obtiene un producto por ID. |
| `GET` | `/api/skate/modelos` | Público | Lista los modelos sin repetir. |
| `GET` | `/api/skate/modelo/{modelo}` | Público | Filtra por modelo, sin distinguir mayúsculas. |
| `POST` | `/api/skate` | ADMIN | Crea un producto. |
| `PUT` | `/api/skate/{id}` | ADMIN | Actualiza un producto. |
| `DELETE` | `/api/skate/{id}` | ADMIN | Elimina un producto. |

### Pagos con Webpay Plus

El backend integra Webpay Plus usando el SDK oficial de Transbank en ambiente de integración. El flujo es:

1. El frontend solicita una transacción al backend.
2. El backend devuelve `token`, `url` y `buyOrder`.
3. El frontend crea un formulario `POST` hacia `url` con el campo `token_ws` y lo envía a Webpay.
4. Webpay devuelve el resultado a `/api/payments/webpay/return`.
5. El backend confirma la transacción y valida que la orden y el monto coincidan con lo registrado en H2.

| Método | URL | Acceso | Descripción |
| --- | --- | --- | --- |
| `POST` | `/api/payments/webpay/transactions` | Autenticado | Crea una transacción Webpay. |
| `POST` | `/api/payments/webpay/return` | Público | Recibe el retorno de Webpay y confirma el pago. |
| `POST` | `/api/payments/webpay/commit?token_ws={token}` | Autenticado | Confirma manualmente una transacción. |
| `GET` | `/api/payments/webpay/status?token_ws={token}` | Autenticado | Consulta el estado en Transbank. |

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
$env:TRANSBANK_API_KEY_SECRET="579B532A7440BB0C9079DED94D31EA1615BACEB56610332264630D42D0A36B1C"
$env:TRANSBANK_RETURN_URL="http://localhost:8080/api/payments/webpay/return"
```

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

- `http://localhost:5173`
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
- `401 Unauthorized`: falta el token, el token expiró o las credenciales son incorrectas.
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