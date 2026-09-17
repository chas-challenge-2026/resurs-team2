import { authApi } from "./authApi";
import { tokenStorage } from "./tokenStorage";
import type { AuthTokens } from "../context/auth.types";

type SessionExpiredListener = () => void;

/**
 * Listeners notified when the session can no longer be refreshed (the refresh
 * token was rejected/exhausted). AuthProvider uses this to log the user out;
 * ProtectedRoute then redirects because `user` becomes null.
 */
const sessionExpiredListeners = new Set<SessionExpiredListener>();

/**
 * In-flight refresh promise. The backend makes the refresh token single-use
 * (SessionTokenStore.rotate), so a burst of concurrent 401s must present it
 * EXACTLY once: the first caller creates the promise, every other caller awaits
 * the same one (single-flight). Reset to null once settled so the next expiry
 * starts a fresh rotation.
 */
let inFlight: Promise<AuthTokens | null> | null = null;

/** Register a listener called when the session expires. Returns an unsubscribe. */
export function onSessionExpired(listener: SessionExpiredListener): () => void {
  sessionExpiredListeners.add(listener);
  return () => {
    sessionExpiredListeners.delete(listener);
  };
}

function notifySessionExpired(): void {
  for (const listener of sessionExpiredListeners) {
    try {
      listener();
    } catch {
      // A listener must never break the refresh flow for other callers.
    }
  }
}

/**
 * Rotate the session with the stored refresh token, single-flight. Every caller
 * (the 401 retry interceptor AND the AuthProvider session restore) goes through
 * this one function, so the single-use refresh token is never presented more
 * than once — even when restore and a 401 race, or StrictMode double-invokes
 * the restore effect.
 *
 * @returns the rotated tokens (already stored) on success, or `null` if the
 *          session is over (tokens cleared, session-expired listeners fired).
 */
export function refreshTokens(): Promise<AuthTokens | null> {
  const existing = inFlight;
  if (existing !== null) {
    return existing;
  }

  const refreshToken = tokenStorage.getRefreshToken();
  if (!refreshToken) {
    // No session to rotate — treat as expired so callers reject cleanly.
    tokenStorage.clearTokens();
    notifySessionExpired();
    return Promise.resolve(null);
  }

  inFlight = authApi
    .refresh(refreshToken)
    .then((tokens) => {
      tokenStorage.setTokens(tokens.accessToken, tokens.refreshToken);
      return tokens;
    })
    .catch(() => {
      tokenStorage.clearTokens();
      notifySessionExpired();
      return null;
    })
    .finally(() => {
      inFlight = null;
    });

  return inFlight;
}