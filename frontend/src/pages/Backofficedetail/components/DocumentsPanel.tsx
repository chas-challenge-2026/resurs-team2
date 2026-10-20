import type { ApplicationDocument } from "../../../types/document";
import { Panel } from "../../../components/Panel/Panel";
import { documentApi } from "@/api/documentApi";
import styles from "../../Documents/Documents.module.css";

interface DocumentsPanelProps {
  documents: ApplicationDocument[];
  onDocumentDeleted?: (documentId: string) => void;
}

export const DocumentsPanel: React.FC<DocumentsPanelProps> = ({
  documents,
  onDocumentDeleted,
}) => {
  const handleDownload = async (
    documentId: string,
    filename: string,
  ) => {
    try {
      const blob = await documentApi.downloadDocument(documentId);

      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");

      link.href = url;
      link.download = filename;

      document.body.appendChild(link);
      link.click();
      link.remove();

      URL.revokeObjectURL(url);
    }catch( error) {
      console.error("Kunde inte ladda ner dokument:", error);
    }
  };

  const handleDelete = async (documentId: string) => {
    try {
      await documentApi.deleteDocument(documentId);

      onDocumentDeleted?.(documentId);
    }catch (error) {
      console.error("Kunde inte ta bort dokument:", error);
    }
  };
  return (
    <Panel title="Uppladdade dokument">
      {documents.length === 0 ? (
        <div className="text-muted">Inga dokument.</div>
      ) : (
        <ul className="document-list">
          {documents.map((doc) => (
            <li key={doc.uuid}>
              📄 {doc.filename}{" "}
              <small className="label label-document">{doc.docType}</small>

              <button
                type="button"
                className={styles.secondaryButton}
                onClick={() =>
                  handleDownload(doc.uuid, doc.filename)
                }
              >
                Ladda ner dokument
              </button>
              <button
                type="button"
                className={styles.secondaryButton}
                onClick={() => {
                  if (
                    window.confirm(
                      `Vill du verkligen ta bort "${doc.filename}"?`,
                    )
                  ) {
                    handleDelete(doc.uuid);
                  }
                }}
              >
                Ta bort dokument
              </button>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
};
