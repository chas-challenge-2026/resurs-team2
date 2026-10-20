import { useState } from "react";
import { parseRejectionReason, type RejectionReason } from "../../../utils/rejectionReasonFormatter";
import styles from "./RejectedApplicationPanel.module.css";

interface RejectedApplicationPanelProps {
  decisionReason: string | null;
}

const ReasonIcon = ({
  type,
}: {
  type: RejectionReason["type"];
}) => {
  if (type === "REJECT") {
    return (
      <span
        className={`${styles.reasonIcon} ${styles.reasonIconReject}`}
        aria-hidden="true"
      >
        !
      </span>
    );
  }

  return (
    <span
      className={`${styles.reasonIcon} ${styles.reasonIconWarning}`}
      aria-hidden="true"
    >
      !
    </span>
  );
};

export const RejectedApplicationPanel = ({
  decisionReason,
}: RejectedApplicationPanelProps) => {
  const [showDetails, setShowDetails] = useState(false);

  if (!decisionReason) {
    return null;
  }

  const reasons = parseRejectionReason(decisionReason);

  return (
    <section
      className={styles.rejectedApplication}
      aria-labelledby="rejected-application-heading"
    >
      <div
        className={styles.rejectedApplicationIcon}
        aria-hidden="true"
      >
        !
      </div>

      <div className={styles.rejectedApplicationContent}>
        <h2 id="rejected-application-heading">
          Ansökan avslagen
        </h2>

        <p>
          Efter genomgång av din ansökan har vi bedömt att
          den inte uppfyller våra krav.
        </p>
      </div>

      <button
        type="button"
        className={styles.rejectedApplicationToggle}
        aria-expanded={showDetails}
        aria-controls="rejected-application-details"
        onClick={() => setShowDetails((current) => !current)}
      >
        <span>
          {showDetails
            ? "Dölj detaljerade skäl"
            : "Se detaljerade skäl"}
        </span>

        <span
          className={`${styles.chevron} ${
            showDetails ? styles.chevronExpanded : ""
          }`}
          aria-hidden="true"
        >
          ↓
        </span>
      </button>

      {showDetails && (
        <div
          id="rejected-application-details"
          className={styles.details}
        >
          <div className={styles.detailsHeader}>
            <h3>Detaljerade skäl</h3>

            <p>
              Följande delar av ansökan uppfyller inte våra
              krav eller behöver uppmärksammas.
            </p>
          </div>

          {reasons.length > 0 ? (
            <ul className={styles.reasons}>
              {reasons.map((reason, index) => (
                <li
                  key={`${reason.type}-${index}`}
                  className={`${styles.reason} ${
                    reason.type === "REJECT"
                      ? styles.reasonReject
                      : styles.reasonWarning
                  }`}
                >
                  <ReasonIcon type={reason.type} />

                  <div className={styles.reasonContent}>
                    <strong>
                      {reason.type === "REJECT"
                        ? "Avslag"
                        : "Varning"}
                    </strong>

                    <span>{reason.text}</span>
                  </div>
                </li>
              ))}
            </ul>
          ) : (
            <p className={styles.noDetails}>
              Ingen ytterligare motivering finns
              tillgänglig.
            </p>
          )}
        </div>
      )}
    </section>
  );
};