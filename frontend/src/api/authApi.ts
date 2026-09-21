import type {
  CaseWorkerCredentials,
  CompanyCredentials,
  CurrentCompanyResponse,
  PrincipalResponse,
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
  ): Promise<PrincipalResponse> {
    const response = await fetch(`${API_BASE}/auth/login/company`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(credentials),
    });

    return parseResponse<PrincipalResponse>(
      response,
      "Inloggning misslyckades. Kontrollera organisationsnumret.",
    );
  },

  async loginCaseWorker(
    credentials: CaseWorkerCredentials,
  ): Promise<PrincipalResponse> {
    const response = await fetch(`${API_BASE}/auth/login/caseWorker`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(credentials),
    });

    return parseResponse<PrincipalResponse>(
      response,
      "Felaktig e-postadress eller lösenord",
    );
  },

  async me(): Promise<PrincipalResponse> {
    const response = await fetch(`${API_BASE}/auth/me`);

    return parseResponse<PrincipalResponse>(
      response,
      "Ingen aktiv session.",
    );
  },

  async getCurrentCompany(): Promise<CurrentCompanyResponse> {
    const response = await fetch(`${API_BASE}/companies/me`);

    return parseResponse<CurrentCompanyResponse>(
      response,
      "Kunde inte hämta företagsinformationen.",
    );
  },

  async logout(): Promise<void> {
    const response = await fetch(`${API_BASE}/auth/logout`, {
      method: "POST",
    });
    if (!response.ok && response.status !== 401) {
      throw new Error("Utloggning misslyckades.");
    }
  },
};