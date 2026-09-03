import type { ChangeEvent, FormEvent } from "react";
import { useState, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";

import type { DocumentData, DocumentType } from "./Documents.schema";
import styles from "./Documents.module.css";

export function Documents() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const applicationId = id ?? "0";

  const [documents, setDocuments] = useState<DocumentData[]>([]);
  const [docType, setDocType] = useState<DocumentType>("arsredovisning");
  const [file, setFile] = useState<File | null>(null);

  // Hanterar ändring av dokumenttyp
  const handleDocumentTypeChange = (
    event: ChangeEvent<HTMLSelectElement>
  ) => {
    setDocType(event.target.value as DocumentType);
  };

  // Hanterar filval
  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const selectedFile = event.target.files?.[0] ?? null;
    setFile(selectedFile);
  };

  // Hanterar uppladdning
  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!file) return;

    const newDocument: DocumentData = {
      id: crypto.randomUUID(),
      filename: file.name,
      docType: docType,
      uploadedAt: new Date().toLocaleString("sv-SE"),
    };

    setDocuments((currentDocuments) => [...currentDocuments, newDocument]);
    
    // Återställ fil-state och rensa inputfältet
    setFile(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  const handleDownload = (documentId: string) => {
    console.log("Laddar ner dokument:", documentId);
  };

  const handleBack = () => {
    navigate("/application");
  };

  return (
    <section className={styles.formSection}>
      <header>
        <h2>Dokument – Ansökan #{applicationId}</h2>
        <div className={`${styles.alert} ${styles.alertDanger}`}></div>
      </header>

      <div className={styles.documentGrid}>
        <div className={styles.panel}>
          <div className={styles.panelHeading}>Ladda upp dokument</div>

          <div className={styles.panelBody}>
            <p>
              Ladda upp årsredovisning (PDF) och F-skatteintyg.
            </p>

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
                <p className={styles.mutedText}>
                  Inga dokument uppladdade.
                </p>
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