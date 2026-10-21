# Acceso a las aplicaciones Skates

## URLs locales de desarrollo

Estas direcciones corresponden a los servidores de desarrollo de Vite en el equipo donde se ejecuta el proyecto:

| Aplicación | URL |
| --- | --- |
| Tienda para clientes | <https://localhost:5173/> |
| Panel de administración | <https://localhost:5174/> |

No son URLs públicas ni significan que los frontends estén desplegados en AWS. Para abrirlas, los servidores de desarrollo deben estar ejecutándose en el equipo local.

## Iniciar los frontends en PowerShell

En dos ventanas de PowerShell, ejecutar un comando en cada una:

```powershell
Set-Location 'Skates-tienda_Sanchez_Cerda'
npm run dev -- --host 127.0.0.1
```

```powershell
Set-Location 'Skates-tienda-admin_Sanchez_Cerda'
npm run dev -- --host 127.0.0.1
```

A continuación, abrir las URLs de la tabla. Vite usa un certificado HTTPS local autofirmado; aceptar la advertencia del navegador para continuar en este entorno de desarrollo.

## Inicio de sesión

- La tienda usa su propio App Client de Cognito y requiere los scopes `openid`, `email`, `skate-api/read` y `skate-api/purchase`.
- El panel admin usa un App Client independiente. La cuenta debe pertenecer al grupo Cognito `ADMIN` y tener los scopes `openid`, `email`, `skate-api/read` y `skate-api/write`.
- Las URLs de callback y cierre de sesión de cada App Client deben coincidir exactamente con la URL correspondiente de la tabla.

## Estado de la integración cloud

La tienda y el panel de administración están publicados en AWS Amplify y consumen el backend a través de API Gateway. El backend Spring Boot corre en EC2 como servicio `systemd` (`skates-backend`), conectado a RDS MySQL. No requiere iniciar comandos desde VS Code ni mantener una sesión SSH abierta. No se debe abrir el puerto `8080` del backend a Internet.

## Preparar la presentación y recuperación de emergencia

Antes de presentar, comprobar en AWS:

1. La base RDS `skates-db` aparece como **Available**.
2. La instancia EC2 `skates-backend` aparece como **Running**.
3. En EC2, el target del backend en `skates-backend-tg` aparece **Healthy**.
4. Abrir los dominios de Amplify de la tienda y del panel admin, iniciar sesión y confirmar que carguen el catálogo/inventario.

El servicio `skates-backend` está habilitado para iniciar automáticamente al encender EC2. API Gateway, VPC Link, ALB y Amplify son servicios administrados y no requieren inicio desde VS Code. Si RDS estuvo detenida, esperar a que vuelva a **Available** y comprobar el target del ALB antes de probar la aplicación.

## MUY IMPORTANTE: renovar credenciales temporales de AWS Academy

El backend y el worker de SNS/SQS pueden usar credenciales temporales de AWS Academy configuradas en `/etc/skates/backend.env`. Estas credenciales caducan cuando expira o se detiene la sesión del laboratorio; no tienen una duración fija garantizada. Si caducan, la tienda puede seguir funcionando, pero las operaciones de mensajería con AWS fallarán hasta renovar las credenciales.

1. Iniciar el Learner Lab y, en **AWS Details → AWS CLI → Show**, copiar los tres valores nuevos: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` y `AWS_SESSION_TOKEN`.

2. Conectar por SSH a la EC2 con:

```powershell
ssh -i "$env:USERPROFILE\Downloads\skates-backend-ec2.pem" ec2-user@<IPV4_PUBLICA_EC2>
```

3. En la terminal SSH de la EC2, abrir el archivo de entorno:

   ```bash
   sudo nano /etc/skates/backend.env
   ```

4. Actualizar únicamente los valores de `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` y `AWS_SESSION_TOKEN`. No reemplazar el resto del archivo. Guardar con **Ctrl+O**, pulsar **Enter** y salir con **Ctrl+X**.

5. Asegurar que los permisos del archivo sean restrictivos:

   ```bash
   sudo chmod 600 /etc/skates/backend.env
   ```

6. Reiniciar ambos servicios para que lean las credenciales renovadas:

   ```bash
   sudo systemctl restart skates-backend
   sudo systemctl restart skates-worker
   sudo systemctl status skates-backend skates-worker --no-pager
   ```

Confirmar que ambos servicios estén `active (running)` y probar **Mensajería SNS/SQS → Actualizar** en el panel admin. No incluir estas credenciales en chats, capturas, repositorios ni otros archivos versionados. Si AWS Academy deniega una acción por permisos, renovar las credenciales no elimina esa restricción.

### Si la EC2 está Running pero la aplicación no responde

Conectar por SSH a la EC2 y consultar el servicio:

```bash
sudo systemctl status skates-backend --no-pager
sudo journalctl -u skates-backend -n 100 --no-pager
```

Si está detenido o fallido, reiniciar el servicio y volver a revisar el estado:

```bash
sudo systemctl restart skates-backend
sudo systemctl status skates-backend --no-pager
```

Comprobar localmente desde la EC2:

```bash
curl -i http://127.0.0.1:8080/api/skate
```

Sin un token, `401 Unauthorized` es normal y confirma que el backend responde. Si devuelve `HTTP 000`, revisar `systemctl status`, los registros, que RDS esté **Available** y la conectividad al puerto 3306. No iniciar otra copia con `java -jar`: el servicio es quien administra el proceso.

Si cambia la contraseña RDS de `skates_app`, actualizar de forma segura `DB_PASSWORD` en `/etc/skates/backend.env` en la EC2 y reiniciar `skates-backend`. Ese archivo contiene secretos: no imprimirlo, compartirlo ni subirlo a Git. No guardar contraseñas, tokens o claves privadas en este README.

## Preparación para AWS Amplify Hosting

El archivo `amplify.yml` de la raíz define dos aplicaciones independientes para este monorepo:

- `Skates-tienda_Sanchez_Cerda`: tienda de clientes.
- `Skates-tienda-admin_Sanchez_Cerda`: panel de administración.

En Amplify Hosting, conectar el repositorio GitHub dos veces, una aplicación por carpeta. Configurar `AMPLIFY_MONOREPO_APP_ROOT` con la ruta exacta correspondiente en cada aplicación. El build usa Node.js 22 y `npm ci`.

Ambas aplicaciones Amplify deben tener `VITE_API_URL` configurada con el invoke URL HTTPS de API Gateway, sin una barra final. El valor actual es `https://gseiqgc6m6.execute-api.us-east-1.amazonaws.com`. No usar `localhost` en Amplify.

Los dominios Amplify actuales son `https://main.d1o3gk7dbea0nw.amplifyapp.com` para la tienda y `https://main.d3sasx7zpqdlhj.amplifyapp.com` para administración. Deben coincidir con las callback y sign-out URLs de sus respectivos App Clients de Cognito y con los orígenes CORS permitidos por el backend. La configuración del frontend usa el origen actual como callback si no se define `VITE_COGNITO_REDIRECT_URI` o `VITE_COGNITO_LOGOUT_URI`.
