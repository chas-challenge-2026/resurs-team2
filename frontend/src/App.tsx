import React from "react";
import { BrowserRouter } from "react-router-dom";
import { Layout } from "./components/Layout/Layout";
import { AppRoutes } from "./routes/AppRoutes";

export const App: React.FC = () => {
  return (
    <BrowserRouter>
      <Layout>
        <AppRoutes />
      </Layout>
    </BrowserRouter>
  );
};

export default App;