# Skates Admin Panel

Panel de administración moderno y minimalista desarrollado para la gestión de inventario, productos y órdenes de la tienda de skate. 
---

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