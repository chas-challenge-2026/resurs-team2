import { authApi } from "./authApi";
import { tokenStorage } from "./tokenStorage";

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
let inFlight: Promise<boolean> | null = null;

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
 * Rotate the session with the stored refresh token, single-flight.
 *
 * @returns `true` if rotation succeeded (new tokens are stored), `false` if the
 *          session is over (tokens cleared, session-expired listeners fired).
 */
export function refreshTokens(): Promise<boolean> {
  const existing = inFlight;
  if (existing !== null) {
    return existing;
  }

  const refreshToken = tokenStorage.getRefreshToken();
  if (!refreshToken) {
    // No session to rotate — treat as expired so callers reject cleanly.
    tokenStorage.clearTokens();
    notifySessionExpired();
    return Promise.resolve(false);
  }

  inFlight = authApi
    .refresh(refreshToken)
    .then((tokens) => {
      tokenStorage.setTokens(tokens.accessToken, tokens.refreshToken);
      return true;
    })
    .catch(() => {
      tokenStorage.clearTokens();
      notifySessionExpired();
      return false;
    })
    .finally(() => {
      inFlight = null;
    });

  return inFlight;
}