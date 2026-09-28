import { BrowserRouter } from "react-router-dom";

import { Layout } from "./components/Layout/Layout";
import { AuthProvider } from "./context/AuthProvider";
import { AppRoutes } from "./routes/AppRoutes";

export function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Layout>
          <AppRoutes />
        </Layout>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;