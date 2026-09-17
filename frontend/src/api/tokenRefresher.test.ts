import { authApi } from "./authApi";
import { onSessionExpired, refreshTokens } from "./tokenRefresher";
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

describe("refreshTokens single-flight", () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.mocked(authApi.refresh).mockReset();
  });

  it("presents the single-use refresh token exactly once for concurrent callers", async () => {
    tokenStorage.setTokens("access-1", "refresh-1");
    vi.mocked(authApi.refresh).mockResolvedValueOnce(rotatedTokens);

    // Mirrors what happens on page reload with StrictMode (the restore effect
    // runs twice) and/or a 401 interceptor racing the restore: any number of
    // concurrent consumers must share ONE presentation of the refresh token.
    const [first, second, third] = await Promise.all([
      refreshTokens(),
      refreshTokens(),
      refreshTokens(),
    ]);

    expect(authApi.refresh).toHaveBeenCalledTimes(1);
    expect(first).toEqual(rotatedTokens);
    expect(second).toEqual(rotatedTokens);
    expect(third).toEqual(rotatedTokens);

    // The rotated pair was stored for subsequent requests.
    expect(tokenStorage.getAccessToken()).toBe("access-2");
    expect(tokenStorage.getRefreshToken()).toBe("refresh-2");
  });

  it("returns null and clears the session once when the refresh fails", async () => {
    tokenStorage.setTokens("access-1", "refresh-1");
    vi.mocked(authApi.refresh).mockRejectedValueOnce(new Error("401"));

    const sessionExpired = vi.fn();
    const unsubscribe = onSessionExpired(sessionExpired);
    try {
      const [first, second] = await Promise.all([
        refreshTokens(),
        refreshTokens(),
      ]);

      expect(authApi.refresh).toHaveBeenCalledTimes(1);
      expect(first).toBeNull();
      expect(second).toBeNull();
      expect(tokenStorage.getAccessToken()).toBeNull();
      expect(tokenStorage.getRefreshToken()).toBeNull();
      expect(sessionExpired).toHaveBeenCalledTimes(1);
    } finally {
      unsubscribe();
    }
  });

  it("returns null without calling the backend when there is no refresh token", async () => {
    const [result] = await Promise.all([refreshTokens(), refreshTokens()]);

    expect(authApi.refresh).not.toHaveBeenCalled();
    expect(result).toBeNull();
  });
});