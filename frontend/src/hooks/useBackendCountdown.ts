import { useEffect, useState } from "react";
import { useNow } from "./useNow";

export function useBackendCountdown(remainingSeconds?: number | null) {
  const now = useNow();
  const [base, setBase] = useState<{ receivedAt: number; remainingSeconds: number } | null>(null);

  useEffect(() => {
    if (remainingSeconds == null || !Number.isFinite(remainingSeconds)) {
      setBase(null);
      return;
    }

    setBase({
      receivedAt: Date.now(),
      remainingSeconds: Math.max(0, remainingSeconds)
    });
  }, [remainingSeconds]);

  if (!base) return undefined;

  const elapsedSeconds = Math.floor((now - base.receivedAt) / 1000);
  return Math.max(0, base.remainingSeconds - elapsedSeconds);
}
