// src/services/efiCard.ts
import EfiPay from "payment-token-efi";

export type EfiEnv = "production" | "sandbox";
export type CardBrand = "visa" | "mastercard" | "amex" | "elo" | "diners";

export interface InstallmentItem {
  installment: number;
  has_interest: boolean;
  value: number;        // in cents
  currency: string;     // e.g.: "239,94"
  interest_percentage: number;
}

export interface InstallmentsResp {
  rate: number;
  name: string; // brand
  installments: InstallmentItem[];
}

export interface TokenizeInput {
  brand: CardBrand;
  number: string;          // digits only
  cvv: string;             // 3 or 4
  expirationMonth: string; // "MM"
  expirationYear: string;  // "YYYY"
  holderName: string;
  holderDocument: string;  // CPF/CNPJ digits only (required)
  reuse?: boolean;
}

export interface TokenizeResult {
  payment_token: string;
  card_mask: string;
}

/* ===================== ENV bindings (match your .env) ===================== */

const ENV = (import.meta as unknown as { env?: Record<string, string | undefined> }).env ?? {};

// Your .env:
// VITE_EFI_PAYEE_CODE=b348a05b9c0391c8097ab315eb3b4d56
// VITE_EFI_SANDBOX=false  => forces PRODUCTION here
export const EFI_ACCOUNT =
  (ENV.VITE_EFI_PAYEE_CODE ?? "").trim() || "b348a05b9c0391c8097ab315eb3b4d56";

export const EFI_ENV: EfiEnv =
  String(ENV.VITE_EFI_SANDBOX ?? "false").toLowerCase() === "true"
    ? "sandbox"
    : "production";

/* ===================== Debug & utilities ===================== */

/** Toggle Efí debug logs (handy on sandbox). */
export const EfiDebugger = (enable: boolean): void => {
  (EfiPay.CreditCard as unknown as { debugger(enable: boolean): void }).debugger(enable);
};

/** Detects if Efí fingerprint/script is being blocked by an extension/adblock. */
export const isScriptBlocked = (): Promise<boolean> =>
  EfiPay.CreditCard.isScriptBlocked();

/** Detect card brand from card number (digits only). */
export const verifyBrandFromNumber = async (
  cardNumberOnlyDigits: string
): Promise<CardBrand | "unsupported" | "undefined"> => {
  const brand = await EfiPay.CreditCard
    .setCardNumber(cardNumberOnlyDigits)
    .verifyCardBrand();
  return brand as CardBrand | "unsupported" | "undefined";
};

/* ===================== Core (explicit account/env) ===================== */

export const getInstallments = async (
  account: string,
  env: EfiEnv,
  brand: CardBrand,
  totalInCents: number
): Promise<InstallmentsResp> => {
  const cents = Math.max(0, Math.floor(Number(totalInCents) || 0));
  if (cents <= 0) return { rate: 0, name: brand, installments: [] };

  const resp = await EfiPay.CreditCard
    .setAccount(account)
    .setEnvironment(env)
    .setBrand(brand)
    .setTotal(cents)
    .getInstallments();

  return resp as InstallmentsResp;
};

export const tokenize = async (
  account: string,
  env: EfiEnv,
  data: TokenizeInput
): Promise<TokenizeResult> => {
  const result = await EfiPay.CreditCard
    .setAccount(account)
    .setEnvironment(env)
    .setCreditCardData({
      brand: data.brand,
      number: data.number,
      cvv: data.cvv,
      expirationMonth: data.expirationMonth,
      expirationYear: data.expirationYear,
      holderName: data.holderName,
      holderDocument: data.holderDocument,
      reuse: !!data.reuse,
    })
    .getPaymentToken();

  return result as TokenizeResult;
};

/* ===================== Convenience (auto-ENV from .env) ===================== */
/* Use these if you don’t want to pass account/env on every call. */

export const getInstallmentsEnv = (
  brand: CardBrand,
  totalInCents: number
) => getInstallments(EFI_ACCOUNT, EFI_ENV, brand, totalInCents);

export const tokenizeEnv = (data: TokenizeInput) =>
  tokenize(EFI_ACCOUNT, EFI_ENV, data);
