import React from "react";
import { useNavigate } from "react-router";
import styles from "./Header.module.css";

interface HeaderProps {
  companyName?: string;
}

const Header: React.FC<HeaderProps> = ({ companyName = "Företagsnamn" }) => {
  const navigate = useNavigate();

  return (
    <nav className={styles.header}>
      <span className={styles.headerTitle} onClick={() => navigate("")}>
        Resurs Kreditansökan
      </span>

      <div className={styles.headerRight}>
        <span className={styles.headerCompany}>{companyName}</span>
        <span className={styles.headerLogout} onClick={() => navigate("")}>
          Logga ut
        </span>
      </div>
    </nav>
  );
};

export default Header;
