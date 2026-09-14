export type Role = "COMPANY" | "CASEWORKER";

export interface User {
  id: string;
  name: string;
  email: string;
  role: Role;
}

export interface CompanyCredentials {
  orgNumber: string;
}

export interface CaseWorkerCredentials {
  email: string;
  password: string;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  role: Role;
  name: string;
}

export interface CurrentCompanyResponse {
  name: string;
  orgNumber: string;
}

export interface AuthContextType {
  user: User | null;
  isLoggedIn: boolean;
  isLoading: boolean;
  loginCompany: (credentials: CompanyCredentials) => Promise<void>;
  loginCaseWorker: (credentials: CaseWorkerCredentials) => Promise<void>;
  logout: () => Promise<void>;
}