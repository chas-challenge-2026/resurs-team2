import type {
  AuthTokens,
  CaseWorkerCredentials,
  CompanyCredentials,
  CurrentCompanyResponse,
} from "../context/auth.types";

const API_BASE = "/api/v1";

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

    return parseResponse<AuthTokens>(
      response,
      "Inloggning misslyckades. Kontrollera organisationsnumret.",
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

    return parseResponse<AuthTokens>(
      response,
      "Felaktig e-postadress eller lösenord.",
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

    return parseResponse<AuthTokens>(
      response,
      "Sessionen har gått ut.",
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