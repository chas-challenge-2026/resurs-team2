export type RejectionReasonType = "REJECT" | "WARNING";

export interface RejectionReason {
  type: RejectionReasonType;
  text: string;
}

const NON_ACTIONABLE_PATTERNS = [
  /Soliditet OK/,
  /Skuldsättningsgrad OK.*?(?=(?:AVSLAG|VARNING):|$)/,
  /Likviditetsgrad (?:god|godkänd).*?(?=(?:AVSLAG|VARNING):|$)/,
  /Rörelseresultatmarginal (?:god|godkänd).*?(?=(?:AVSLAG|VARNING):|$)/,
  /Kassaflödeskvot OK.*?(?=(?:AVSLAG|VARNING):|$)/,
  /Räntetäckningsgrad OK.*?(?=(?:AVSLAG|VARNING):|$)/,
  /Räntetäckningsgrad ej tillämplig.*?(?=(?:AVSLAG|VARNING):|$)/,
];

const removeNonActionableMessages = (reason: string): string => {
  return NON_ACTIONABLE_PATTERNS.reduce(
    (result, pattern) => result.replace(pattern, ""),
    reason,
  );
};

export const parseRejectionReason = (
  decisionReason: string,
): RejectionReason[] => {
  const normalizedReason = removeNonActionableMessages(
    decisionReason
      .replace(/^=== ANSÖKAN AVSLAGEN ===\s*/i, "")
      .replace(/\s+/g, " ")
      .trim(),
  );

  const matches = normalizedReason.matchAll(
    /(AVSLAG|VARNING):\s*(.*?)(?=\s+(?:AVSLAG|VARNING):|$)/gi,
  );

  const reasons: RejectionReason[] = [];

  for (const match of matches) {
    const [, type, text] = match;

    if (!type || !text) {
      continue;
    }

    reasons.push({
      type: type.toUpperCase() === "AVSLAG" ? "REJECT" : "WARNING",
      text: text.trim(),
    });
  }

  if (reasons.length > 0) {
    return reasons;
  }

  const fallbackReason = normalizedReason
    .replace(/^=== ANSÖKAN AVSLAGEN ===\s*/i, "")
    .trim();

  if (!fallbackReason) {
    return [];
  }

  return [
    {
      type: "REJECT",
      text: fallbackReason,
    },
  ];
};