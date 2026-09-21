import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

import { authApi } from "../api/authApi";
import { AuthContext } from "./AuthContext";

import type {
  AuthContextType,
  CaseWorkerCredentials,
  CompanyCredentials,
  User,
} from "./auth.types";

interface AuthProviderProps {
  children: ReactNode;
}

export const AuthProvider = ({ children }: AuthProviderProps) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const clearSession = useCallback(() => {
    setUser(null);
  }, []);

  useEffect(() => {
    const restoreSession = async () => {
      try {
        const principal = await authApi.me();

        if (principal.role === "COMPANY") {
          const company = await authApi.getCurrentCompany();

          setUser({
            id: company.orgNumber,
            name: company.name || principal.name || "Företag",
            email: "",
            role: "COMPANY",
          });

          return;
        }

        if (principal.role === "CASEWORKER") {
          setUser({
            id: "caseworker",
            name: principal.name || "Handläggare",
            email: "",
            role: "CASEWORKER",
          });

          return;
        }

        clearSession();
      } catch (error) {
        console.error("Kunde inte återställa sessionen:", error);
        clearSession();
      } finally {
        setIsLoading(false);
      }
    };

    void restoreSession();
  }, [clearSession]);

  const loginCompany = useCallback(
    async (credentials: CompanyCredentials) => {
      setIsLoading(true);

      try {
        const principal = await authApi.loginCompany(credentials);
        const company = await authApi.getCurrentCompany();

        setUser({
          id: company.orgNumber || credentials.orgNumber,
          name: company.name || principal.name || "Företag",
          email: "",
          role: "COMPANY",
        });
      } catch (error) {
        clearSession();
        throw error;
      } finally {
        setIsLoading(false);
      }
    },
    [clearSession],
  );

  const loginCaseWorker = useCallback(
    async (credentials: CaseWorkerCredentials) => {
      setIsLoading(true);

      try {
        const principal = await authApi.loginCaseWorker(credentials);

        setUser({
          id: credentials.email,
          name: principal.name || credentials.email,
          email: credentials.email,
          role: "CASEWORKER",
        });
      } catch (error) {
        clearSession();
        throw error;
      } finally {
        setIsLoading(false);
      }
    },
    [clearSession],
  );

  const logout = useCallback(async () => {
    setIsLoading(true);

    try {
      await authApi.logout();
    } catch (error) {
      console.error("Fel vid utloggning:", error);
    } finally {
      clearSession();
      setIsLoading(false);
    }
  }, [clearSession]);

  const value = useMemo<AuthContextType>(
    () => ({
      user,
      isLoggedIn: user !== null,
      isLoading,
      loginCompany,
      loginCaseWorker,
      logout,
    }),
    [user, isLoading, loginCompany, loginCaseWorker, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
