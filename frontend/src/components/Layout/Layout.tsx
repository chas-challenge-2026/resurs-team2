import type { ReactNode } from "react";

import { Footer } from "../Footer/Footer";
import { Navbar } from "../Navbar/Navbar";

import styles from "./Layout.module.css";

interface LayoutProps {
  children: ReactNode;
}

export function Layout({ children }: LayoutProps) {
  return (
    <div className={styles.layoutContainer}>
      <Navbar />

      <main className={styles.mainContent}>
        {children}
      </main>

      <Footer />
    </div>
  );
}

export default Layout;