import { z } from "zod";

export const companyLoginSchema = z.object({
  orgNumber: z
    .string()
    .trim()
    .regex(
      /^\d{6}-\d{4}$/,
      "Ange organisationsnummer i formatet 556000-1234.",
    ),
});

export const caseworkerLoginSchema = z.object({
  email: z
    .string()
    .trim()
    .pipe(z.email("Ange en giltig e-postadress.")),
  password: z
    .string()
    .min(1, "Ange lösenord."),
});