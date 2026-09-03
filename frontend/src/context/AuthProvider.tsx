import React, { useState } from "react";
import {
  AuthContext,
  type User,
  type CompanyCredentials,
  type CaseWorkerCredentials,
} from "./AuthContext";

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);

  const loginCompany = async (credentials: CompanyCredentials) => {
    setIsLoading(true);
    try {
      const response = await fetch("/api/v1/auth/login/company", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(credentials),
      });

      if (!response.ok) {
        throw new Error("Inloggning misslyckades. Kontrollera organisationsnumret.");
      }

      const data = await response.json();
      const accessToken = data.accessToken || data.token;

      if (accessToken) {
        localStorage.setItem("token", accessToken);
        setToken(accessToken);
      }

      setUser({
        id: credentials.orgNumber,
        name: "Företag",
        email: "",
        role: "COMPANY",
      });
    } finally {
      setIsLoading(false);
    }
  };

  const loginCaseWorker = async (credentials: CaseWorkerCredentials) => {
    setIsLoading(true);
    try {
      const response = await fetch("/api/v1/auth/login/caseWorker", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(credentials),
      });

      if (!response.ok) {
        throw new Error("Felaktig e-postadress eller lösenord.");
      }

      const data = await response.json();
      const accessToken = data.accessToken || data.token;

      if (accessToken) {
        localStorage.setItem("token", accessToken);
        setToken(accessToken);
      }

      setUser({
        id: "cw-1",
        name: "Handläggare",
        email: credentials.email,
        role: "CASEWORKER",
      });
    } finally {
      setIsLoading(false);
    }
  };

  const logout = () => {
    localStorage.removeItem("token");
    setUser(null);
    setToken(null);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: !!user,
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