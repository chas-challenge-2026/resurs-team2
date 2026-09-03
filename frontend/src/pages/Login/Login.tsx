import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../components/hooks/useAuth";
import "./Login.css";

export const Login: React.FC = () => {
  const navigate = useNavigate();
  const { login } = useAuth();

  const [activeTab, setActiveTab] = useState<"company" | "caseWorker">("company");
  const [orgNumber, setOrgNumber] = useState<string>("");
  const [email, setEmail] = useState<string>("");
  const [password, setPassword] = useState<string>("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(false);

  const handleCompanyLogin = async (e: React.SyntheticEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      await login({
        id: orgNumber,
        name: "Företag",
        email: email,
        role: "COMPANY",
      });
      navigate("/application");
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError("Inloggning misslyckades. Kontrollera organisationsnumret.");
      }
    } finally {
      setLoading(false);
    }
  };

  const handleCaseWorkerLogin = async (e: React.SyntheticEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      await login({
        id: "cw-1",
        name: "Handläggare",
        email: email,
        role: "CASEWORKER",
      });
      navigate("/backoffice");
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError("Felaktig e-post eller lösenord.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-container">
      <div className="container">
        <div className="login-box">
          <div className="login-logo">
            <h2>Resurs Kreditansökan</h2>
            <p className="text-muted">Logga in för att fortsätta</p>
          </div>

          {error && <div className="alert alert-danger">{error}</div>}

          <ul className="nav nav-tabs">
            <li className={activeTab === "company" ? "active" : ""}>
              <a
                className="login-tab-link"
                onClick={() => setActiveTab("company")}
              >
                Företagsinloggning
              </a>
            </li>
            <li className={activeTab === "caseWorker" ? "active" : ""}>
              <a
                className="login-tab-link"
                onClick={() => setActiveTab("caseWorker")}
              >
                Handläggare
              </a>
            </li>
          </ul>

          <div className="tab-content">
            {activeTab === "company" && (
              <div className="tab-pane active">
                <div className="login-bankid-header">
                  <span className="bankid-icon">🔒</span>
                  <p className="text-muted login-bankid-text">
                    Autentisering via BankID
                  </p>
                </div>
                <form onSubmit={handleCompanyLogin}>
                  <div className="form-group">
                    <label htmlFor="orgNumber">Organisationsnummer</label>
                    <input
                      type="text"
                      className="form-control"
                      id="orgNumber"
                      placeholder="556000-1234"
                      value={orgNumber}
                      onChange={(e) => setOrgNumber(e.target.value)}
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
            )}

            {activeTab === "caseWorker" && (
              <div className="tab-pane active">
                <form onSubmit={handleCaseWorkerLogin}>
                  <div className="form-group">
                    <label htmlFor="email">E-postadress</label>
                    <input
                      type="email"
                      className="form-control"
                      id="email"
                      placeholder="karin@resurs.se"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label htmlFor="password">Lösenord</label>
                    <input
                      type="password"
                      className="form-control"
                      id="password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      required
                    />
                  </div>
                  <button
                    type="submit"
                    className="btn btn-warning btn-block"
                    disabled={loading}
                  >
                    {loading ? "Loggar in..." : "Logga in"}
                  </button>
                </form>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};