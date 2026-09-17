import { fileURLToPath, URL } from "node:url";
import { configDefaults, defineConfig } from "vitest/config";

// Unit-test configuration (vitest). Uses jsdom so tests can exercise code that
// touches window/sessionStorage (e.g. the API token modules), and mirrors the
// "@" path alias from vite.config.ts so tests import from src the same way.
export default defineConfig({
  test: {
    environment: "jsdom",
    globals: true,
    // Playwright e2e specs (*.spec.ts) live in e2e/ and have their own runner.
    exclude: [...configDefaults.exclude, "**/e2e/**"],
  },
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
});