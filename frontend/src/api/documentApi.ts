import { apiFetch } from "./apiFetch";
import type { ApplicationDocument } from "../types/document";

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
  docType: string,
  file: File,
): Promise<ApplicationDocument> {
  const formData = new FormData();

  formData.append("id", String(applicationId));
  formData.append("docType", docType);
  formData.append("file", file);

  const response = await apiFetch(
    `/api/v1/applications/${applicationId}/documents`,
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

  async downloadDocument(
    documentId: number | string,
  ): Promise<Blob> {
    const response = await apiFetch(
      `/api/v1/documents/${documentId}`,
    );

    if (!response.ok) {
      throw new Error("Kunde inte ladda ner dokumentet.");
    }

    return response.blob();
  },

  async deleteDocument(
    documentId: number | string,
  ): Promise<void> {
    const response = await apiFetch(
      `/api/v1/documents/${documentId}`,
      {
        method: "DELETE",
      },
    );

    if (!response.ok) {
      throw new Error("Kunde inte ta bort dokumentet.");
    }
  },
};