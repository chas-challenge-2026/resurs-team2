import { Outlet } from "react-router-dom";
import Header from "../../components/header/Header";
import styles from "./Layout.module.css";

export function Layout() {
    return (
        <div className={styles.page}>
            <Header />
            <main>
                <Outlet />
            </main>
        </div>
    );
}