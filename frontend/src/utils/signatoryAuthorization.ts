export const canSignForOrganization = (
  personalNumber: string,
  orgNumber: string,
): boolean => {
  return orgNumber === "556000-1234" && personalNumber === "20000101-1111";
};
