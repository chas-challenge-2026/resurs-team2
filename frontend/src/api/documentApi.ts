import { apiFetch } from "./apiFetch";
import type { ApplicationDocument } from "../types/document";
import type { DocumentType } from "../schemas/Documents.schema";

export const documentApi = {
  async getAllDocuments(
    applicationId: number | string,
  ): Promise<ApplicationDocument[]> {
    const response = await apiFetch(
      `/api/v1/applications/${applicationId}/documents`,
    );

    if (!response.ok) {
      throw new Error("Kunde inte hämta dokumenten.");
    }

    return response.json();
  },

  async uploadDocument(
    applicationId: number | string,
    docType: DocumentType,
    file: File,
  ): Promise<ApplicationDocument> {
    const formData = new FormData();

    formData.append("file", file);

    const response = await apiFetch(
      `/api/v1/applications/${applicationId}/documents?docType=${encodeURIComponent(docType)}`,
      {
        method: "POST",
        body: formData,
      },
    );

    if (!response.ok) {
      const errorText = await response.text();

      throw new Error(
        `Kunde inte ladda upp dokumentet. Status: ${response.status}. ${errorText}`,
      );
    }

    return response.json();
  },

  downloadDocument: async (uuid: string): Promise<Blob> => {
    const response = await apiFetch(`/api/v1/documents/${uuid}`);

    if (!response.ok) {
      throw new Error("Kunde inte ladda ner dokumentet.");
    }

    return response.blob();
  },

  deleteDocument: async (uuid: string): Promise<void> => {
    const response = await apiFetch(`/api/v1/documents/${uuid}`, {
      method: "DELETE",
    });

    if (!response.ok) {
      throw new Error("Kunde inte ta bort dokumentet.");
    }
  }
}