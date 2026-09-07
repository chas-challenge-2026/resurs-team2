import type { Application } from "./application";
import type { ApplicationDocument } from "./document";

export interface ApplicationDetails {
  application: Application;
  auditLogRaw: string;
  workerName: string;
  documents: ApplicationDocument[];
}