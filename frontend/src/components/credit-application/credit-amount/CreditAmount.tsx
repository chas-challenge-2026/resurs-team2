import type { ChangeEvent, SubmitEvent } from "react";
import { useState } from "react";
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
  const [purposeError, setPurposeError] = useState<string | null>(null);
  const [amountError, setAmountError] = useState<string | null>(null);

  const MIN_PURPOSE_LENGTH = 10;
  const MAX_PURPOSE_LENGTH = 500;
  const MIN_AMOUNT = 50000;
  const MAX_AMOUNT = 10000000;

  // *Handles changes to the requested credit amount.*
 const handleAmountChange = (
  event: ChangeEvent<HTMLInputElement>,
) => {
  const { value } = event.target;

  const amount = value === "" ? 0 : Number(value);

  onChange({
    ...data,
    requestedAmount: amount,
  });

  if (
    amountError &&
    amount >= MIN_AMOUNT &&
    amount <= MAX_AMOUNT
  ) {
    setAmountError(null);
  }
};

  // Handles changes to the credit purpose.
  const handlePurposeChange = (event: ChangeEvent<HTMLTextAreaElement>) => {
    const value = event.target.value;

    onChange({
      ...data,
      purpose: value,
    });

    if (purposeError && value.trim().length >= MIN_PURPOSE_LENGTH) {
      setPurposeError(null);
    }
  };

  // Prevents the default form submission and proceeds to the confirmation step.
  const handleSubmit = (
  event: SubmitEvent<HTMLFormElement>,
) => {
  event.preventDefault();

  let hasError = false;

  if (
    data.requestedAmount < MIN_AMOUNT ||
    data.requestedAmount > MAX_AMOUNT
  ) {
    setAmountError(
      `Kreditbeloppet måste vara mellan ${MIN_AMOUNT.toLocaleString("sv-SE")} SEK och ${MAX_AMOUNT.toLocaleString("sv-SE")} SEK.`,
    );

    hasError = true;
  }

  if (
    data.purpose.trim().length <
    MIN_PURPOSE_LENGTH
  ) {
    setPurposeError(
      `Beskrivningen måste innehålla minst ${MIN_PURPOSE_LENGTH} tecken.`,
    );

    hasError = true;
  }

  if (hasError) {
    return;
  }

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
            value={data.requestedAmount === 0 ? "" : data.requestedAmount}
            onChange={handleAmountChange}
          />

          <p className={styles.helpText}>
            Minsta belopp: 50 000 kr. Maxbelopp: 10 000 000 kr.
          </p>

        {amountError && (
          <p className={styles.errorText}>
            {amountError}
          </p>
)}
        </div>

        <div className={styles.formGroup}>
          <label htmlFor="purpose">Syfte med krediten</label>

          <textarea
            className={styles.formControl}
            id="purpose"
            name="purpose"
            rows={4}
            minLength={MIN_PURPOSE_LENGTH}
            maxLength={MAX_PURPOSE_LENGTH}
            placeholder="Beskriv kortfattat vad krediten ska användas till (expansion, rörelsekapital, investering, etc.)"
            value={data.purpose}
            onChange={handlePurposeChange}
          />
          <div className={styles.purposeInfo}>
  <span>
    {data.purpose.trim().length < MIN_PURPOSE_LENGTH
      ? `Minst ${MIN_PURPOSE_LENGTH} tecken (${MIN_PURPOSE_LENGTH - data.purpose.trim().length} kvar)`
      : "Minimilängd uppnådd"}
  </span>

  <span>
    {data.purpose.length} / {MAX_PURPOSE_LENGTH} tecken
  </span>
</div>

          {purposeError && <p className={styles.errorText}>{purposeError}</p>}
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
