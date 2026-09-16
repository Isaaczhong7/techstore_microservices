import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const host = (name: string, fallback: string) => process.env[name] || fallback;

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api/products": {
        target: host("PRODUCT_SERVICE_URL", "http://localhost:8081"),
        changeOrigin: true
      },
      "/api/inventory": {
        target: host("INVENTORY_SERVICE_URL", "http://localhost:8082"),
        changeOrigin: true
      },
      "/api/orders": {
        target: host("ORDER_SERVICE_URL", "http://localhost:8080"),
        changeOrigin: true
      },
      "/api/lookup": {
        target: host("LOOKUP_SERVICE_URL", "http://localhost:8084"),
        changeOrigin: true
      }
    }
  }
});
