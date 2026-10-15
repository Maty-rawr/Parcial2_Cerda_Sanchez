import { config } from "./config";

export async function iniciarSesion(username: string, password: string): Promise<void> {
  const response = await fetch(`${config.apiUrl}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  if (!response.ok) throw new Error("Usuario o contraseña incorrectos");
  const data = await response.json();
  localStorage.setItem("skates-token", data.token);
}

export function cerrarSesion(): void {
  localStorage.removeItem("skates-token");
}