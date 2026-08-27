import type { ChangeEvent, SubmitEvent } from "react";
import type { CompanyInformationData } from "./CompanyInformation.schema";
import styles from "./CompanyInformation.module.css";

type CompanyInformationProps = {
    data: CompanyInformationData;
    onChange: (data: CompanyInformationData) => void;
    onNext: () => void;
};

export function CompanyInformation({
    data,
    onChange,
    onNext,
}: CompanyInformationProps) {

    // Handles changes to the authorized signatory field.
    const handleAuthorizedSignatoryChange = (
        event: ChangeEvent<HTMLInputElement>
    ) => {
        onChange({
            ...data,
            authorizedSignature: event.target.value,
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
                <h2>Steg 1: Företagsuppgifter</h2>

                <p className={styles.mutedText}>
                    Kontrollera att uppgifterna stämmer
                    överens med era registrerade uppgifter.
                </p>
            </header>

            <form onSubmit={handleSubmit}>
                <fieldset>
                    <legend>Företagsuppgifter</legend>

                    <div className={styles.formGroup}>
                        <label htmlFor="orgNumber">
                            Organisationsnummer
                        </label>

                        <input className={styles.formControl}
                            id="orgNumber"
                            name="orgNumber"
                            type="text"
                            value={data.orgNumber}
                            readOnly
                        />
                    </div>

                    <div className={styles.formGroup}>
                        <label htmlFor="companyName">
                            Företagsnamn
                        </label>

                        <input
                            className={styles.formControl}
                            id="companyName"
                            name="companyName"
                            type="text"
                            value={data.companyName}
                            readOnly
                        />
                    </div>

                    <div className={styles.formGroup}>
                        <label htmlFor="authorizedSignature">
                            Firmatecknare (för- och efternamn)
                        </label>

                        <input
                            className={styles.formControl}
                            id="authorizedSignature"
                            name="authorizedSignature"
                            type="text"
                            placeholder="Anna Andersson"
                            value={data.authorizedSignature}
                            onChange={handleAuthorizedSignatoryChange}
                        />
                    </div>
                </fieldset>

                <button type="submit" className={styles.primaryButton}>
                    Nästa →
                </button>
            </form>
        </section>
    );
}