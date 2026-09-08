import type { ChangeEvent } from "react";
import { useRef, useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";

import type { DocumentType } from "./Documents.schema";
import type { ApplicationDocument } from "../../types/document";
import { documentApi } from "../../api/documentApi";

import styles from "./Documents.module.css";

export function Documents() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const applicationId = id ?? "0";
  const [documents, setDocuments] = useState<ApplicationDocument[]>([]);
  const [docType, setDocType] = useState<DocumentType>("arsredovisning");
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function fetchDocuments() {
      try {
        setError(null);

        const fetchedDocuments =
          await documentApi.getAllDocuments(applicationId);

        setDocuments(fetchedDocuments);
      } catch (error) {
        console.error("Kunde inte hämta dokument:", error);

        setError("Kunde inte hämta dokumenten.");
      }
    }

    fetchDocuments();
  }, [applicationId]);

  // Hanterar ändring av dokumenttyp
  const handleDocumentTypeChange = (event: ChangeEvent<HTMLSelectElement>) => {
    setDocType(event.target.value as DocumentType);
  };

  // Hanterar filval
  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const selectedFile = event.target.files?.[0] ?? null;

    if (!selectedFile) {
      setFile(null);
      return;
    }

    if (selectedFile.type !== "application/pdf") {
      setError("Endast PDF-filer kan laddas upp.");
      setFile(null);
      return;
    }

    if (selectedFile.size > 10 * 1024 * 1024) {
      setError("Filen får vara högst 10 MB.");
      setFile(null);
      return;
    }

    setError(null);
    setFile(selectedFile);
  };

  // Hanterar uppladdning
  const handleSubmit = async (event: React.SyntheticEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!file) return;

    try {
      setError(null);

      const uploadedDocument = await documentApi.uploadDocument(
        applicationId,
        docType,
        file,
      );

      setDocuments((currentDocuments) => [
        ...currentDocuments,
        uploadedDocument,
      ]);

      setFile(null);

      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    } catch (error) {
      console.error("Kunde inte ladda upp dokument:", error);

      setError("Kunde inte ladda upp dokumentet.");
    }
  };

  const handleDownload = async (documentId: number) => {
    try {
      const blob = await documentApi.downloadDocument(documentId);

      const url = URL.createObjectURL(blob);

      const link = document.createElement("a");

      link.href = url;

      link.download = "";

      link.click();

      URL.revokeObjectURL(url);
    } catch (error) {
      console.error("Kunde inte ladda ner dokument:", error);
    }

    setError("Kunde inte ladda ner dokumentet.");
  };

  const handleBack = () => {
    navigate("/application");
  };

  return (
    <section className={styles.formSection}>
      <header>
        <h2>Dokument – Ansökan #{applicationId}</h2>
        {error && (
          <div className={`${styles.alert} ${styles.alertDanger}`}>{error}</div>
        )}
      </header>

      <div className={styles.documentGrid}>
        <div className={styles.panel}>
          <div className={styles.panelHeading}>Ladda upp dokument</div>

          <div className={styles.panelBody}>
            <p>Ladda upp årsredovisning (PDF) och F-skatteintyg.</p>

            <form onSubmit={handleSubmit}>
              <div className={styles.formGroup}>
                <label htmlFor="docType">Dokumenttyp</label>

                <select
                  id="docType"
                  name="docType"
                  className={styles.formControl}
                  value={docType}
                  onChange={handleDocumentTypeChange}
                >
                  <option value="arsredovisning">Årsredovisning</option>
                  <option value="fskattebevis">F-skattebevis</option>
                  <option value="bolagsordning">Bolagsordning</option>
                  <option value="ovrigt">Övrigt</option>
                </select>
              </div>

              <div className={styles.formGroup}>
                <label htmlFor="file">Fil (PDF)</label>

                <input
                  ref={fileInputRef}
                  id="file"
                  name="file"
                  type="file"
                  accept=".pdf,.PDF"
                  onChange={handleFileChange}
                />

                <p className={styles.helpText}>
                  Max 10 MB. Filen sparas men parsas inte automatiskt.
                </p>
              </div>

              <button
                type="submit"
                className={styles.primaryButton}
                disabled={!file}
              >
                ↑ Ladda upp
              </button>
            </form>
          </div>
        </div>

        <div>
          <div className={styles.panel}>
            <div className={styles.panelHeading}>Uppladdade dokument</div>

            <div className={styles.panelBody}>
              {documents.length === 0 ? (
                <p className={styles.mutedText}>Inga dokument uppladdade.</p>
              ) : (
                <table className={styles.documentTable}>
                  <thead>
                    <tr>
                      <th scope="col">Fil</th>
                      <th scope="col">Typ</th>
                      <th scope="col">Uppladdad</th>
                      <th scope="col">Åtgärd</th>
                    </tr>
                  </thead>

                  <tbody>
                    {documents.map((document) => (
                      <tr key={document.id}>
                        <td>{document.filename}</td>
                        <td>{document.docType}</td>
                        <td>{document.uploadedAt}</td>

                        <td>
                          <button
                            type="button"
                            className={styles.secondaryButton}
                            onClick={() => handleDownload(document.id)}
                          >
                            ↓
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>

          <div className={styles.warningAlert}>
            <strong>OBS:</strong> PDF-innehåll läses inte automatiskt i v1.
            Handläggare granskar dokumenten manuellt.
          </div>
        </div>
      </div>

      <button
        type="button"
        className={styles.secondaryButton}
        onClick={handleBack}
      >
        ← Tillbaka till ansökan
      </button>
    </section>
  );
}
