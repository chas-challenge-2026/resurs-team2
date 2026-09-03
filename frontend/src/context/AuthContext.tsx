import React, { createContext, useContext, useState, useEffect } from "react";

export type Role = "company" | "caseWorker";

export interface User {
  id?: string;
  name: string;
  role: Role;
  email?: string;
}

interface AuthContextType {
  user: User | null;
  isLoggedIn: boolean;
  isLoading: boolean;
  loginCompany: (credentials: { orgNumber: string; email: string }) => Promise<void>;
  loginCaseWorker: (credentials: { username: string; password: string }) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const fetchCompanyProfile = async (): Promise<User | null> => {
    try {
      const res = await fetch("/api/v1/companies/me", {
        method: "GET",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
      });

      if (res.ok) {
        const data = await res.json();
        return {
          id: data.id,
          name: data.companyName || data.name || data.orgName || data.email || "Företag",
          role: "company",
          email: data.email,
        };
      }
    } catch (err) {
      console.error("Kunde inte hämta företagsprofil:", err);
    }
    return null;
  };

  useEffect(() => {
    const checkAuthStatus = async () => {
      try {
        const currentUser = await fetchCompanyProfile();
        setUser(currentUser);
      } catch {
        setUser(null);
      } finally {
        setIsLoading(false);
      }
    };

    checkAuthStatus();
  }, []);

  const loginCompany = async (credentials: { orgNumber: string; email: string }) => {
    const res = await fetch("/api/v1/auth/login/company", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify(credentials),
    });

    if (!res.ok) {
      throw new Error("Inloggningen misslyckades. Kontrollera dina uppgifter.");
    }

    const companyUser = await fetchCompanyProfile();

    if (companyUser) {
      setUser(companyUser);
    } else {
      setUser({
        name: credentials.email || credentials.orgNumber,
        role: "company",
        email: credentials.email,
      });
    }
  };

  const loginCaseWorker = async (credentials: { username: string; password: string }) => {
    const res = await fetch("/api/v1/auth/login/case-worker", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify(credentials),
    });

    if (!res.ok) {
      throw new Error("Handläggarinloggning misslyckades.");
    }

    const data = await res.json().catch(() => ({}));

    setUser({
      name: data.name || credentials.username,
      role: "caseWorker",
    });
  };

  const logout = async () => {
    try {
      await fetch("/api/v1/auth/logout", {
        method: "POST",
        credentials: "include",
      });
    } catch (err) {
      console.error("Fel vid utloggningsanrop:", err);
    } finally {
      setUser(null);
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
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