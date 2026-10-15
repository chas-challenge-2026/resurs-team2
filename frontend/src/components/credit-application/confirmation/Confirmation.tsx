import { useState, type SubmitEvent } from "react";
import type { ConfirmationFormData } from "../../../schemas/credit-application-schemas/Confirmation.schema";
import styles from "./Confirmation.module.css";

type ConfirmationProps = {
  data: ConfirmationFormData;
  companyName: string;
  orgNumber: string;
  onChange: (data: ConfirmationFormData) => void;
  onPrevious: () => void;
  onSubmit: () => void;
};

export function Confirmation({
  data,
  companyName,
  orgNumber,
  onChange,
  onPrevious,
  onSubmit,
}: ConfirmationProps) {
  const [submitted, setSubmitted] = useState(false);

  const handleSubmit = (event: SubmitEvent) => {
    setSubmitted(true);
    event.preventDefault();
    onSubmit();
  };

  return (
    <section
      className={styles.formSection}
      aria-labelledby="confirmation-heading"
    >
      <header>
        <h2 id="confirmation-heading">
          Steg 4: Bekräftelse
        </h2>
      </header>

      <form onSubmit={handleSubmit} noValidate>
        <div className={styles.infoAlert}>
          <p>
            <strong>
              Kontrollera uppgifterna innan du skickar
              in ansökan.
            </strong>
          </p>

          <p>
            Ansökan kommer behandlas av Resurs
            Kreditavdelning. Du kan följa statusen i
            portalen.
          </p>
        </div>

        <section
          className={styles.panel}
          aria-labelledby="summary-heading"
        >
          <h3
            id="summary-heading"
            className={styles.panelHeadingSum}
          >
            Sammanfattning
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

            <p className={styles.mutedText}>
              Dina uppgifter lagras för kreditbedömning.
            </p>
          </div>
        </section>

        <div className={styles.checkbox}>
  <input
    id="financialConfirmation"
    name="financialConfirmation"
    type="checkbox"
    checked={data.financialConfirmation}
    onChange={(event) =>
      onChange({
        financialConfirmation: event.target.checked,
      })
    }
    aria-describedby={
      submitted && !data.financialConfirmation
        ? "financialConfirmation-error"
        : undefined
    }
    aria-invalid={
      submitted && !data.financialConfirmation
    }
  />

  <div className={styles.checkboxContent}>
    <label htmlFor="financialConfirmation">
      Jag intygar att de finansiella uppgifterna
      är korrekta och hämtade från senaste
      årsredovisningen.
    </label>

    {submitted &&
      !data.financialConfirmation && (
        <p
          id="financialConfirmation-error"
          className={styles.errorText}
          role="alert"
        >
          Du måste intyga att de finansiella
          uppgifterna är korrekta.
        </p>
      )}
  </div>
</div>

        <div className={styles.buttonGroup}>
          <button
            type="button"
            className={styles.secondaryButton}
            onClick={onPrevious}
          >
            ← Tillbaka
          </button>

          <button
            type="submit"
            className={styles.primaryButton}
          >
            Skicka in ansökan
          </button>
        </div>
      </form>
    </section>
  );
}