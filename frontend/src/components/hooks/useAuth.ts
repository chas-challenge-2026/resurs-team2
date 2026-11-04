import { useContext } from "react";
import { AuthContext } from "../../context/AuthContext";
import type { AuthContextType } from "../../context/auth.types";

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);

  if (context === undefined) {
    throw new Error("useAuth måste användas inom en AuthProvider");
  }

  return context;
};