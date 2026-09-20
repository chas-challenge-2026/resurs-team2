// Infrastructure smoke test: proves the vitest + jsdom setup is wired up.
// - `describe`/`it`/`expect` are used as globals (vitest.config.ts `globals: true`).
// - `window` must exist, confirming the jsdom environment (not node) is active.
// - the "@" alias must resolve to src the same way it does in vite.config.ts.
import { apiFetch } from "@/api/apiFetch";

describe("test infrastructure", () => {
  it("runs vitest with the jsdom environment", () => {
    expect(typeof window).toBe("object");
  });

  it("resolves the @ path alias to src", async () => {
    expect(apiFetch).toBeTypeOf("function");
  });
});