import { z } from "zod";

export const documentTypeSchema = z.enum([
  "arsredovisning",
  "fskattebevis",
  "bolagsordning",
  "ovrigt",
]);

export const documentUploadSchema = z.object({
  applicationId: z.string().min(1, {
    error: "Ansöknings-ID saknas.",
  }),

  docType: documentTypeSchema,

  file: z
    .instanceof(File, {
      error: "Välj en PDF-fil.",
    })
    .refine((file) => file.type === "application/pdf", {
      error: "Filen måste vara en PDF.",
    })
    .refine((file) => file.size <= 10 * 1024 * 1024, {
      error: "Filen får vara högst 10 MB.",
    }),
});

export const documentSchema = z.object({
  id: z.string(),
  filename: z.string(),
  docType: documentTypeSchema,
  uploadedAt: z.string(),
});

export type DocumentType = z.infer<
typeof documentTypeSchema >;

export type DocumentUploadData = z.infer<
typeof documentUploadSchema >;

export type DocumentData = z.infer<
typeof documentSchema >;