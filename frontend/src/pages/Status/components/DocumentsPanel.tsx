import React from "react";
import { useNavigate } from "react-router-dom";
import type { ApplicationDocument } from "../../../types/document";
import { Panel } from "../../../components/Panel/Panel";

interface DocumentsPanelProps {
  applicationId: number;
  documents: ApplicationDocument[];
}

export const DocumentsPanel: React.FC<
  DocumentsPanelProps
> = ({
  applicationId,
  documents,
}) => {
  const navigate = useNavigate();

  return (
    <Panel
      title={
        <>
          Dokument

          <button
            type="button"
            className="btn btn-sm btn-primary pull-right"
            onClick={() =>
              navigate(
                `/documents/${applicationId}`,
              )
            }
          >
            Ladda upp
          </button>
        </>
      }
    >
      {documents.length === 0 ? (
        <p className="text-muted">
          Inga dokument uppladdade ännu.
        </p>
      ) : (
        <ul className="document-list">
          {documents.map((doc) => (
            <li key={doc.id}>
              📄 {doc.filename} (
              {doc.docType})
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
};