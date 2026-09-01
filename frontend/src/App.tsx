import { BrowserRouter, Routes, Route } from "react-router-dom";
import { Login } from "./pages/Login/Login";
import { Layout } from "./pages/Layout/Layout";
import { Documents } from "./components/documents/Documents";
import { CreditApplication } from "./components/credit-application/CreditApplication";

function App() {

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Login />} />
        <Route element={<Layout />} >


        <Route path="/documents" element={<Documents />} />
        <Route path="/credit-application" element={<CreditApplication />} />
        
        
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;