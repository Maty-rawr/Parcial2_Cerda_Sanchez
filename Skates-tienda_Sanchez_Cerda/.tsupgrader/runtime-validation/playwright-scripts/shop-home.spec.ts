import { test, expect } from '@playwright/test';

test.use({ channel: 'msedge' });

test('shop-home', async ({ page }) => {
  await page.route("**/*", async route => {
    const requestUrl = new URL(route.request().url());
    if (requestUrl.origin === "http://127.0.0.1:5173") {
      await route.continue();
    } else {
      await route.abort("blockedbyclient");
    }
  });
  await page.goto('http://127.0.0.1:5173');
  await page.getByRole('button', { name: 'Todos' }).click();
  await expect(page.getByText('Urban StoreSkate — Tienda0DisponiblesIniciar Sesión+Carrito0NUEVA COLECCIÓN')).toHaveText("Urban StoreSkate — Tienda0DisponiblesIniciar Sesión+Carrito0NUEVA COLECCIÓN 2026DOMINA EL ASFALTO.Diseño, resistencia y máxima pop. Selecciona tu estilo de tabla ideal y arma tu setup perfecto.Madera de alta resistenciaPop consistenteSetup listo para calleINVENTARIO EN VIVOCatálogo de TablasTodosInicia sesión para consultar el catálogo protegido.Skate Shop — Todos los derechos reservados © 2026");
});
