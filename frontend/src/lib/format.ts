export function formatEnum(value: string) {
  return value
    .toLowerCase()
    .split("_")
    .map((word) => word[0].toUpperCase() + word.slice(1))
    .join(" ");
}

export function formatDateTime(value?: string) {
  if (!value) return "Pending";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("en-US", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(date);
}

export function formatRemainingTime(expiresAt?: string, now = Date.now()) {
  if (!expiresAt) return "Waiting for expiration time";

  const expiresAtMs = new Date(expiresAt).getTime();
  if (Number.isNaN(expiresAtMs)) return "Waiting for expiration time";

  const remainingSeconds = Math.max(0, Math.ceil((expiresAtMs - now) / 1000));
  if (remainingSeconds <= 0) return "Expired";

  const minutes = Math.floor(remainingSeconds / 60);
  const seconds = remainingSeconds % 60;
  if (minutes <= 0) return `${seconds}s remaining`;

  return `${minutes}m ${seconds.toString().padStart(2, "0")}s remaining`;
}

export function formatRemainingSeconds(remainingSeconds?: number | null) {
  if (remainingSeconds == null || !Number.isFinite(remainingSeconds)) {
    return "Waiting for expiration time";
  }

  const normalizedSeconds = Math.max(0, Math.ceil(remainingSeconds));
  if (normalizedSeconds <= 0) return "Expired";

  const minutes = Math.floor(normalizedSeconds / 60);
  const seconds = normalizedSeconds % 60;
  if (minutes <= 0) return `${seconds}s remaining`;

  return `${minutes}m ${seconds.toString().padStart(2, "0")}s remaining`;
}

export function messageFrom(error: unknown) {
  if (error instanceof Error) {
    return error.message.replaceAll('"', "");
  }
  return "Request failed";
}
