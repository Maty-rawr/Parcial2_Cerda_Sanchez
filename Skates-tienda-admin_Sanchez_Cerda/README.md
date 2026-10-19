# Skates Admin Panel

Panel de administración moderno y minimalista desarrollado para la gestión de inventario, productos y órdenes de la tienda de skate. 
---

## Autenticación con Amazon Cognito

El panel usa AWS Amplify Auth con Cognito Hosted UI y Authorization Code + PKCE. Copia `.env.example` a `.env.local`; usa el App Client `2ndve0vjmlau9as43sapcmermb`, habilita Authorization Code y los scopes `openid`, `email`, `skate-api/read` y `skate-api/write`. Registra la URL local o pública como callback y logout URL en Cognito. Vite sirve el panel por HTTPS en desarrollo con un certificado local autofirmado; al abrirlo por primera vez, acepta el aviso del navegador. Las cuentas de administración también deben pertenecer al grupo Cognito `ADMIN`; el backend aplica tanto el scope como el grupo, sin depender solo de la comprobación de UI.

### Publicar el panel admin bajo el dominio de la tienda

Para probarlo con el unico dominio ngrok gratuito, inicia el panel en modo ngrok en el puerto `5174`. El proxy de la tienda publica el panel en `https://<dominio-ngrok>/admin/` y reenvia sus llamadas `/api` al backend local. No inicies un segundo tunel ngrok con el mismo hostname.

```powershell
$env:NGROK_HOST = "<hostname-ngrok-sin-https>"
$env:VITE_COGNITO_REDIRECT_URI = "https://<dominio-ngrok>/admin/"
$env:VITE_COGNITO_LOGOUT_URI = "https://<dominio-ngrok>/admin/"
$env:VITE_API_URL = ""
npm run dev -- --host 127.0.0.1 --mode ngrok
```

Agrega exactamente `https://<dominio-ngrok>/admin/` como callback y logout URL del App Client de admin. En modo `ngrok`, el panel usa `/api` en el mismo origen aunque `.env.local` contenga un `VITE_API_URL` local. Esta modalidad de prueba requiere que Spring Boot, ambos servidores Vite y el tunel ngrok de la tienda sigan ejecutandose en el mismo equipo.

La tienda conserva su App Client propio (`7vge2gthmumgohfn84aepg41qh`). El backend debe aceptar ambos IDs en `COGNITO_CLIENT_IDS` y permitir `https://localhost:5173` y `https://localhost:5174` como orígenes CORS. No configures un client secret en el navegador.

## Tecnologías Utilizadas

* **React** (con Vite) para un entorno de desarrollo rápido y eficiente.
* **TypeScript** para un tipado estático robusto.

---

## Características Principales

* **Gestión de Inventario (Productos):** 
  * Visualización y control de patinetas, repuestos y accesorios disponibles en la tienda.
  * Estructura lista para la integración de altas, bajas y modificaciones en tiempo real.
* **Panel de Órdenes y Pedidos:** 
  * Seguimiento del estado de las compras realizadas por los clientes en la plataforma principal.
* **Interfaz Full-Screen Inmersiva:** 
  * Diseño adaptado a pantalla completa con scroll optimizado y componentes modulares.

---

## Estructura del Proyecto

Skates-admin/
├── public/             # Recursos estáticos e iconos
├── src/
│   ├── App.tsx         # Componente principal con el layout y pestañas de administración
│   ├── App.css         # Estilos específicos del panel
│   ├── api.ts          # Módulo de comunicación con el backend (Axios/Fetch)
│   ├── config.ts       # Configuración global y variables del sistema
│   └── index.css       # Estilos globales, reseteo y variables de color
├── index.html          # Punto de entrada HTML
├── package.json        # Dependencias y scripts del proyecto
└── vite.config.ts      # Configuración del empaquetador Vite