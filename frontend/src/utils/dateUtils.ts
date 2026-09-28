export const formatTime = (date: string): string => {
    return new Date(date).toLocaleTimeString("sv-SE", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
    });
};