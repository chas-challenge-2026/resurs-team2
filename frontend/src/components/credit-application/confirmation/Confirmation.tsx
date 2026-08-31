import type { SubmitEvent } from "react";
import type { ConfirmationFormData } from "./Confirmation.schema";
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
   
    // Prevents the default form submission and triggers the final submission.
    const handleSubmit = (event: SubmitEvent) => {
    event.preventDefault();
    onSubmit();
};

    return (
        <section className={styles.formSection}>
            <header>
                <h2>Steg 4: Bekräftelse</h2>
            </header>

            <form onSubmit={handleSubmit}>
                <div className={styles.infoAlert}>
                    <p>
                        <strong>
                            Kontrollera uppgifterna innan du skickar in ansökan.
                        </strong>
                    </p>

                    <p>
                        Ansökan kommer behandlas av Resurs Kreditavdelning.
                        Du kan följa statusen i portalen.
                    </p>
                </div>

                <div className={styles.panel}>
                    <div className={styles.panelHeading}>
                        Sammanfattning
                    </div>

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
                </div>

                <div className={styles.checkbox}>
                    <label htmlFor="financialConfirmation">
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
/>

                        Jag intygar att de finansiella uppgifterna är korrekta
                        och hämtade från senaste årsredovisningen.
                    </label>
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
                        className={styles.submitButton}
                    >
                        Skicka in ansökan
                    </button>
                </div>
            </form>
        </section>
    );
}