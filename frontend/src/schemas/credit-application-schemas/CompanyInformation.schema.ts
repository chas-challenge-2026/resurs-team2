import { z } from "zod";

export const companyInformationSchema = z.object({
  orgNumber: z.string().min(1, "Organisationsnummer saknas."),
  companyName: z.string().min(1, "Företagsnamn måste anges."),
  
});

export type CompanyInformationData = z.infer<typeof companyInformationSchema>;