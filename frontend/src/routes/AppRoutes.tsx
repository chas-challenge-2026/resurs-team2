import React from "react";
import { Routes, Route, Navigate } from "react-router-dom";
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

import { mockApplications } from "../mockdata/applications";
import { mockTimelineSteps } from "../mockdata/timeline";

export const AppRoutes: React.FC = () => {
  return (
    <Routes>
      <Route path="/" element={<StartPage />} />
      <Route path="/foretagsbanken" element={<StartPage />} />
      <Route path="/login" element={<Login />} />

      <Route
        path="/application"
        element={
          <ProtectedRoute allowedRole="company">
            <Application />
          </ProtectedRoute>
        }
      />
      <Route
        path="/applications"
        element={
          <ProtectedRoute allowedRole="company">
            <Application />
          </ProtectedRoute>
        }
      />

      <Route
        path="/apply"
        element={
          <ProtectedRoute allowedRole="company">
            <CreditApplication />
          </ProtectedRoute>
        }
      />
      <Route
        path="/credit-application"
        element={
          <ProtectedRoute allowedRole="company">
            <CreditApplication />
          </ProtectedRoute>
        }
      />

      <Route
        path="/dashboard"
        element={
          <ProtectedRoute allowedRole="company">
            <Dashboard />
          </ProtectedRoute>
        }
      />

      <Route
        path="/documents/:id?"
        element={
          <ProtectedRoute allowedRole="company">
            <Documents />
          </ProtectedRoute>
        }
      />

      <Route
        path="/status/:id?"
        element={
          <ProtectedRoute allowedRole="company">
            <Status
              application={mockApplications[0]}
              steps={mockTimelineSteps}
              documents={[]}
            />
          </ProtectedRoute>
        }
      />

      <Route
        path="/backoffice"
        element={
          <ProtectedRoute allowedRole="caseWorker">
            <Backoffice />
          </ProtectedRoute>
        }
      />
      <Route
        path="/backoffice/:id"
        element={
          <ProtectedRoute allowedRole="caseWorker">
            <Backofficedetail />
          </ProtectedRoute>
        }
      />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
};