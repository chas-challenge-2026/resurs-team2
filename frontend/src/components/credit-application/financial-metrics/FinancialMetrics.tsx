import type { ChangeEvent, SubmitEvent } from "react";
import type { FinancialMetricsData } from "./FinancialMetrics.schema";
import styles from "./FinancialMetrics.module.css";

type FinancialMetricsProps = {
  data: FinancialMetricsData;
  onChange: (data: FinancialMetricsData) => void;
  onNext: () => void;
  onPrevious: () => void;
};

export function FinancialMetrics({
  data,
  onChange,
  onNext,
  onPrevious,
}: FinancialMetricsProps) {
    
  // Handles changes to the financial metric fields.
  const handleInputChange = (event: ChangeEvent<HTMLInputElement>) => {
    const { name, value } = event.target;

    onChange({
      ...data,
      [name]: value === "" ? "" : Number(value),
    });
  };

  // Prevents the default form submission and proceeds to the next step.
  const handleSubmit = (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
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
                placeholder="2500000"
                value={data.equity}
                onChange={handleInputChange}
              />

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
                placeholder="8000000"
                value={data.totalCapital}
                onChange={handleInputChange}
              />

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
                placeholder="3000000"
                value={data.currentAssets}
                onChange={handleInputChange}
              />
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
                placeholder="2000000"
                value={data.currentLiabilities}
                onChange={handleInputChange}
              />
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
                placeholder="5500000"
                value={data.totalLiabilities}
                onChange={handleInputChange}
              />
            </div>
          </div>

          <div className={styles.column}>
            <div className={styles.formGroup}>
              <label htmlFor="operatingResult">Rörelseresultat (SEK)</label>

              <input
                className={styles.formControl}
                id="operatingResult"
                name="operatingResult"
                type="number"
                step="1"
                placeholder="450000"
                value={data.operatingResult}
                onChange={handleInputChange}
              />

              <p className={styles.helpText}>EBIT</p>
            </div>
          </div>
        </div>

        <div className={styles.formGroup}>
          <label htmlFor="netSales">Nettoomsättning (SEK)</label>

          <input
            className={styles.formControl}
            id="netSales"
            name="netSales"
            type="number"
            step="1"
            placeholder="9500000"
            value={data.netSales}
            onChange={handleInputChange}
          />
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
