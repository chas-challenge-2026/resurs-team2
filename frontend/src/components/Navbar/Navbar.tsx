import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";
import "./Navbar.css";

interface NavbarProps {
  role?: string;
  userName?: string;
}

export const Navbar: React.FC<NavbarProps> = ({ role, userName }) => {
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();

  const currentRole = (user?.role || role || "").toUpperCase();
  const displayName = user?.name || userName || "";

  const handleLogoutClick = async (e: React.MouseEvent) => {
    e.preventDefault();
    await logout();
    navigate("/");
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
            {isAuthenticated && (user || displayName) ? (
              <>
                <span className="user-info">
                  👤{" "}
                  {currentRole === "COMPANY"
                    ? `Företag: ${displayName}`
                    : `Handläggare: ${displayName}`}
                </span>
                <button onClick={handleLogoutClick} className="logout-btn">
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
            {isAuthenticated && currentRole === "COMPANY" && (
              <>
                <li>
                  <Link to="/apply"> Ny ansökan</Link>
                </li>
                <li>
                  <Link to="/applications"> Mina ansökningar</Link>
                </li>
              </>
            )}

            {isAuthenticated && currentRole === "CASEWORKER" && (
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