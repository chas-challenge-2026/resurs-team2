import React from "react";
import "./Navbar.css";

interface NavbarProps {
  role?: "company" | "caseWorker";
  userName?: string;
}

export const Navbar: React.FC<NavbarProps> = ({
  role = "caseWorker",
  userName = "Erik Mattsson",
}) => {
  return (
    <nav className="navbar">
      <div className="navbar-container">
        <a className="navbar-brand" href="/">
          <span className="brand-title">Resurs Bank</span>
        </a>

        <ul className="navbar-nav">
          {role === "company" && (
            <>
              <li>
                <a href="/apply">➕ Ny ansökan</a>
              </li>
              <li>
                <a href="/applications">📋 Mina ansökningar</a>
              </li>
            </>
          )}

          {role === "caseWorker" && (
            <li>
              <a href="/backoffice">💼 Backoffice</a>
            </li>
          )}

          {userName && (
            <li className="user-info">
              👤 {role === "company" ? `Företag: ${userName}` : `Handläggare: ${userName}`}
            </li>
          )}

          <li>
            <a href="/logout">Logga ut</a>
          </li>
        </ul>
      </div>
    </nav>
  );
};

export default Navbar;