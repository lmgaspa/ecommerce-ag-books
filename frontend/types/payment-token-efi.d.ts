/* eslint-disable no-var */

// ====== Tipos comuns (globais para reuso) ======
declare type EfiEnv = "production" | "sandbox";
declare type CardBrand = "visa" | "mastercard" | "amex" | "elo" | "diners";

declare interface InstallmentItem {
  installment: number;
  has_interest: boolean;
  value: number;       // centavos
  currency: string;    // ex.: "239,94"
  interest_percentage: number;
}

declare interface InstallmentsResp {
  rate: number;
  name: string;        // brand
  installments: InstallmentItem[];
}

declare interface TokenizeInput {
  brand: CardBrand;
  number: string;           // só dígitos
  cvv: string;              // 3 ou 4
  expirationMonth: string;  // "MM"
  expirationYear: string;   // "YYYY"
  holderName: string;
  holderDocument: string;   // CPF/CNPJ só dígitos
  reuse?: boolean;
}

declare interface TokenizeResult {
  payment_token: string;
  card_mask: string;
}

// ====== API NPM/ESM/CJS ======
declare interface EfiCreditCardAPI {
  // Globais úteis
  isScriptBlocked(): Promise<boolean>;
  debugger(enable: boolean): void;

  // Detectar bandeira
  setCardNumber(cardNumberOnlyDigits: string): EfiCreditCardAPI;
  verifyCardBrand(): Promise<CardBrand | "unsupported" | "undefined">;

  // Parcelas
  setAccount(payeeCode: string): EfiCreditCardAPI;
  setEnvironment(env: EfiEnv): EfiCreditCardAPI;
  setBrand(brand: CardBrand): EfiCreditCardAPI;
  setTotal(totalInCents: number): EfiCreditCardAPI;
  getInstallments(): Promise<InstallmentsResp>;

  // Tokenização
  setCreditCardData(data: TokenizeInput): EfiCreditCardAPI;
  getPaymentToken(): Promise<TokenizeResult>;
}

declare interface EfiPayRoot {
  CreditCard: EfiCreditCardAPI;
}

/** Suporte ao import default: `import EfiPay from "payment-token-efi"` */
declare module "payment-token-efi" {
  const EfiPay: EfiPayRoot;
  export default EfiPay;
}

// ====== Modo CDN/jQuery ($gn.ready) - opcional ======
declare interface GnTokenWrapped {
  code?: number;
  data?: {
    payment_token?: string;
    card_mask?: string;
    rate?: number;
    name?: string;
    installments?: InstallmentItem[];
  };
}

declare interface GnError {
  code?: number;
  error?: string;
  error_description?: string;
}

declare interface GnCheckout {
  /** Gera o token de pagamento (token em `response.data.payment_token`) */
  getPaymentToken(
    params: {
      brand: CardBrand;
      number: string;            // só dígitos
      cvv: string;               // 3 ou 4
      expiration_month: string;  // "MM"
      expiration_year: string;   // "YYYY" recomendado
      reuse?: boolean;
    },
    callback: (error: GnError | null, response: GnTokenWrapped | null) => void
  ): void;

  /** Parcelas conforme conta/configuração */
  getInstallments(
    totalInCents: number,
    brand: CardBrand,
    callback: (error: GnError | null, response: GnTokenWrapped | null) => void
  ): void;
}

declare interface GnGlobal {
  validForm?: boolean;
  processed?: boolean;
  done?: unknown;
  ready(fn: (checkout: GnCheckout) => void): void;
}

declare global {
  // Disponibilizado pelo script da Efí (CDN)
  var $gn: GnGlobal | undefined;

  interface Window {
    $gn?: GnGlobal;
  }
}