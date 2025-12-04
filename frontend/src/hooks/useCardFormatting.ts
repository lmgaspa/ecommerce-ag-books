import { useMemo } from "react";
import type { CardBrand } from "../services/efiCard";

export interface CardData {
  number: string;
  holderName: string;
  expirationMonth: string;
  expirationYear: string;
  cvv: string;
  brand: CardBrand;
}

/**
 * Hook para formatação de dados de cartão
 */
export const useCardFormatting = (brand: CardBrand) => {
  const formatCardNumber = useMemo(
    () => (value: string): string => {
      const digits = value.replace(/\D/g, "");
      if (brand === "amex") {
        const d = digits.slice(0, 15);
        return d
          .replace(/^(\d{1,4})(\d{1,6})?(\d{1,5})?$/, (_, a, b, c) =>
            [a, b, c].filter(Boolean).join(" ")
          )
          .trim();
      }
      return digits.slice(0, 16).replace(/(\d{4})(?=\d)/g, "$1 ").trim();
    },
    [brand]
  );

  const formatMonth = useMemo(
    () => (value: string): string => {
      let d = value.replace(/\D/g, "").slice(0, 2);
      if (d.length === 1) {
        if (Number(d) > 1) d = `0${d}`;
      } else if (d.length === 2) {
        const n = Number(d);
        if (n === 0) d = "01";
        else if (n > 12) d = "12";
      }
      return d;
    },
    []
  );

  const formatYear = useMemo(
    () => (value: string): string => {
      return value.replace(/\D/g, "").slice(0, 4);
    },
    []
  );

  const formatCvv = useMemo(
    () => (value: string): string => {
      const max = brand === "amex" ? 4 : 3;
      return value.replace(/\D/g, "").slice(0, max);
    },
    [brand]
  );

  const toYYYY = useMemo(
    () => (yyOrYYYY: string): string => {
      const d = yyOrYYYY.replace(/\D/g, "");
      return d.length === 2 ? `20${d}` : d.slice(0, 4);
    },
    []
  );

  return {
    formatCardNumber,
    formatMonth,
    formatYear,
    formatCvv,
    toYYYY,
  };
};
