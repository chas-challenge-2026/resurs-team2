import { apiFetch } from "./apiFetch";
import { authApi } from "./authApi";
import { onSessionExpired } from "./tokenRefresher";
import { tokenStorage } from "./tokenStorage";
import type { AuthTokens } from "../context/auth.types";

vi.mock("./authApi", () => ({
  authApi: { refresh: vi.fn() },
}));

const rotatedTokens: AuthTokens = {
  accessToken: "access-2",
  refreshToken: "refresh-2",
  role: "COMPANY",
  name: "Företag",
};

const json = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

const unauthorized = (): Response => new Response(null, { status: 401 });

describe("apiFetch automatic rotation", () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.unstubAllGlobals();
    vi.mocked(authApi.refresh).mockReset();
  });

  it("single-flights refresh when several requests 401 concurrently", async () => {
    tokenStorage.setTokens("access-1", "refresh-1");
    vi.mocked(authApi.refresh).mockResolvedValueOnce(rotatedTokens);

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(unauthorized())
      .mockResolvedValueOnce(unauthorized())
      .mockResolvedValueOnce(json({ ok: true }))
      .mockResolvedValueOnce(json({ ok: true }));
    vi.stubGlobal("fetch", fetchMock);

    // Two requests 401 at the same time. The single-use refresh token must be
    // presented EXACTLY once; both requests are then retried with the rotated
    // access token instead of one losing the race.
    const [first, second] = await Promise.all([
      apiFetch("/api/v1/applications"),
      apiFetch("/api/v1/applications/audit"),
    ]);

    expect(authApi.refresh).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(first.status).toBe(200);
    expect(second.status).toBe(200);

    // Both retried requests carried the rotated access token.
    const retryHeadersA = new Headers(fetchMock.mock.calls[2][1].headers);
    const retryHeadersB = new Headers(fetchMock.mock.calls[3][1].headers);
    expect(retryHeadersA.get("Authorization")).toBe("Bearer access-2");
    expect(retryHeadersB.get("Authorization")).toBe("Bearer access-2");

    // The rotated pair is now what future requests will use.
    expect(tokenStorage.getAccessToken()).toBe("access-2");
    expect(tokenStorage.getRefreshToken()).toBe("refresh-2");
  });

  it("retries a 401 request with the rotated access token", async () => {
    tokenStorage.setTokens("access-1", "refresh-1");
    vi.mocked(authApi.refresh).mockResolvedValueOnce(rotatedTokens);

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(unauthorized())
      .mockResolvedValueOnce(json({ id: 42 }));
    vi.stubGlobal("fetch", fetchMock);

    const response = await apiFetch("/api/v1/applications");

    expect(authApi.refresh).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(2);

    const retryHeaders = new Headers(fetchMock.mock.calls[1][1].headers);
    expect(retryHeaders.get("Authorization")).toBe("Bearer access-2");
    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({ id: 42 });
  });

  it("clears the session, notifies listeners and does not retry when refresh fails", async () => {
    tokenStorage.setTokens("access-1", "refresh-1");
    vi.mocked(authApi.refresh).mockRejectedValueOnce(new Error("401"));

    const sessionExpired = vi.fn();
    const unsubscribe = onSessionExpired(sessionExpired);
    try {
      const fetchMock = vi.fn().mockResolvedValueOnce(unauthorized());
      vi.stubGlobal("fetch", fetchMock);

      await expect(apiFetch("/api/v1/applications")).rejects.toThrow(
        "Sessionen har gått ut.",
      );

      // No retry loop: the original request was attempted exactly once.
      expect(fetchMock).toHaveBeenCalledTimes(1);
      expect(authApi.refresh).toHaveBeenCalledTimes(1);
      // The refresher cleared the tokens and told AuthProvider to log out.
      expect(tokenStorage.getAccessToken()).toBeNull();
      expect(tokenStorage.getRefreshToken()).toBeNull();
      expect(sessionExpired).toHaveBeenCalledTimes(1);
    } finally {
      unsubscribe();
    }
  });

  it("does not retry a second time when the retried request 401s again", async () => {
    tokenStorage.setTokens("access-1", "refresh-1");
    vi.mocked(authApi.refresh).mockResolvedValueOnce(rotatedTokens);

    // Original request 401s, the retried request 401s again — no third attempt.
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(unauthorized())
      .mockResolvedValueOnce(unauthorized());
    vi.stubGlobal("fetch", fetchMock);

    const response = await apiFetch("/api/v1/applications");

    expect(authApi.refresh).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(response.status).toBe(401);
  });

  it("does not touch the refresh token when the request succeeds", async () => {
    tokenStorage.setTokens("access-1", "refresh-1");

    const fetchMock = vi.fn().mockResolvedValueOnce(json({ ok: true }));
    vi.stubGlobal("fetch", fetchMock);

    const response = await apiFetch("/api/v1/applications");

    expect(authApi.refresh).not.toHaveBeenCalled();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(response.status).toBe(200);
  });
});