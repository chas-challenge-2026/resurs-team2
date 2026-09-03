import React, { createContext, useContext, useState, useEffect } from "react";

export type Role = "company" | "caseWorker";

export interface User {
  id?: string;
  name?: string;
  orgNumber?: string;
  email?: string;
  role: Role;
}

interface AuthTokens {
  accessToken: string;
  refreshToken?: string;
}

interface AuthContextType {
  user: User | null;
  token: string | null;
  isLoggedIn: boolean;
  isLoading: boolean;
  loginCompany: (credentials: { orgNumber: string }) => Promise<void>;
  loginCaseWorker: (credentials: { username: string; password: string }) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(localStorage.getItem("accessToken"));
  const [isLoading, setIsLoading] = useState<boolean>(true);

  useEffect(() => {
    const storedToken = localStorage.getItem("accessToken");
    const storedUser = localStorage.getItem("authUser");

    if (storedToken && storedUser) {
      setToken(storedToken);
      try {
        setUser(JSON.parse(storedUser));
      } catch {
        setUser(null);
      }
    }
    setIsLoading(false);
  }, []);

  const saveSession = (tokens: AuthTokens, userData: User) => {
    localStorage.setItem("accessToken", tokens.accessToken);
    localStorage.setItem("authUser", JSON.stringify(userData));
    setToken(tokens.accessToken);
    setUser(userData);
  };

  const loginCompany = async (credentials: { orgNumber: string }) => {
    const res = await fetch("/api/v1/auth/login/company", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ orgNumber: credentials.orgNumber }),
    });

    if (!res.ok) {
      throw new Error("Inloggning misslyckades. Kontrollera organisationsnumret.");
    }

    const tokens: AuthTokens = await res.json();

    const userData: User = {
      orgNumber: credentials.orgNumber,
      role: "company",
    };

    saveSession(tokens, userData);
  };

  const loginCaseWorker = async (credentials: { username: string; password: string }) => {
    const res = await fetch("/api/v1/auth/login/caseWorker", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        email: credentials.username,
        password: credentials.password,
      }),
    });

    if (!res.ok) {
      throw new Error("Felaktig e-post eller lösenord.");
    }

    const tokens: AuthTokens = await res.json();

    const userData: User = {
      email: credentials.username,
      role: "caseWorker",
    };

    saveSession(tokens, userData);
  };

  const logout = () => {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("authUser");
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isLoggedIn: !!user,
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

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth måste användas inom en AuthProvider");
  }
  return context;
};