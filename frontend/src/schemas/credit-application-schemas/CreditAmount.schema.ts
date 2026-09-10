import { z } from "zod";

export const creditAmountSchema = z.object({
  requestedAmount: z
    .number({error: "Ange önskat kreditbelopp.",})
    .min(50_000, "Kreditbeloppet måste vara minst 50 000 kr.")
    .max(10_000_000, "Kreditbeloppet får vara högst 10 000 000 kr.")
    .multipleOf(1_000, "Kreditbeloppet måste anges i hela tusental."),

  purpose: z
    .string({error: "Ange syftet med krediten.",})
    .trim()
    .min(10, "Beskriv syftet med minst 10 tecken.")
    .max(1000, "Syftet får vara högst 1000 tecken."),
});

export type CreditAmountData = z.infer<typeof creditAmountSchema>;