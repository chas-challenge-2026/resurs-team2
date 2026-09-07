import React from "react";
import styles from "./Layout.module.css";
import { Navbar } from "../Navbar/Navbar";
import { Footer } from "../Footer/Footer";

interface LayoutProps {
  children: React.ReactNode;
  isLoggedIn?: boolean;
}

export const Layout: React.FC<LayoutProps> = ({ children, isLoggedIn = true }) => {
  return (
    <div className={styles.layoutContainer}>
      <Navbar
        role="caseWorker"
        userName={isLoggedIn ? "Erik Mattsson" : undefined}
      />
      <main className={styles.mainContent}>
        {children}
      </main>
      <Footer />
    </div>
  );
};

export default Layout;