import type { Application } from "@/types/application";

export const formatCurrency = (amount?: number) => {
  amount ??= 0;

  return new Intl.NumberFormat("sv-SE").format(amount) + " kr";
};

export const formatDateTime = (value?: string) => {
  if (!value) {
    return "-";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("sv-SE", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(date);
};

export const formatStatus = (
  status: Application["status"],
) => {
  switch (status) {
    case "PENDING_DOCS":
      return "Väntar på dokument";

    case "UNDER_REVIEW":
      return "Under granskning";

    case "APPROVED":
      return "Godkänd";

    case "REJECTED":
      return "Avslagen";

    default:
      return status;
  }
};

export const formatWorker = (
  status: Application["status"],
  workerName?: string,
) => {
  if (workerName) {
    return workerName;
  }

  if (status === "UNDER_REVIEW") {
    return "Väntar på tilldelning";
  }

  if (status === "APPROVED" || status === "REJECTED") {
    return "Automatisk review – ingen handläggare tilldelad";
  }

  return "Ej tilldelad";
};