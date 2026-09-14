import { useState, type ChangeEvent, type SubmitEvent } from "react";
import { financialMetricsSchema, type FinancialMetricsFormData } from "../../../schemas/credit-application-schemas/FinancialMetrics.schema";
import styles from "./FinancialMetrics.module.css";

type FinancialMetricsProps = {
  data: FinancialMetricsFormData;
  onChange: (data: FinancialMetricsFormData) => void;
  onNext: () => void;
  onPrevious: () => void;
};

export function FinancialMetrics({
  data,
  onChange,
  onNext,
  onPrevious,
}: FinancialMetricsProps) {
  
  const [errors, setErrors] = useState<
    Partial<Record<keyof FinancialMetricsFormData, string>>
  >({});

  // Handles changes to the financial metric fields.
  const handleInputChange = (event: ChangeEvent<HTMLInputElement>) => {
    const { name, value } = event.target;

    onChange({
      ...data,
      [name]: value === "" ? undefined : Number(value),
    });
  };

  // Prevents the default form submission and proceeds to the next step.
  const handleSubmit = (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();

    const result = financialMetricsSchema.safeParse(data);

    if (!result.success) {
      const fieldErrors: Partial<
        Record<keyof FinancialMetricsFormData, string>
      > = {};

      result.error.issues.forEach((issue) => {
        const fieldName = issue.path[0];

        if (typeof fieldName === "string") {
          fieldErrors[fieldName as keyof FinancialMetricsFormData] =
            issue.message;
        }
      });

      setErrors(fieldErrors);
      return;
    }

    setErrors({});
    onNext();
  };

  return (
    <section className={styles.formSection}>
      <header>
        <h2>Steg 2: Finansiella nyckeltal</h2>

        <p className={styles.mutedText}>
          Hämta värdena från senaste årsredovisningen (belopp i SEK).
        </p>
      </header>

      <form onSubmit={handleSubmit}>
        <div className={styles.row}>
          <div className={styles.column}>
            <div className={styles.formGroup}>
              <label htmlFor="equity">Eget kapital (SEK)</label>

              <input
                className={styles.formControl}
                id="equity"
                name="equity"
                type="number"
                step="1"
                placeholder="0"
                value={data.equity ?? ""}
                onChange={handleInputChange}
              />

              {errors.equity && (
                <p className={styles.errorText}>{errors.equity}</p>
              )}

              <p className={styles.helpText}>Summa eget kapital</p>
            </div>
          </div>

          <div className={styles.column}>
            <div className={styles.formGroup}>
              <label htmlFor="totalCapital">Totalt kapital (SEK)</label>

              <input
                className={styles.formControl}
                id="totalCapital"
                name="totalCapital"
                type="number"
                step="1"
                placeholder="0"
                value={data.totalCapital ?? ""}
                onChange={handleInputChange}
              />
              {errors.totalCapital && (
                <p className={styles.errorText}>{errors.totalCapital}</p>
              )}

              <p className={styles.helpText}>Balansomslutning</p>
            </div>
          </div>
        </div>

        <div className={styles.row}>
          <div className={styles.column}>
            <div className={styles.formGroup}>
              <label htmlFor="currentAssets">Omsättningstillgångar (SEK)</label>

              <input
                className={styles.formControl}
                id="currentAssets"
                name="currentAssets"
                type="number"
                step="1"
                placeholder="0"
                value={data.currentAssets ?? ""}
                onChange={handleInputChange}
              />
              {errors.currentAssets && (
                <p className={styles.errorText}>{errors.currentAssets}</p>
              )}
            </div>
          </div>

          <div className={styles.column}>
            <div className={styles.formGroup}>
              <label htmlFor="currentLiabilities">
                Kortfristiga skulder (SEK)
              </label>

              <input
                className={styles.formControl}
                id="currentLiabilities"
                name="currentLiabilities"
                type="number"
                step="1"
                placeholder="0"
                value={data.currentLiabilities ?? ""}
                onChange={handleInputChange}
              />
              {errors.currentLiabilities && (
                <p className={styles.errorText}>{errors.currentLiabilities}</p>
              )}
            </div>
          </div>
        </div>

        <div className={styles.row}>
          <div className={styles.column}>
            <div className={styles.formGroup}>
              <label htmlFor="totalLiabilities">Totala skulder (SEK)</label>

              <input
                className={styles.formControl}
                id="totalLiabilities"
                name="totalLiabilities"
                type="number"
                step="1"
                placeholder="0"
                value={data.totalLiabilities ?? ""}
                onChange={handleInputChange}
              />
              {errors.totalLiabilities && (
                <p className={styles.errorText}>{errors.totalLiabilities}</p>
              )}
            </div>
          </div>

          <div className={styles.column}>
            <div className={styles.formGroup}>
              <label htmlFor="operatingIncome">Rörelseresultat (SEK)</label>

              <input
                className={styles.formControl}
                id="operatingIncome"
                name="operatingIncome"
                type="number"
                step="1"
                placeholder="0"
                value={data.operatingIncome ?? ""}
                onChange={handleInputChange}
              />

              {errors.operatingIncome && (
                <p className={styles.errorText}>{errors.operatingIncome}</p>
              )}

              <p className={styles.helpText}>EBIT</p>
            </div>
          </div>
        </div>

        <div className={styles.formGroup}>
          <label htmlFor="netRevenue">Nettoomsättning (SEK)</label>

          <input
            className={styles.formControl}
            id="netRevenue"
            name="netRevenue"
            type="number"
            step="1"
            placeholder="0"
            value={data.netRevenue ?? ""}
            onChange={handleInputChange}
          />
          {errors.netRevenue && (
            <p className={styles.errorText}>{errors.netRevenue}</p>
          )}
        </div>

        <button
          type="button"
          className={styles.secondaryButton}
          onClick={onPrevious}
        >
          ← Tillbaka
        </button>

        <button type="submit" className={styles.primaryButton}>
          Nästa →
        </button>
      </form>
    </section>
  );
}
