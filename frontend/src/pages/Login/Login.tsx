import { useState } from "react";
import { CaseWorkerLoginForm } from "./CaseWorkerLoginForm";
import { CompanyLoginForm } from "./CompanyLoginForm";
import "./Login.css";

export const Login: React.FC = () => {
  const [showCaseWorker, setShowCaseWorker] = useState(false);

  return (
    <div className="login-container">
      <div className="container">
        <div className="login-box">
          <div className="login-logo">
            <h2>Resurs Kreditansökan</h2>
            <p className="text-muted">Logga in för att fortsätta</p>
          </div>

          <div className="tab-content">
            {showCaseWorker ? <CaseWorkerLoginForm /> : <CompanyLoginForm />}
          </div>

          <div style={{ marginTop: "20px", textAlign: "center" }}>
            <button
              type="button"
              onClick={() => setShowCaseWorker(!showCaseWorker)}
            >
              {showCaseWorker
                ? "← Tillbaka till företagsinloggning"
                : "Logga in som handläggare"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};