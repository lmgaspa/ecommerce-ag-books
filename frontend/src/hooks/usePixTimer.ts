import { useState, useEffect, useRef } from "react";

interface UsePixTimerParams {
  expiresAtMs: number | null;
  onExpire?: () => void;
}

interface UsePixTimerResult {
  remainingSec: number;
  isExpired: boolean;
  formattedTime: string;
}

function formatMMSS(totalSec: number): string {
  const m = Math.floor(totalSec / 60);
  const s = totalSec % 60;
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}

/**
 * Hook para gerenciar timer de expiração do Pix
 */
export const usePixTimer = ({ expiresAtMs, onExpire }: UsePixTimerParams): UsePixTimerResult => {
  const [remainingSec, setRemainingSec] = useState<number>(0);
  const [isExpired, setIsExpired] = useState(false);
  const timerRef = useRef<number | null>(null);

  useEffect(() => {
    if (!expiresAtMs) return;
    if (timerRef.current) {
      window.clearInterval(timerRef.current);
      timerRef.current = null;
    }

    const tick = () => {
      const sec = Math.max(0, Math.floor((expiresAtMs - Date.now()) / 1000));
      setRemainingSec(sec);
      if (sec <= 0) {
        setIsExpired(true);
        if (onExpire) onExpire();
        if (timerRef.current) {
          window.clearInterval(timerRef.current);
          timerRef.current = null;
        }
      }
    };
    tick();
    timerRef.current = window.setInterval(tick, 1000);

    return () => {
      if (timerRef.current) {
        window.clearInterval(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [expiresAtMs, onExpire]);

  return {
    remainingSec,
    isExpired,
    formattedTime: formatMMSS(remainingSec),
  };
};

