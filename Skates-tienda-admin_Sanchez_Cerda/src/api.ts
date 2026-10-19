import { config } from "./config";
import { obtenerAccessToken } from "./auth";

export interface Skate {
  id: number;
  modelo: string;
  marca: string;
  medida: number | null;
  wheelbase: number | null;
  stock: number;
}

export interface NuevoSkate {
  modelo: string;
  marca: string;
  medida?: number | null;
  wheelbase?: number | null;
  stock: number;
}

export async function apiFetch(path: string, options: RequestInit = {}): Promise<Response> {
  if (!config.apiUrl && import.meta.env.MODE !== "ngrok") {
    throw new Error("Configura VITE_API_URL con la URL de API Gateway para conectar con el backend.");
  }

  const token = await obtenerAccessToken();
  const headers = new Headers(options.headers);
  if (!headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  if (token) headers.set("Authorization", `Bearer ${token}`);

  return fetch(`${config.apiUrl}${path}`, {
    ...options,
    headers,
  });
}

export async function obtenerCatalogo(): Promise<Skate[]> {
  const res = await apiFetch("/api/skate");
  if (!res.ok) {
    throw new Error(`El backend respondió ${res.status} al pedir el inventario`);
  }
  return res.json();
}

export async function agregarSkate(datos: NuevoSkate): Promise<Skate> {
  const res = await apiFetch("/api/skate", {
    method: "POST",
    body: JSON.stringify(datos),
  });
  if (!res.ok) {
    const cuerpo = await res.json().catch(() => null);
    throw new Error(cuerpo?.mensaje ?? `El backend respondió ${res.status} al agregar el Skate`);
  }
  return res.json();
}

export async function actualizarSkate(id: number, datos: NuevoSkate): Promise<Skate> {
  const res = await apiFetch(`/api/skate/${id}`, {
    method: "PUT",
    body: JSON.stringify(datos),
  });
  if (!res.ok) {
    const cuerpo = await res.json().catch(() => null);
    throw new Error(cuerpo?.mensaje ?? `El backend respondió ${res.status} al actualizar el Skate`);
  }
  return res.json();
}

export async function eliminarSkate(id: number): Promise<void> {
  const res = await apiFetch(`/api/skate/${id}`, {
    method: "DELETE",
  });
  if (!res.ok) {
    const cuerpo = await res.json().catch(() => null);
    throw new Error(cuerpo?.mensaje ?? `El backend respondió ${res.status} al eliminar el Skate`);
  }
}