export interface ApplicationStep {
    name: string;
    status: "DONE" | "CURRENT" | "PENDING";
    description: string;
    eta?: string;
}