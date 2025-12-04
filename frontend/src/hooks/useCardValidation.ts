import { useMemo } from "react";
import type { CardBrand } from "../services/efiCard";
import type { CardData } from "./useCardFormatting";

/**
 * Validação Luhn (algoritmo de checksum de cartão)
 */
const isValidLuhn = (numDigits: string): boolean => {
  let sum = 0;
  let dbl = false;
  for (let i = numDigits.length - 1; i >= 0; i--) {
    let n = Number(numDigits[i]);
    if (dbl) {
      n *= 2;
      if (n > 9) n -= 9;
    }
    sum += n;
    dbl = !dbl;
  }
  return sum % 10 === 0;
};

/**
 * Hook para validação de dados de cartão
 */
export const useCardValidation = (card: CardData, brand: CardBrand, holderDocument: string) => {
  const numberDigits = useMemo(() => card.number.replace(/\D/g, ""), [card.number]);
  
  const cvvLen = useMemo(() => (brand === "amex" ? 4 : 3), [brand]);

  const validations = useMemo(() => {
    // Validação de comprimento do número
    const lenOk =
      (brand === "amex" && numberDigits.length === 15) ||
      (brand !== "amex" && numberDigits.length >= 14 && numberDigits.length <= 16);

    // Validação Luhn
    const luhnOk = lenOk && isValidLuhn(numberDigits);

    // Validação do nome
    const holderOk = card.holderName.trim().length > 0;

    // Validação do mês
    const monthOk =
      /^\d{2}$/.test(card.expirationMonth) &&
      Number(card.expirationMonth) >= 1 &&
      Number(card.expirationMonth) <= 12;

    // Validação do ano
    const yearOk = /^\d{4}$/.test(card.expirationYear);

    // Validação do CVV
    const cvvOk = new RegExp(`^\\d{${cvvLen}}$`).test(card.cvv);

    // Validação do CPF (mínimo 11 dígitos)
    const docOk = holderDocument.length >= 11;

    return {
      isNumberValid: luhnOk,
      isHolderValid: holderOk,
      isExpirationValid: monthOk && yearOk,
      isCvvValid: cvvOk,
      isDocumentValid: docOk,
      // Validação geral
      isValid: luhnOk && holderOk && monthOk && yearOk && cvvOk && docOk,
    };
  }, [card, brand, cvvLen, numberDigits, holderDocument]);

  return {
    ...validations,
    numberDigits,
    cvvLen,
  };
};
