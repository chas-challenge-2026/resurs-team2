import { useState, type ChangeEvent, type SubmitEvent } from "react";

import {
  financialMetricsSchema,
  type FinancialMetricsFormData,
} from "../../../schemas/credit-application-schemas/FinancialMetrics.schema";

import { FinancialMetricField } from "./FinancialMetricsField";
import { financialMetricFields } from "./FinancialMetrics.fields";

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

      <p
        id="financial-metrics-description"
        className={styles.mutedText}
      >
        Hämta värdena från senaste årsredovisningen
        (belopp i SEK).
      </p>
    </header>

    <form
      onSubmit={handleSubmit}
      aria-describedby="financial-metrics-description"
    >
      <div className={styles.row}>
        {financialMetricFields.slice(0, 2).map((field) => (
          <div className={styles.column} key={field.name}>
            <FinancialMetricField
              {...field}
              value={data[field.name]}
              error={errors[field.name]}
              onChange={handleInputChange}
            />
          </div>
        ))}
      </div>

      <div className={styles.row}>
        {financialMetricFields.slice(2, 4).map((field) => (
          <div className={styles.column} key={field.name}>
            <FinancialMetricField
              {...field}
              value={data[field.name]}
              error={errors[field.name]}
              onChange={handleInputChange}
            />
          </div>
        ))}
      </div>

      <div className={styles.row}>
        {financialMetricFields.slice(4, 6).map((field) => (
          <div className={styles.column} key={field.name}>
            <FinancialMetricField
              {...field}
              value={data[field.name]}
              error={errors[field.name]}
              onChange={handleInputChange}
            />
          </div>
        ))}
      </div>

      <FinancialMetricField
        {...financialMetricFields[6]}
        value={data[financialMetricFields[6].name]}
        error={errors[financialMetricFields[6].name]}
        onChange={handleInputChange}
      />

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
  )
}