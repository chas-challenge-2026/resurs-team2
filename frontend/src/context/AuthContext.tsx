import { createContext } from "react";

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

export interface AuthContextType {
  user: User | null;
  isLoggedIn: boolean;
  isLoading: boolean;
  loginCompany: (credentials: CompanyCredentials) => Promise<void>;
  loginCaseWorker: (credentials: CaseWorkerCredentials) => Promise<void>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextType | undefined>(
  undefined,
);