import type { DocumentType } from "@/schemas/Documents.schema";

export interface ApplicationDocument {
  uuid: string;
  applicationId: number;
  filename: string;
  docType: DocumentType;
  uploadedAt: string;
}