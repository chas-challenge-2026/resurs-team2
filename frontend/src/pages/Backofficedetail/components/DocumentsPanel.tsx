import React from "react";
import type { ApplicationDocument } from "../../../types/document";
import { Panel } from "../../../components/Panel/Panel";

interface DocumentsPanelProps {
  documents: ApplicationDocument[];
}

export const DocumentsPanel: React.FC<DocumentsPanelProps> = ({
  documents,
}) => {
  return (
    <Panel title="Uppladdade dokument">
      {documents.length === 0 ? (
        <div className="text-muted">Inga dokument.</div>
      ) : (
        <ul className="document-list">
          {documents.map((doc) => (
            <li key={doc.id}>
              📄 {doc.filename}{" "}
              <small className="label label-default">{doc.docType}</small>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
};
