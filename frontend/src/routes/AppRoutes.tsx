import React from "react";
import { Routes, Route, Navigate } from "react-router-dom";
import { StartPage } from "../pages/Startpage/StartPage";
import { Login } from "../pages/Login/Login";
import { Status } from "../pages/Status/Status";
import { Backoffice } from "../pages/Backoffice/Backoffice";

import { mockApplications } from "../mockdata/applications";
import { mockTimelineSteps } from "../mockdata/timeline";

export const AppRoutes: React.FC = () => {
  return (
    <Routes>
      <Route path="/" element={<StartPage />} />
      <Route path="/foretagsbanken" element={<StartPage />} />

      <Route path="/login" element={<Login />} />

      <Route
        path="/status"
        element={
          <Status
            application={mockApplications[0]}
            steps={mockTimelineSteps}
            documents={[]}
          />
        }
      />

      <Route path="/backoffice" element={<Backoffice />} />
      
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
};