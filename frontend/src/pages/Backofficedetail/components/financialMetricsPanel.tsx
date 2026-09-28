import type { FinancialMetricsData } from "@/schemas/credit-application-schemas/FinancialMetrics.schema";
import styles from "../../../components/credit-application/financial-metrics/FinancialMetrics.module.css";

interface FinancialMetricsPanelProps {
  financialMetrics: FinancialMetricsData;
}

export const FinancialMetricsPanel: React.FC<
  FinancialMetricsPanelProps
> = ({ financialMetrics }) => {
  return (
    <section className={styles.formSection}>
      <header>
        <h2>Finansiella nyckeltal</h2>

        <p className={styles.mutedText}>
          Finansiella uppgifter som angavs i kreditansökan.
        </p>
      </header>

      <div>
        <div className={styles.row}>
          <div className={styles.column}>
            <div className={styles.formGroup}>
              <label htmlFor="equity">Eget kapital (SEK)</label>

              <input
                className={styles.formControl}
                id="equity"
                type="number"
                value={financialMetrics.equity}
                readOnly
              />

              <p className={styles.helpText}>
                Summa eget kapital
              </p>
            </div>
          </div>

          <div className={styles.column}>
            <div className={styles.formGroup}>
              <label htmlFor="totalCapital">
                Totalt kapital (SEK)
              </label>

              <input
                className={styles.formControl}
                id="totalCapital"
                type="number"
                value={financialMetrics.totalCapital}
                readOnly
              />

              <p className={styles.helpText}>
                Balansomslutning
              </p>
            </div>
          </div>
        </div>

        <div className={styles.row}>
          <div className={styles.column}>
            <div className={styles.formGroup}>
              <label htmlFor="currentAssets">
                Omsättningstillgångar (SEK)
              </label>

              <input
                className={styles.formControl}
                id="currentAssets"
                type="number"
                value={financialMetrics.currentAssets}
                readOnly
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
                type="number"
                value={financialMetrics.currentLiabilities}
                readOnly
              />
            </div>
          </div>
        </div>

        <div className={styles.row}>
          <div className={styles.column}>
            <div className={styles.formGroup}>
              <label htmlFor="totalLiabilities">
                Totala skulder (SEK)
              </label>

              <input
                className={styles.formControl}
                id="totalLiabilities"
                type="number"
                value={financialMetrics.totalLiabilities}
                readOnly
              />
            </div>
          </div>

          <div className={styles.column}>
            <div className={styles.formGroup}>
              <label htmlFor="operatingIncome">
                Rörelseresultat (SEK)
              </label>

              <input
                className={styles.formControl}
                id="operatingIncome"
                type="number"
                value={financialMetrics.operatingIncome}
                readOnly
              />

              <p className={styles.helpText}>EBIT</p>
            </div>
          </div>
        </div>

        <div className={styles.formGroup}>
          <label htmlFor="netRevenue">
            Nettoomsättning (SEK)
          </label>

          <input
            className={styles.formControl}
            id="netRevenue"
            type="number"
            value={financialMetrics.netRevenue}
            readOnly
          />
        </div>
      </div>
    </section>
  );
};