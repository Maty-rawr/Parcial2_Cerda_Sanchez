import react from "@vitejs/plugin-react";
import basicSsl from "@vitejs/plugin-basic-ssl";
import { defineConfig } from "vite";

export default defineConfig(({ mode }) => {
  const ngrokHost = mode === "ngrok" ? process.env.NGROK_HOST : undefined;
  if (mode === "ngrok" && !ngrokHost) {
    throw new Error("Set NGROK_HOST to the exact public ngrok hostname.");
  }

  return {
    plugins: [react(), ...(mode === "ngrok" ? [] : [basicSsl()])],
    server: {
      port: 5173,
      ...(ngrokHost ? { allowedHosts: [ngrokHost] } : {}),
      ...(mode === "ngrok"
        ? {
            proxy: {
              "/api": {
                target: "http://127.0.0.1:8080",
                changeOrigin: true,
              },
              "/admin": {
                target: "http://127.0.0.1:5174",
                changeOrigin: true,
              },
            },
          }
        : {}),
    },
  };
});