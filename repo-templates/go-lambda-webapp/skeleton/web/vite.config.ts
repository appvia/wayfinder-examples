import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Point the app at a different API root with VITE_API_BASE, e.g.
//   VITE_API_BASE=https://orders-dev.example.com/api npm run dev
// It defaults to /api, which is what CloudFront serves the deployed app on.
export default defineConfig({
  plugins: [react()],
});
