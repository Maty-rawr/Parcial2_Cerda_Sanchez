import { config } from "./config";

export interface Skate {
  id: number;
  modelo: string;
  marca: string;
  medida: number | null;
  wheelbase: number | null;
  stock: number;
  precio?: number;
}

export interface Usuario {
  username: string;
  nombre?: string;
  email?: string;
}

export interface TransaccionWebpay {
  token: string;
  url: string;
}

export async function apiFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const token = localStorage.getItem("skates-token");

  return fetch(`${config.apiUrl}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });
}

export async function obtenerCatalogo(): Promise<Skate[]> {
  const res = await apiFetch("/api/skate");
  if (!res.ok) {
    throw new Error(`El backend respondió ${res.status} al pedir el catálogo`);
  }
  return res.json();
}

export async function obtenerPerfil(): Promise<Usuario> {
  const res = await apiFetch("/api/auth/me");
  if (!res.ok) {
    throw new Error("No se pudo cargar el perfil del usuario");
  }
  return res.json();
}

export async function crearTransaccionWebpay(
  items: Skate[],
  total: number,
  usuario: Usuario,
): Promise<TransaccionWebpay> {
  const res = await apiFetch("/api/payments/webpay/create", {
    method: "POST",
    body: JSON.stringify({
      customer: usuario,
      items: items.map((item) => ({ id: item.id, quantity: 1 })),
      total,
      returnUrl: `${window.location.origin}/pago/resultado`,
    }),
  });

  if (!res.ok) {
    const detail = await res.text();
    let message = detail;

    try {
      const payload = JSON.parse(detail) as { message?: string; error?: string; error_message?: string };
      message = payload.message || payload.error || payload.error_message || detail;
    } catch {
      // La API puede responder texto plano en caso de error.
    }

    throw new Error(`Webpay respondió ${res.status}: ${message || "sin detalle"}`);
  }

  const transaction = await res.json() as Partial<TransaccionWebpay>;
  if (!transaction.token || !transaction.url) {
    throw new Error("La API de Webpay no devolvió token ni URL de pago");
  }

  return transaction as TransaccionWebpay;
}