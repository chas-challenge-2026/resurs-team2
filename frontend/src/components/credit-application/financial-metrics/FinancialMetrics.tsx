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

  const handleInputChange = (
    event: ChangeEvent<HTMLInputElement>,
  ) => {
    const { name, value } = event.target;

    onChange({
      ...data,
      [name]:
        value === "" ? undefined : Number(value),
    });
  };

  const handleSubmit = (
    event: SubmitEvent<HTMLFormElement>,
  ) => {
    event.preventDefault();

    const result =
      financialMetricsSchema.safeParse(data);

    if (!result.success) {
      const fieldErrors: Partial<
        Record<keyof FinancialMetricsFormData, string>
      > = {};

      result.error.issues.forEach((issue) => {
        const fieldName = issue.path[0];

        if (typeof fieldName === "string") {
          fieldErrors[
            fieldName as keyof FinancialMetricsFormData
          ] = issue.message;
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
          Hämta värdena från senaste årsredovisningen
          (belopp i SEK).
        </p>
      </header>

      <form onSubmit={handleSubmit}>
        <div className={styles.row}>
          <div className={styles.column}>
            <div className={styles.formGroup}>
              <label htmlFor="equity">
                Eget kapital (SEK)
              </label>

              <input
                className={styles.formControl}
                id="equity"
                name="equity"
                type="number"
                aria-invalid={Boolean(errors.equity)}
                aria-describedby={
                  errors.equity
                    ? "equity-error"
                    : undefined
                }
                step="1"
                placeholder="0"
                value={data.equity ?? ""}
                onChange={handleInputChange}
              />

              {errors.equity && (
                <p
                  id="equity-error"
                  className={styles.errorText}
                  role="alert"
                >
                  {errors.equity}
                </p>
              )}

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
                name="totalCapital"
                type="number"
                aria-invalid={Boolean(
                  errors.totalCapital,
                )}
                aria-describedby={
                  errors.totalCapital
                    ? "totalCapital-error"
                    : undefined
                }
                step="1"
                placeholder="0"
                value={data.totalCapital ?? ""}
                onChange={handleInputChange}
              />

              {errors.totalCapital && (
                <p
                  id="totalCapital-error"
                  className={styles.errorText}
                  role="alert"
                >
                  {errors.totalCapital}
                </p>
              )}

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
                name="currentAssets"
                type="number"
                aria-invalid={Boolean(
                  errors.currentAssets,
                )}
                aria-describedby={
                  errors.currentAssets
                    ? "currentAssets-error"
                    : undefined
                }
                step="1"
                placeholder="0"
                value={data.currentAssets ?? ""}
                onChange={handleInputChange}
              />

              {errors.currentAssets && (
                <p
                  id="currentAssets-error"
                  className={styles.errorText}
                  role="alert"
                >
                  {errors.currentAssets}
                </p>
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
                aria-invalid={Boolean(
                  errors.currentLiabilities,
                )}
                aria-describedby={
                  errors.currentLiabilities
                    ? "currentLiabilities-error"
                    : undefined
                }
                step="1"
                placeholder="0"
                value={data.currentLiabilities ?? ""}
                onChange={handleInputChange}
              />

              {errors.currentLiabilities && (
                <p
                  id="currentLiabilities-error"
                  className={styles.errorText}
                  role="alert"
                >
                  {errors.currentLiabilities}
                </p>
              )}
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
                name="totalLiabilities"
                type="number"
                aria-invalid={Boolean(
                  errors.totalLiabilities,
                )}
                aria-describedby={
                  errors.totalLiabilities
                    ? "totalLiabilities-error"
                    : undefined
                }
                step="1"
                placeholder="0"
                value={data.totalLiabilities ?? ""}
                onChange={handleInputChange}
              />

              {errors.totalLiabilities && (
                <p
                  id="totalLiabilities-error"
                  className={styles.errorText}
                  role="alert"
                >
                  {errors.totalLiabilities}
                </p>
              )}
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
                name="operatingIncome"
                type="number"
                aria-invalid={Boolean(
                  errors.operatingIncome,
                )}
                aria-describedby={
                  errors.operatingIncome
                    ? "operatingIncome-error"
                    : undefined
                }
                step="1"
                placeholder="0"
                value={data.operatingIncome ?? ""}
                onChange={handleInputChange}
              />

              {errors.operatingIncome && (
                <p
                  id="operatingIncome-error"
                  className={styles.errorText}
                  role="alert"
                >
                  {errors.operatingIncome}
                </p>
              )}

              <p className={styles.helpText}>
                EBIT
              </p>
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
            name="netRevenue"
            type="number"
            aria-invalid={Boolean(errors.netRevenue)}
            aria-describedby={
              errors.netRevenue
                ? "netRevenue-error"
                : undefined
            }
            step="1"
            placeholder="0"
            value={data.netRevenue ?? ""}
            onChange={handleInputChange}
          />

          {errors.netRevenue && (
            <p
              id="netRevenue-error"
              className={styles.errorText}
              role="alert"
            >
              {errors.netRevenue}
            </p>
          )}
        </div>

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
          Nästa →
        </button>
      </form>
    </section>
  );
}