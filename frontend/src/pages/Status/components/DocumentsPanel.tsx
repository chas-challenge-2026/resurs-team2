import { useNavigate } from "react-router-dom";

import { Panel } from "../../../components/Panel/Panel";
import type { ApplicationDocument } from "../../../types/document";

interface DocumentsPanelProps {
  applicationId: number;
  documents: ApplicationDocument[];
}

export const DocumentsPanel: React.FC<DocumentsPanelProps> = ({
  applicationId,
  documents,
}) => {
  const navigate = useNavigate();

  return (
    <Panel title="Dokument">
      <div className="documents-actions">
        <span className="text-muted">
          {documents.length === 0
            ? "Inga dokument uppladdade ännu."
            : `${documents.length} dokument uppladdade`}
        </span>

        <button
          type="button"
          className="btn btn-sm btn-primary"
          onClick={() => navigate(`/documents/${applicationId}`)}
        >
          Ladda upp
        </button>
      </div>

      {documents.length > 0 && (
        <ul className="document-list">
          {documents.map((doc) => (
            <li key={doc.uuid}>
              📄 {doc.filename} ({doc.docType})
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
};