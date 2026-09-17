import { refreshTokens } from "./tokenRefresher";
import { tokenStorage } from "./tokenStorage";

export async function apiFetch(
  url: string,
  options: RequestInit = {},
): Promise<Response> {
  return requestWithAuth(url, options, false);
}

/**
 * Fetch with automatic session rotation: a 401 (expired access token) triggers
 * a single-flight refresh of the session and a single retry with the rotated
 * access token. A retried request that 401s again is NOT retried a second time,
 * and a failed refresh rejects (the refresher already cleared the session and
 * notified AuthProvider, which logs the user out).
 */
async function requestWithAuth(
  url: string,
  options: RequestInit,
  alreadyRetried: boolean,
): Promise<Response> {
  const accessToken = tokenStorage.getAccessToken();

  const headers = new Headers(options.headers);

  if (accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }

  const response = await fetch(url, {
    ...options,
    headers,
  });

  if (response.status === 401 && !alreadyRetried) {
    const refreshed = await refreshTokens();

    if (refreshed) {
      // The tokens were rotated — retry once with the fresh access token.
      return requestWithAuth(url, options, true);
    }

    // Session is over: refreshTokens cleared the tokens and notified the
    // session-expired listeners, so the caller will be logged out.
    throw new Error("Sessionen har gått ut.");
  }

  return response;
}