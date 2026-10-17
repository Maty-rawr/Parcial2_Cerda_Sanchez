import react from "@vitejs/plugin-react";
import basicSsl from "@vitejs/plugin-basic-ssl";
import { defineConfig } from "vite";

export default defineConfig(({ mode }) => {
  const ngrokHost = mode === "ngrok" ? process.env.NGROK_HOST : undefined;
  if (mode === "ngrok" && !ngrokHost) {
    throw new Error("Set NGROK_HOST to the exact public ngrok hostname.");
  }

  return {
    base: mode === "ngrok" ? "/admin/" : "/",
    plugins: [react(), ...(mode === "ngrok" ? [] : [basicSsl()])],
    server: {
      port: 5174,
      ...(ngrokHost ? { allowedHosts: [ngrokHost] } : {}),
    },
  };
});