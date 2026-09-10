export interface AuditLog {
  sequenceNumber: number;
  timestamp: string;
  entry: Record<string, unknown>;
}