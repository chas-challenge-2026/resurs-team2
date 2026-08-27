import type { ChangeEvent, SubmitEvent } from "react";
import type { CreditAmountData } from "./CreditAmount.schema";
import styles from "./CreditAmount.module.css";

type CreditAmountProps = {
  data: CreditAmountData;
  onChange: (data: CreditAmountData) => void;
  onNext: () => void;
  onPrevious: () => void;
};

export function CreditAmount({
  data,
  onChange,
  onNext,
  onPrevious,
}: CreditAmountProps) {
  // Handles changes to the requested credit amount.
  const handleAmountChange = (event: ChangeEvent<HTMLInputElement>) => {
    const value = event.target.value;

    onChange({
      ...data,
      requestedAmount: value === "" ? 0 : Number(value),
    });
  };

  // Handles changes to the credit purpose.
  const handlePurposeChange = (event: ChangeEvent<HTMLTextAreaElement>) => {
    onChange({
      ...data,
      purpose: event.target.value,
    });
  };

  // Prevents the default form submission and proceeds to the confirmation step.
  const handleSubmit = (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    onNext();
  };

  return (
    <section className={styles.formSection}>
      <header>
        <h2>Steg 3: Kreditbelopp och syfte</h2>
      </header>

      <form onSubmit={handleSubmit}>
        <div className={styles.formGroup}>
          <label htmlFor="requestedAmount">Önskat kreditbelopp (SEK)</label>

          <input
            className={styles.formControl}
            id="requestedAmount"
            name="requestedAmount"
            type="number"
            step="1000"
            min="50000"
            max="10000000"
            placeholder="500000"
            value={data.requestedAmount}
            onChange={handleAmountChange}
          />

          <p className={styles.helpText}>
            Minsta belopp: 50 000 kr. Maxbelopp: 10 000 000 kr.
          </p>
        </div>

        <div className={styles.formGroup}>
          <label htmlFor="purpose">Syfte med krediten</label>

          <textarea
            className={styles.formControl}
            id="purpose"
            name="purpose"
            rows={4}
            placeholder="Beskriv kortfattat vad krediten ska användas till (expansion, rörelsekapital, investering, etc.)"
            value={data.purpose}
            onChange={handlePurposeChange}
          />
        </div>

        <div className={styles.buttonGroup}>
          <button
            type="button"
            className={styles.secondaryButton}
            onClick={onPrevious}
          >
            ← Tillbaka
          </button>

          <button type="submit" className={styles.primaryButton}>
            Granska ansökan →
          </button>
        </div>
      </form>
    </section>
  );
}
