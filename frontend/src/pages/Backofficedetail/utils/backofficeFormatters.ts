import type { Application } from "../../../types/application";

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

export const formatStatus = (status: Application["status"]) => {
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

export const getStatusBadgeClass = (status: Application["status"]) => {
  switch (status) {
    case "APPROVED":
      return "label-success";

    case "REJECTED":
      return "label-danger";

    case "UNDER_REVIEW":
      return "label-warning";

    default:
      return "label-default";
  }
};

export const getScoringBadgeClass = (score?: string | null) => {
  if (!score) {
    return "label-default";
  }

  const uppercaseScore = score.toUpperCase();

  if (uppercaseScore.includes("GREEN")) {
    return "label-success";
  }

  if (uppercaseScore.includes("YELLOW")) {
    return "label-warning";
  }

  if (uppercaseScore.includes("RED")) {
    return "label-danger";
  }

  return "label-default";
};
