import type {
  AuthTokens,
  CaseWorkerCredentials,
  CompanyCredentials,
  CurrentCompanyResponse,
} from "../context/auth.types";

const API_BASE = "/api/v1";

/**
 * The backend carries the Spring-security role name ("CASE_WORKER") while the
 * frontend models roles as "COMPANY" | "CASEWORKER". Normalize at the API
 * boundary so no caller has to remember the backend spelling.
 */
type RawAuthTokens = Omit<AuthTokens, "role"> & {
  role: "COMPANY" | "CASE_WORKER";
};

const toAuthTokens = (raw: RawAuthTokens): AuthTokens => ({
  ...raw,
  role: raw.role === "CASE_WORKER" ? "CASEWORKER" : "COMPANY",
});

const parseResponse = async <T>(
  response: Response,
  errorMessage: string,
): Promise<T> => {
  if (!response.ok) {
    throw new Error(errorMessage);
  }

  return response.json() as Promise<T>;
};

export const authApi = {
  async loginCompany(
    credentials: CompanyCredentials,
  ): Promise<AuthTokens> {
    const response = await fetch(`${API_BASE}/auth/login/company`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(credentials),
    });

    return toAuthTokens(
      await parseResponse<RawAuthTokens>(
        response,
        "Inloggning misslyckades. Kontrollera organisationsnumret.",
      ),
    );
  },

  async loginCaseWorker(
    credentials: CaseWorkerCredentials,
  ): Promise<AuthTokens> {
    const response = await fetch(`${API_BASE}/auth/login/caseWorker`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(credentials),
    });

    return toAuthTokens(
      await parseResponse<RawAuthTokens>(
        response,
        "Felaktig e-postadress eller lösenord.",
      ),
    );
  },

  async refresh(refreshToken: string): Promise<AuthTokens> {
    const response = await fetch(`${API_BASE}/auth/refresh`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        refreshToken,
      }),
    });

    return toAuthTokens(
      await parseResponse<RawAuthTokens>(
        response,
        "Sessionen har gått ut.",
      ),
    );
  },

  async getCurrentCompany(
    accessToken: string,
  ): Promise<CurrentCompanyResponse> {
    const response = await fetch(`${API_BASE}/companies/me`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });

    return parseResponse<CurrentCompanyResponse>(
      response,
      "Kunde inte hämta företagsinformationen.",
    );
  },

  async logout(accessToken: string): Promise<void> {
    const response = await fetch(`${API_BASE}/auth/logout`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });

    if (!response.ok && response.status !== 401) {
      throw new Error("Utloggning misslyckades.");
    }
  },
};