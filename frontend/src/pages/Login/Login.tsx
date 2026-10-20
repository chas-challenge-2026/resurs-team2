import { useState } from "react";

import { CaseWorkerLoginForm } from "./CaseWorkerLoginForm";
import { CompanyLoginForm } from "./CompanyLoginForm";

import "./Login.css";

export const Login: React.FC = () => {
  const [activeTab, setActiveTab] = useState<"company" | "caseWorker">(
    "company",
  );

  return (
    <div className="login-container">
      <div className="container">
        <div className="login-box">
          <div className="login-logo">
            <h2>Resurs Kreditansökan</h2>
            <p className="text-muted">Logga in för att fortsätta</p>
          </div>

          <ul className="nav nav-tabs">
            <li className={activeTab === "company" ? "active" : ""}>
              <button
                type="button"
                className="login-tab-link"
                onClick={() => setActiveTab("company")}
              >
                Företagsinloggning
              </button>
            </li>

            <li className={activeTab === "caseWorker" ? "active" : ""}>
              <button
                type="button"
                className="login-tab-link"
                onClick={() => setActiveTab("caseWorker")}
              >
                Handläggare
              </button>
            </li>
          </ul>

          <div className="tab-content">
            {activeTab === "company" && <CompanyLoginForm />}
            {activeTab === "caseWorker" && <CaseWorkerLoginForm />}
          </div>
        </div>
      </div>
    </div>
  );
};
