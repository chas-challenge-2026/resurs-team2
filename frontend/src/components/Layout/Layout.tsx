import React from "react";
import styles from "./Layout.module.css";
import { Navbar } from "../Navbar/Navbar";
import { Footer } from "../Footer/Footer";

interface LayoutProps {
  children: React.ReactNode;
}

export const Layout: React.FC<LayoutProps> = ({ children }) => {
  return (
    <div className={styles.layoutContainer}>
      <Navbar />

      <main className={styles.mainContent}>{children}</main>

      <Footer />
    </div>
  );
};

export default Layout;
