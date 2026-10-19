# Skates-Tienda

## Autenticacion con Amazon Cognito

La tienda usa AWS Amplify Auth con Cognito Hosted UI y Authorization Code + PKCE. Copia `.env.example` a `.env.local`; configura el User Pool y el App Client `7vge2gthmumgohfn84aepg41qh` para permitir Authorization Code y los scopes `openid`, `email`, `skate-api/read` y `skate-api/purchase`. No concedas a la tienda `skate-api/write`, que se reserva para administración del catálogo. La URL local de callback y cierre de sesión es:

- `https://localhost:5173/`

La configuración inicial de `.env.example` usa el User Pool y el App Client indicados para este entorno. Vite sirve la tienda por HTTPS en desarrollo con un certificado local autofirmado; al abrirla por primera vez, acepta el aviso del navegador. No configures un client secret en la aplicación del navegador. El catálogo requiere `skate-api/read` y las operaciones Webpay requieren `skate-api/purchase`. Para producción, registra las URLs HTTPS correspondientes en Cognito y define `VITE_COGNITO_REDIRECT_URI` y `VITE_COGNITO_LOGOUT_URI`.

### Publicar tienda y API para pruebas con ngrok

Inicia Spring Boot en el puerto `8080`, el panel admin en modo ngrok (puerto `5174`, ver su README) y solo el túnel `tienda` definido en el `infra/ngrok.yml` del backend. Vite reenvía `/api/**` al backend local y `/admin/**` al panel admin; así tienda, panel y API quedan bajo un solo origen HTTPS sin necesitar otros dominios ngrok. En PowerShell, desde esta carpeta, inicia Vite así:

```powershell
$env:NGROK_HOST = "<hostname-ngrok-sin-https>"
$env:VITE_COGNITO_REDIRECT_URI = "https://<hostname-ngrok>/"
$env:VITE_COGNITO_LOGOUT_URI = "https://<hostname-ngrok>/"
npm run dev -- --host 127.0.0.1 --mode ngrok
```

`NGROK_HOST` permite solo ese hostname exacto en Vite. En modo `ngrok`, el frontend usa rutas relativas `/api` incluso si `VITE_API_URL` esta definido en `.env.local`; el proxy las envia a `http://127.0.0.1:8080`. Registra las URLs HTTPS indicadas como callback y logout URLs del App Client en Cognito; para admin registra tambien `https://<hostname-ngrok>/admin/` en su App Client. `/admin/` se envia al Vite admin. API y admin no obtienen hostnames independientes y solo estan disponibles mientras Spring Boot, ambos servidores Vite y ngrok sigan ejecutandose en tu equipo. Este modo es para pruebas, no para produccion.

El panel de administración usa un App Client independiente. Registra su URL de retorno `https://localhost:5174/` en ese cliente; Vite sirve el panel por HTTPS en desarrollo.

## Tecnologias utilizadas

* **React**: Biblioteca de JavaScript para construir interfaces de usuario.
* **TypeScript**: Superconjunto de JavaScript que aporta tipado estatico.
* **Vite**: Entorno de desarrollo rapido y empaquetador de modulos.
* **CSS**: Diseno adaptable e interfaz limpia.

---

## Estructura del Proyecto

```text
Skates-tienda-main/
├── public/            # Archivos publicos (iconos, favicon)
├── src/               # Codigo fuente de la aplicacion
│   ├── assets/        # Imagenes y recursos graficos (hero, logos)
│   ├── App.css        # Estilos principales del componente App
│   ├── App.tsx        # Componente principal de la aplicacion
│   ├── api.ts         # Conexion y peticiones a la API
│   ├── config.ts      # Variables de configuracion global
│   ├── index.css      # Estilos globales
│   └── main.tsx       # Punto de entrada de React
├── package.json       # Dependencias y scripts del proyecto
└── vite.config.ts     # Configuracion de Vite