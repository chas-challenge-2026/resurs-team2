import React from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";
import "./Navbar.css";

export const Navbar: React.FC = () => {
  const { user, isLoggedIn, logout } = useAuth();

  const navigate = useNavigate();

  console.log("NAVBAR AUTH:", {
    user,
    isLoggedIn,
  });

  const handleLogoutClick = async () => {
    await logout();
    navigate("/login");
  };

  return (
    <header className="navbar-wrapper">
      <div className="navbar-top-bar">
        <div className="navbar-container navbar-top-container">
          <div className="top-nav-left">
            <Link to="/" className="nav-tab">
              Privat
            </Link>

            <Link to="/" className="nav-tab">
              Betallösningar
            </Link>

            <Link to="/" className="nav-tab active-pill">
              Företagsbanken
            </Link>
          </div>

          <div className="top-nav-center">
            <span className="demo-badge">Skolprojekt (Ej officiell sida)</span>
          </div>

          <div className="top-nav-right">
            {isLoggedIn && user ? (
              <>
                <span className="user-info">
                  👤{" "}
                  {user.role === "COMPANY"
                    ? `Företag: ${user.name}`
                    : `Handläggare: ${user.name}`}
                </span>

                <button
                  type="button"
                  onClick={handleLogoutClick}
                  className="logout-btn"
                >
                  Logga ut
                </button>
              </>
            ) : (
              <Link to="/login" className="login-link">
                Logga in
              </Link>
            )}
          </div>
        </div>
      </div>

      <div className="navbar-main">
        <div className="navbar-container">
          <Link className="navbar-brand" to="/">
            <span className="brand-logo-text">Resurs</span>

            <span className="brand-subtext">Kreditansökan</span>
          </Link>

          <ul className="navbar-nav">
            {isLoggedIn && user?.role === "COMPANY" && (
              <>
                <li>
                  <Link to="/apply">Ny ansökan</Link>
                </li>

                <li>
                  <Link to="/application">Mina ansökningar</Link>
                </li>
              </>
            )}

            {isLoggedIn && user?.role === "CASEWORKER" && (
              <li>
                <Link to="/backoffice">💼 Backoffice</Link>
              </li>
            )}
          </ul>
        </div>
      </div>
    </header>
  );
};

export default Navbar;
