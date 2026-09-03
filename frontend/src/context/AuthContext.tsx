import { createContext } from "react";

export type Role = "COMPANY" | "CASEWORKER" | "ADMIN";

export interface User {
  id: string;
  name: string;
  email: string;
  role: Role;
}

export interface AuthContextType {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (userData: User) => void;
  logout: () => void;
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined);