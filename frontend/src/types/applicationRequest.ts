export interface ApplicationRequest {
  orgNumber: string;
  companyName: string;
  authorizedSignature: string;
  equity: number;
  totalCapital: number;
  currentAssets: number;
  currentLiabilities: number;
  totalLiabilities: number;
  operatingIncome: number;
  netRevenue: number;
  requestedAmount: number;
  purpose: string;
  operatingCashFlow?: number;
  investingCashFlow?: number;
  interestExpenses?: number;
  industry?: string;
}
