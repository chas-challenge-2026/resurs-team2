import { useState, type FormEvent } from "react";
import styles from "./BankIdSignature.module.css";

type BankIdSignatureProps = {
  companyName: string;
  orgNumber: string;
  onPrevious: () => void;
  onSign: () => Promise<void>;
};

/**
 * Final wizard step: the signatory signs the application with BankID, and the
 * submission happens as part of that signing call.
 *
 * The button stays disabled once a signing call has succeeded, even before
 * navigation finishes -- re-enabling it would let a second click submit a
 * second application while the first one is still on its way.
 */
export function BankIdSignature({
  companyName,
  orgNumber,
  onPrevious,
  onSign,
}: BankIdSignatureProps) {
  const [signing, setSigning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSign = async (event: FormEvent) => {
    event.preventDefault();

    if (signing) {
      return;
    }

    setSigning(true);
    setError(null);

    try {
      await onSign();
    } catch {
      setError(
        "Signeringen kunde inte genomföras. Försök igen.",
      );
      setSigning(false);
    }
  };

  return (
    <section
      className={styles.formSection}
      aria-labelledby="bankid-heading"
    >
      <header>
        <h2 id="bankid-heading">Steg 5: Signera med BankID</h2>
      </header>

      <form onSubmit={handleSign} noValidate>
        <div className={styles.infoAlert}>
          <p>
            <strong>
              Ansökan behöver signeras innan den
              skickas in.
            </strong>
          </p>

          <p>
            Signaturen läggs till på ansökan och
            registreras i händelseloggen tillsammans
            med uppgifter om vem som signerade.
          </p>
        </div>

        <section
          className={styles.panel}
          aria-labelledby="signer-heading"
        >
          <h3
            id="signer-heading"
            className={styles.panelHeading}
          >
            Undertecknare
          </h3>

          <div className={styles.panelBody}>
            <p>
              <strong>Företag:</strong>{" "}
              <span>{companyName}</span>
            </p>

            <p>
              <strong>Org.nummer:</strong>{" "}
              <span>{orgNumber}</span>
            </p>
          </div>
        </section>

        <div
          className={styles.bankIdBox}
          aria-live="polite"
        >
          <span
            className={styles.bankIdIcon}
            aria-hidden="true"
          >
            🔒
          </span>

          <div className={styles.bankIdText}>
            <p className={styles.bankIdTitle}>
              Autentisering och signering via BankID
            </p>

            <p className={styles.mutedText}>
              {signing
                ? "Öppna BankID-appen och bekräfta signaturen."
                : "Du signerar med BankID i din mobil."}
            </p>
          </div>

          {signing && (
            <span
              className={styles.spinner}
              role="status"
              aria-label="Väntar på BankID"
            />
          )}
        </div>

        {error && (
          <p className={styles.errorText} role="alert">
            {error}
          </p>
        )}

        <div className={styles.buttonGroup}>
          <button
            type="button"
            className={styles.secondaryButton}
            onClick={onPrevious}
            disabled={signing}
          >
            ← Tillbaka
          </button>

          <button
            type="submit"
            className={styles.primaryButton}
            disabled={signing}
          >
            {signing
              ? "Väntar på signering..."
              : "Signera med BankID"}
          </button>
        </div>
      </form>
    </section>
  );
}
