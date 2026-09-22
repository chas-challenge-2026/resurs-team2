import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { useAuth } from "../../components/hooks/useAuth";
import { companyLoginSchema } from "./loginSchemas";

export const CompanyLoginForm = () => {
  const navigate = useNavigate();
  const { loginCompany } = useAuth();

  const [orgNumber, setOrgNumber] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (
    event: React.SyntheticEvent<HTMLFormElement>,
  ) => {
    event.preventDefault();
    setError(null);

    const result = companyLoginSchema.safeParse({
      orgNumber,
    });

    if (!result.success) {
      setError(
        result.error.issues[0]?.message ??
          "Ange ett giltigt organisationsnummer.",
      );
      return;
    }

    setLoading(true);

    try {
      await loginCompany(result.data);
      navigate("/dashboard");
    } catch (error: unknown) {
      if (error instanceof Error) {
        setError(error.message);
      } else {
        setError(
          "Inloggning misslyckades. Kontrollera organisationsnumret.",
        );
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="tab-pane active">
      <div className="login-bankid-header">
        <span className="bankid-icon">🔒</span>
        <p className="text-muted login-bankid-text">
          Autentisering via BankID
        </p>
      </div>

      {error && <div className="alert alert-danger">{error}</div>}

      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label htmlFor="orgNumber">Organisationsnummer</label>
          <input
            type="text"
            className="form-control"
            id="orgNumber"
            placeholder="556000-1234"
            value={orgNumber}
            onChange={(event) => setOrgNumber(event.target.value)}
            required
          />

          <p className="help-block">
            Ange organisationsnummer för BankID-autentisering
          </p>
        </div>

        <button
          type="submit"
          className="btn btn-primary btn-block"
          disabled={loading}
        >
          {loading ? "Loggar in..." : "Logga in med BankID"}
        </button>
      </form>

      <div className="alert alert-info login-test-info">
        <strong>Testmiljö:</strong> Godkända org.nummer: 556000-1234,
        556000-5678
      </div>
    </div>
  );
};