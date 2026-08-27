import React, { useState } from "react";
import "./Login.css";

export const Login: React.FC = () => {
  const [activeTab, setActiveTab] = useState<"company" | "caseWorker">(
    "company",
  );
  const [orgNumber, setOrgNumber] = useState<string>("");
  const [email, setEmail] = useState<string>("");
  const [password, setPassword] = useState<string>("");
  const [error, setError] = useState<string | null>(null);

  const handleCompanyLogin = async (
    e: React.SyntheticEvent<HTMLFormElement>,
  ) => {
    e.preventDefault();
    const response = await fetch("/login/company", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ orgNumber }),
    });
    if (response.redirected) {
      window.location.href = response.url;
    }
  };

  const handleCaseWorkerLogin = async (
    e: React.SyntheticEvent<HTMLFormElement>,
  ) => {
    e.preventDefault();
    const response = await fetch("/login/caseWorker", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ email, password }),
    });
    if (response.redirected) {
      window.location.href = response.url;
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
                onClick={() => setActiveTab("company")}
                style={{ cursor: "pointer" }}
              >
                Företagsinloggning
              </a>
            </li>
            <li className={activeTab === "caseWorker" ? "active" : ""}>
              <a
                onClick={() => setActiveTab("caseWorker")}
                style={{ cursor: "pointer" }}
              >
                Handläggare
              </a>
            </li>
          </ul>

          <div className="tab-content">
            {activeTab === "company" && (
              <div className="tab-pane active">
                <div className="text-center" style={{ marginBottom: "15px" }}>
                  <span className="bankid-icon">🔒</span>
                  <p className="text-muted" style={{ marginTop: "8px" }}>
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
                      Ange organisationsnummer för bankID-autentisering
                    </p>
                  </div>
                  <button type="submit" className="btn btn-primary btn-block">
                    Logga in med bankID
                  </button>
                </form>
                <div
                  className="alert alert-info"
                  style={{ marginTop: "15px", fontSize: "12px" }}
                >
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
                  <button type="submit" className="btn btn-warning btn-block">
                    Logga in
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
