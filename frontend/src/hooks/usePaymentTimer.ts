import { useState, useEffect, useCallback } from "react";

interface PaymentTimerResult {
  timeLeft: number | null;
  showWarning: boolean;
  showSecurityWarning: boolean;
  isExpired: boolean;
  formattedTime: string;
}

/**
 * Hook para gerenciar timer de expiração de pagamento
 */
export const usePaymentTimer = (
  ttlSeconds: number | null,
  warningAt: number = 60,
  securityWarningAt: number = 60
): PaymentTimerResult => {
  const [timeLeft, setTimeLeft] = useState<number | null>(null);
  const [showWarning, setShowWarning] = useState(false);
  const [showSecurityWarning, setShowSecurityWarning] = useState(false);
  const [isExpired, setIsExpired] = useState(false);

  const formatTimeLeft = useCallback((seconds: number): string => {
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    return `${minutes}:${remainingSeconds.toString().padStart(2, "0")}`;
  }, []);

  useEffect(() => {
    if (!ttlSeconds) return;

    setTimeLeft(ttlSeconds);

    const interval = setInterval(() => {
      setTimeLeft((prev) => {
        if (prev === null) return null;
        const newTime = prev - 1;

        if (newTime <= securityWarningAt && !showSecurityWarning) {
          setShowSecurityWarning(true);
        }
        if (newTime <= warningAt && !showWarning) {
          setShowWarning(true);
        }
        if (newTime <= 0) {
          setIsExpired(true);
          setShowWarning(false);
          setShowSecurityWarning(false);
          return 0;
        }
        return newTime;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [ttlSeconds, warningAt, securityWarningAt, showWarning, showSecurityWarning]);

  return {
    timeLeft,
    showWarning,
    showSecurityWarning,
    isExpired,
    formattedTime: formatTimeLeft(timeLeft ?? 0),
  };
};
