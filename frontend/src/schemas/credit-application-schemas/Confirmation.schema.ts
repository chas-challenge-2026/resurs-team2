import { z } from "zod";

export const confirmationSchema = z.object({
  financialConfirmation: z
    .boolean()
    .refine((val) => val === true, {
      message: "Du måste intyga att de finansiella uppgifterna är korrekta.",
    }),
});

//Input type allows false before user press Confirm button
export type ConfirmationFormData = z.input<typeof confirmationSchema>;

// Output-typen är strikt true efter lyckad validering
export type ConfirmationData = z.output<typeof confirmationSchema>;
