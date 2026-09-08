import React from "react";

import {
  Navigate,
  Route,
  Routes,
} from "react-router-dom";

import { StartPage } from "../pages/Startpage/StartPage";
import { Login } from "../pages/Login/Login";
import { Application } from "../pages/Application/Application";
import { Backoffice } from "../pages/Backoffice/Backoffice";
import { Backofficedetail } from "../pages/Backofficedetail/Backofficedetail";
import { CreditApplication } from "../pages/CreditApplication/CreditApplication";
import { Dashboard } from "../pages/Dashboard/Dashboard";
import { Documents } from "../pages/Documents/Documents";
import { Status } from "../pages/Status/Status";

import { ProtectedRoute } from "../components/ProtectedRoute/ProtectedRoute";

export const AppRoutes: React.FC = () => {
  return (
    <Routes>
      <Route
        path="/"
        element={<StartPage />}
      />

      <Route
        path="/foretagsbanken"
        element={<StartPage />}
      />

      <Route
        path="/login"
        element={<Login />}
      />

      <Route
        path="/application"
        element={
          <ProtectedRoute allowedRole="COMPANY">
            <Application />
          </ProtectedRoute>
        }
      />

      <Route
        path="/applications"
        element={
          <ProtectedRoute allowedRole="COMPANY">
            <Application />
          </ProtectedRoute>
        }
      />

      <Route
        path="/apply"
        element={
          <ProtectedRoute allowedRole="COMPANY">
            <CreditApplication />
          </ProtectedRoute>
        }
      />

      <Route
        path="/credit-application"
        element={
          <ProtectedRoute allowedRole="COMPANY">
            <CreditApplication />
          </ProtectedRoute>
        }
      />

      <Route
        path="/dashboard"
        element={
          <ProtectedRoute allowedRole="COMPANY">
            <Dashboard />
          </ProtectedRoute>
        }
      />

      <Route
        path="/documents/:id"
        element={
          <ProtectedRoute allowedRole="COMPANY">
            <Documents />
          </ProtectedRoute>
        }
      />

      <Route
        path="/status/:id"
        element={
          <ProtectedRoute allowedRole="COMPANY">
            <Status />
          </ProtectedRoute>
        }
      />

      <Route
        path="/backoffice"
        element={
          <ProtectedRoute allowedRole="CASEWORKER">
            <Backoffice />
          </ProtectedRoute>
        }
      />

      <Route
        path="/backoffice/:id"
        element={
          <ProtectedRoute allowedRole="CASEWORKER">
            <Backofficedetail />
          </ProtectedRoute>
        }
      />

      <Route
        path="*"
        element={
          <Navigate
            to="/"
            replace
          />
        }
      />
    </Routes>
  );
};