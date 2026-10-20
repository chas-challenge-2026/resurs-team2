import type { ChangeEvent } from "react";
import type { FinancialMetricsFormData } from "../../../schemas/credit-application-schemas/FinancialMetrics.schema";
import styles from "./FinancialMetrics.module.css";

type FinancialMetricFieldProps = {
  name: keyof FinancialMetricsFormData;
  label: string;
  helpText?: string;
  value: number | undefined;
  error?: string;
  onChange: (event: ChangeEvent<HTMLInputElement>) => void;
};

export function FinancialMetricField({
  name,
  label,
  helpText,
  value,
  error,
  onChange,
}: FinancialMetricFieldProps) {
  const fieldId = String(name);

  return (
    <div className={styles.formGroup}>
      <label htmlFor={fieldId}>{label}</label>

      <input
        className={styles.formControl}
        id={fieldId}
        name={fieldId}
        type="number"
        aria-invalid={Boolean(error)}
        aria-describedby={
          error
            ? `${fieldId}-error`
            : helpText
              ? `${fieldId}-help`
              : undefined
        }
        step="1"
        placeholder="0"
        value={value ?? ""}
        onChange={onChange}
      />

      {error && (
        <p
          id={`${fieldId}-error`}
          className={styles.errorText}
          role="alert"
        >
          {error}
        </p>
      )}

      {helpText && (
        <p
          id={`${fieldId}-help`}
          className={styles.helpText}
        >
          {helpText}
        </p>
      )}
    </div>
  );
}