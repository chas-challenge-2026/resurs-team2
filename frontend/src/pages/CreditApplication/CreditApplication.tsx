import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import styles from "./CreditApplication.module.css";

import { CompanyInformation } from "../../components/credit-application/company-information/CompanyInformation";
import { companyInformationSchema, type CompanyInformationData } from "../../schemas/credit-application-schemas/CompanyInformation.schema";
import { FinancialMetrics } from "../../components/credit-application/financial-metrics/FinancialMetrics";
import { financialMetricsSchema, type FinancialMetricsFormData } from "../../schemas/credit-application-schemas/FinancialMetrics.schema";

import { CreditAmount } from "../../components/credit-application/credit-amount/CreditAmount";
import { creditAmountSchema, type CreditAmountData } from "../../schemas/credit-application-schemas/CreditAmount.schema";

import { Confirmation } from "../../components/credit-application/confirmation/Confirmation";
import { confirmationSchema, type ConfirmationFormData } from "../../schemas/credit-application-schemas/Confirmation.schema";
import { BankIdSignature } from "../../components/credit-application/bankid-signature/BankIdSignature";
import { ApplicationRequestSchema } from "../../schemas/ApplicationRequest.schema";
import { applicationApi } from "../../api/applicationApi";
import { companyApi } from "../../api/companyApi";

export function CreditApplication() {
  const [currentStep, setCurrentStep] = useState(1);
  const navigate = useNavigate();
  const [loadingCompany, setLoadingCompany] = useState(true);
  const [companyError, setCompanyError] = useState<string | null>(null);

  const [companyInformation, setCompanyInformation] =
    useState<CompanyInformationData>({
      orgNumber: "",
      companyName: "",
    });

  const [financialMetrics, setFinancialMetrics] =
    useState<FinancialMetricsFormData>({});

  const [creditAmount, setCreditAmount] = useState<CreditAmountData>({
    requestedAmount: 0,
    purpose: "",
  });

  const [confirmation, setConfirmation] = useState<ConfirmationFormData>({
    financialConfirmation: false,
  });

  useEffect(() => {
    async function fetchCompany() {
      try {
        setLoadingCompany(true);
        setCompanyError(null);

        const company = await companyApi.getCurrentCompany();

        setCompanyInformation({
          orgNumber: company.orgNumber,
          companyName: company.companyName,
        });
      } catch (error) {
        console.error("Kunde inte hämta företagsinformation:", error);

        setCompanyError("Kunde inte hämta företagsinformationen.");
      } finally {
        setLoadingCompany(false);
      }
    }

    fetchCompany();
  }, []);

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

  // Builds the payload the signatory signs. Shared by step 4 (which validates
  // it before letting the user through) and step 5 (which signs and submits
  // it), so the two steps can never disagree about what is being signed.
  const buildApplicationRequest = () =>
    ApplicationRequestSchema.safeParse({
      equity: financialMetrics.equity,
      totalCapital: financialMetrics.totalCapital,
      currentAssets: financialMetrics.currentAssets,
      currentLiabilities: financialMetrics.currentLiabilities,
      totalLiabilities: financialMetrics.totalLiabilities,
      operatingIncome: financialMetrics.operatingIncome,
      netRevenue: financialMetrics.netRevenue,
      requestedAmount: creditAmount.requestedAmount,
      purpose: creditAmount.purpose,
    });

  // Validates step 4 before the user proceeds to signing. Nothing is submitted
  // here: the application only leaves the browser once it is signed.
  const handleConfirmationSubmit = () => {
    const result = confirmationSchema.safeParse(confirmation);

    if (!result.success) {
      console.log(result.error);
      return;
    }

    const applicationResult = buildApplicationRequest();

    if (!applicationResult.success) {
      console.log(applicationResult.error);
      return;
    }

    handleStepChange(5);
  };

  // Signs with BankID and submits as part of the same call. Rejects so the
  // signing step can show the failure and let the user retry.
  const handleSign = async () => {
    const applicationResult = buildApplicationRequest();

    if (!applicationResult.success) {
      console.log(applicationResult.error);
      throw new Error("Ansökan kunde inte valideras.");
    }

    try {
      const applicationId = await applicationApi.create(
        applicationResult.data,
      );

      console.log("Kreditansökan skapad:", applicationId);

      navigate(`/status/${applicationId}`);
    } catch (error) {
      console.error("Kunde inte skapa kreditansökan:", error);
      throw error;
    }
  };

  const steps = [
    "1. Företagsuppgifter",
    "2. Finansiella nyckeltal",
    "3. Kreditbelopp",
    "4. Bekräftelse",
    "5. Signera med BankID",
  ];

  if (loadingCompany) {
    return (
      <main className={styles.container}>
        <p>Laddar företagsinformation...</p>
      </main>
    );
  }

  if (companyError) {
    return (
      <main className={styles.container}>
        <p>{companyError}</p>
      </main>
    );
  }

  return (
    <main className={styles.container}>
      <h2 className={styles.h2}>Ny kreditansökan</h2>
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

      {currentStep === 5 && (
        <BankIdSignature
          companyName={companyInformation.companyName}
          orgNumber={companyInformation.orgNumber}
          onPrevious={() => handleStepChange(4)}
          onSign={handleSign}
        />
      )}
    </main>
  );
}
