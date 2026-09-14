import { z } from "zod";

export const ApplicationRequestSchema = z.object({
  equity: z.number(),
  totalCapital: z.number(),
  currentAssets: z.number(),
  currentLiabilities: z.number(),
  totalLiabilities: z.number(),
  operatingIncome: z.number(),
  netRevenue: z.number(),
  requestedAmount: z.number(),
  purpose: z.string(),
  operatingCashFlow: z.number().optional(),
  investingCashFlow: z.number().optional(),
  interestExpenses: z.number().optional(),
  industry: z.string().optional(),
})

export type ApplicationRequest = z.infer<typeof ApplicationRequestSchema>;