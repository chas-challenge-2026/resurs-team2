import React, { useEffect, useState } from "react";

import {
  AuthContext,
  type User,
  type CompanyCredentials,
  type CaseWorkerCredentials,
} from "./AuthContext";

import { authApi } from "../api/authApi";

const ACCESS_TOKEN_KEY = "accessToken";
const REFRESH_TOKEN_KEY = "refreshToken";

export const AuthProvider: React.FC<{
  children: React.ReactNode;
}> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);

  const [isLoading, setIsLoading] = useState<boolean>(true);

  const saveTokens = (accessToken: string, refreshToken: string) => {
    sessionStorage.setItem(ACCESS_TOKEN_KEY, accessToken);

    sessionStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  };

  const clearSession = () => {
    sessionStorage.removeItem(ACCESS_TOKEN_KEY);

    sessionStorage.removeItem(REFRESH_TOKEN_KEY);

    setUser(null);
  };

  useEffect(() => {
    const restoreSession = async () => {
      const refreshToken = sessionStorage.getItem(REFRESH_TOKEN_KEY);

      if (!refreshToken) {
        setIsLoading(false);
        return;
      }

      try {
        const tokens = await authApi.refresh(refreshToken);

        saveTokens(tokens.accessToken, tokens.refreshToken);

        if (tokens.role === "COMPANY") {
          const company = await authApi.getCurrentCompany(tokens.accessToken);

          setUser({
            id: company.orgNumber,
            name: company.name || tokens.name || "Företag",
            email: "",
            role: "COMPANY",
          });

          return;
        }

        if (tokens.role === "CASEWORKER") {
          setUser({
            id: "caseworker",
            name: tokens.name || "Handläggare",
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

    restoreSession();
  }, []);

  const loginCompany = async (credentials: CompanyCredentials) => {
    setIsLoading(true);

    try {
      const tokens = await authApi.loginCompany(credentials);

      saveTokens(tokens.accessToken, tokens.refreshToken);

      const company = await authApi.getCurrentCompany(tokens.accessToken);

      console.log("COMPANY LOGIN TOKENS:", tokens);

      console.log("CURRENT COMPANY:", company);

      setUser({
        id: company.orgNumber || credentials.orgNumber,

        name: company.name || tokens.name || "Företag",

        email: "",
        role: "COMPANY",
      });
    } catch (error) {
      clearSession();
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const loginCaseWorker = async (credentials: CaseWorkerCredentials) => {
    setIsLoading(true);

    try {
      const tokens = await authApi.loginCaseWorker(credentials);

      saveTokens(tokens.accessToken, tokens.refreshToken);

      setUser({
        id: credentials.email,
        name: tokens.name || credentials.email,
        email: credentials.email,
        role: "CASEWORKER",
      });
    } catch (error) {
      clearSession();
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async () => {
    setIsLoading(true);

    const accessToken = sessionStorage.getItem(ACCESS_TOKEN_KEY);

    try {
      if (accessToken) {
        await authApi.logout(accessToken);
      }
    } catch (error) {
      console.error("Fel vid utloggning:", error);
    } finally {
      clearSession();
      setIsLoading(false);
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isLoggedIn: user !== null,
        isLoading,
        loginCompany,
        loginCaseWorker,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};
