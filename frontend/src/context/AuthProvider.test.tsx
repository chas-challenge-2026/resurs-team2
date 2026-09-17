import { StrictMode, useContext } from "react";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";

import { apiFetch } from "../api/apiFetch";
import { authApi } from "../api/authApi";
import { tokenStorage } from "../api/tokenStorage";
import { onSessionExpired } from "../api/tokenRefresher";
import { AuthProvider } from "./AuthProvider";
import { AuthContext } from "./AuthContext";
import type { AuthTokens } from "./auth.types";

vi.mock("../api/authApi", () => ({
  authApi: {
    refresh: vi.fn(),
    getCurrentCompany: vi.fn(),
    loginCompany: vi.fn(),
    loginCaseWorker: vi.fn(),
    logout: vi.fn(),
  },
}));

const rotated: AuthTokens = {
  accessToken: "access-2",
  refreshToken: "refresh-2",
  role: "COMPANY",
  name: "Malmö Fastigheter AB",
};

const companyResponse = {
  orgNumber: "556000-1234",
  name: "Malmö Fastigheter AB",
  authorizedSignatory: "Anders Karlsson",
};

function Probe() {
  const { user, isLoading } = useContext(AuthContext)!;

  return (
    <div>
      <span data-testid="user">{user?.name ?? "none"}</span>
      <span data-testid="loading">{isLoading ? "loading" : "loaded"}</span>
    </div>
  );
}

describe("AuthProvider session restore on page reload", () => {
  let container: HTMLDivElement;
  let root: Root;

  const renderApp = async () => {
    await act(async () => {
      root = createRoot(container);
      root.render(
        <StrictMode>
          <AuthProvider>
            <Probe />
          </AuthProvider>
        </StrictMode>,
      );
    });
  };

  const flush = async () => {
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
  };

  beforeEach(() => {
    // Without testing-library, opt into React 19's act() environment so React's
    // dev assertions (and StrictMode double-invocation) behave as in dev.
    (globalThis as Record<string, unknown>).IS_REACT_ACT_ENVIRONMENT = true;
    document.body.innerHTML = "";
    container = document.createElement("div");
    document.body.appendChild(container);
    sessionStorage.clear();
    vi.unstubAllGlobals();
    vi.mocked(authApi.refresh).mockReset();
    vi.mocked(authApi.getCurrentCompany).mockReset();
  });

  afterEach(() => {
    root?.unmount();
  });

  it("restores the session with exactly one refresh under StrictMode", async () => {
    // State after a previous login: an access/refresh pair is stored, and the
    // user reloads the page. StrictMode double-invokes the restore effect; the
    // single-flight refresher must present the single-use refresh token ONCE.
    tokenStorage.setTokens("access-1", "refresh-1");
    vi.mocked(authApi.refresh).mockResolvedValue(rotated);
    vi.mocked(authApi.getCurrentCompany).mockResolvedValue(companyResponse);

    const sessionExpired = vi.fn();
    const unsubscribe = onSessionExpired(sessionExpired);
    try {
      await renderApp();
      await flush();

      expect(authApi.refresh).toHaveBeenCalledTimes(1);
      expect(authApi.refresh).toHaveBeenCalledWith("refresh-1");

      // The rotated pair was stored for subsequent requests, the user was
      // restored, and the session was NOT expired (no logout).
      expect(tokenStorage.getAccessToken()).toBe("access-2");
      expect(tokenStorage.getRefreshToken()).toBe("refresh-2");
      expect(sessionExpired).not.toHaveBeenCalled();
      expect(container.textContent).toContain("Malmö Fastigheter AB");
      expect(container.textContent).toContain("loaded");
    } finally {
      unsubscribe();
    }
  });

  it("has restore and a concurrent 401 interceptor share one refresh", async () => {
    tokenStorage.setTokens("access-1", "refresh-1");
    vi.mocked(authApi.getCurrentCompany).mockResolvedValue(companyResponse);

    // The restore's refresh stays pending until we release it, so a request
    // sent with the stale pre-reload access token (401) joins the SAME flight.
    let releaseRefresh!: (tokens: AuthTokens) => void;
    vi.mocked(authApi.refresh).mockImplementationOnce(
      () =>
        new Promise<AuthTokens>((resolve) => {
          releaseRefresh = resolve;
        }),
    );

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValue(
        new Response(JSON.stringify({ ok: true }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      );
    vi.stubGlobal("fetch", fetchMock);

    await renderApp();

    // While the restore's refresh is still in flight, a request carrying the
    // OLD access token 401s and triggers the interceptor's refresh path.
    await act(async () => {
      void apiFetch("/api/v1/applications");
    });

    await act(async () => {
      releaseRefresh(rotated);
    });
    await flush();

    // The single-use token was presented exactly once for both consumers, and
    // the 401 request was retried with the rotated access token.
    expect(authApi.refresh).toHaveBeenCalledTimes(1);
    expect(authApi.refresh).toHaveBeenCalledWith("refresh-1");

    const retryHeaders = new Headers(fetchMock.mock.calls[1][1].headers);
    expect(retryHeaders.get("Authorization")).toBe("Bearer access-2");

    expect(tokenStorage.getAccessToken()).toBe("access-2");
    expect(tokenStorage.getRefreshToken()).toBe("refresh-2");
    expect(container.textContent).toContain("Malmö Fastigheter AB");
    expect(container.textContent).toContain("loaded");
  });
});