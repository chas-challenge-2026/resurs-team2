import { useState } from "react";
import styles from "./CreditApplication.module.css";

import { CompanyInformation } from "./company-information/CompanyInformation";
import { companyInformationSchema,type CompanyInformationData, } from "./company-information/CompanyInformation.schema";

import { FinancialMetrics } from "./financial-metrics/FinancialMetrics";
import { financialMetricsSchema,type FinancialMetricsData, } from "./financial-metrics/FinancialMetrics.schema";

import { CreditAmount } from "./credit-amount/CreditAmount";
import { creditAmountSchema,type CreditAmountData, } from "./credit-amount/CreditAmount.schema";

import { Confirmation } from "./confirmation/Confirmation";
import { confirmationSchema,type ConfirmationFormData, } from "./confirmation/Confirmation.schema";

export function CreditApplication() {
  const [currentStep, setCurrentStep] = useState(1);

  const [companyInformation, setCompanyInformation] =
    useState<CompanyInformationData>({
      orgNumber: "556123-4567",
      companyName: "Exempel AB",
      authorizedSignature: "",
    });

  const [financialMetrics, setFinancialMetrics] =
    useState<FinancialMetricsData>({
      equity: 0,
      totalCapital: 0,
      currentAssets: 0,
      currentLiabilities: 0,
      totalLiabilities: 0,
      operatingResult: 0,
      netSales: 0,
    });

  const [creditAmount, setCreditAmount] = useState<CreditAmountData>({
    requestedAmount: 0,
    purpose: "",
  });

  const [confirmation, setConfirmation] = useState<ConfirmationFormData>({
    financialConfirmation: false,
  });

  // Function that show which step you are on
  const handleStepChange = (step: number) => {
    setCurrentStep(step);
  };

  // Validates step 1 before the user proceeds.
  const handleCompanyInformationNext = () => {
    const result = companyInformationSchema.safeParse(companyInformation);

    if (!result.success) {
      console.log(result.error);
      return;
    }

    handleStepChange(2);
  };

  // Validates step 2 before the user proceeds.
  const handleFinancialMetricsNext = () => {
    const result = financialMetricsSchema.safeParse(financialMetrics);

    if (!result.success) {
      console.log(result.error);
      return;
    }

    handleStepChange(3);
  };

  // Validates step 3 before the user proceeds.
  const handleCreditAmountNext = () => {
    const result = creditAmountSchema.safeParse(creditAmount);

    if (!result.success) {
      console.log(result.error);
      return;
    }

    handleStepChange(4);
  };

  // Validates step 4 before the user proceeds.
  const handleConfirmationSubmit = () => {
    const result = confirmationSchema.safeParse(confirmation);

    if (!result.success) {
      console.log(result.error);
      return;
    }

    console.log("Kreditansökan är redo att skickas:", {
      companyInformation,
      financialMetrics,
      creditAmount: resultCreditAmount(),
      confirmation: result.data,
    });
  };

  // Validates and parses the credit amount.
  const resultCreditAmount = () => {
    const result = creditAmountSchema.safeParse(creditAmount);

    if (!result.success) {
      return null;
    }

    return result.data;
  };

  const steps = [
    "1. Företagsuppgifter",
    "2. Finansiella nyckeltal",
    "3. Kreditbelopp",
    "4. Bekräftelse",
  ];

  return (
    <main className={styles.container}>
      <nav className={styles.stepIndicator} aria-label="Ansökans steg">
        {steps.map((step, index) => {
          const stepNumber = index + 1;

          const isActive = currentStep === stepNumber;
          const isDone = currentStep > stepNumber;

          return (
            <div
              key={step}
              className={`${styles.stepItem} ${isActive ? styles.active : ""} ${
                isDone ? styles.done : ""
              }`}
            >
              {step}
            </div>
          );
        })}
      </nav>

      {currentStep === 1 && (
        <CompanyInformation
          data={companyInformation}
          onChange={setCompanyInformation}
          onNext={handleCompanyInformationNext}
        />
      )}

      {currentStep === 2 && (
        <FinancialMetrics
          data={financialMetrics}
          onChange={setFinancialMetrics}
          onNext={handleFinancialMetricsNext}
          onPrevious={() => handleStepChange(1)}
        />
      )}

      {currentStep === 3 && (
        <CreditAmount
          data={creditAmount}
          onChange={setCreditAmount}
          onNext={handleCreditAmountNext}
          onPrevious={() => handleStepChange(2)}
        />
      )}

      {currentStep === 4 && (
        <Confirmation
          data={confirmation}
          companyName={companyInformation.companyName}
          orgNumber={companyInformation.orgNumber}
          onChange={setConfirmation}
          onPrevious={() => handleStepChange(3)}
          onSubmit={handleConfirmationSubmit}
        />
      )}
    </main>
  );
}
