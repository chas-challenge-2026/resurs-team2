import type { Application } from "./application";
import type { ApplicationDocument } from "./document";

export interface FinancialData {
  equity: number;
  totalCapital: number;
  currentAssets: number;
  currentLiabilities: number;
  totalLiabilities: number;
  operatingIncome: number;
  netRevenue: number;
  requestAmount: number;
  operatingCashFlow: number;
  investingCashFlow: number;
  interestExpenses: number;
  industry: string;
}

export interface ApplicationDetails {
  application: Application;
  financialData: string | null;
  workerName: string | null;
  documents: ApplicationDocument[];
}