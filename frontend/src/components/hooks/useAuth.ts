import { useContext } from "react";
import { AuthContext, type AuthContextType } from "../../context/AuthContext";

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth måste användas inom en AuthProvider");
  }
  return context;
};