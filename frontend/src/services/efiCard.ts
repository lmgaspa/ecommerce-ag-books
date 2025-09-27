import EfiPay from "payment-token-efi";

export type EfiEnv = "production" | "sandbox";
export type CardBrand = "visa" | "mastercard" | "amex" | "elo" | "diners";

export interface InstallmentItem {
  installment: number;
  has_interest: boolean;
  value: number;        // em centavos
  currency: string;     // ex.: "239,94"
  interest_percentage: number;
}

export interface InstallmentsResp {
  rate: number;
  name: string; // brand
  installments: InstallmentItem[];
}

export interface TokenizeInput {
  brand: CardBrand;
  number: string;          // só dígitos
  cvv: string;             // 3 ou 4
  expirationMonth: string; // "MM"
  expirationYear: string;  // "YYYY"
  holderName: string;
  holderDocument: string;  // CPF/CNPJ só dígitos (obrigatório)
  reuse?: boolean;
}

export interface TokenizeResult {
  payment_token: string;
  card_mask: string;
}

/** Evita `any` e mantém acesso ao método "debugger" da lib */
interface HasDebugger {
  debugger(enable: boolean): void;
}

/** Ativa/desativa logs de debug da Efí (útil em sandbox) */
export const EfiDebugger = (enable: boolean): void => {
  (EfiPay.CreditCard as unknown as HasDebugger).debugger(enable);
};

/** Verifica se o fingerprint/script está bloqueado por extensão/adblock */
export const isScriptBlocked = (): Promise<boolean> =>
  EfiPay.CreditCard.isScriptBlocked();

/** Detecta a bandeira real a partir do número do cartão (apenas dígitos) */
export const verifyBrandFromNumber = async (
  cardNumberOnlyDigits: string
): Promise<CardBrand | "unsupported" | "undefined"> => {
  const brand = await EfiPay.CreditCard
    .setCardNumber(cardNumberOnlyDigits)
    .verifyCardBrand();
  return brand as CardBrand | "unsupported" | "undefined";
};

/** Busca as parcelas oficiais conforme a configuração da sua conta Efí */
export const getInstallments = async (
  account: string,
  env: EfiEnv,
  brand: CardBrand,
  totalInCents: number
): Promise<InstallmentsResp> => {
  const resp = await EfiPay.CreditCard
    .setAccount(account)
    .setEnvironment(env)
    .setBrand(brand)
    .setTotal(totalInCents)
    .getInstallments();
  return resp as InstallmentsResp;
};

/** Gera o payment_token (retorno FLAT: { payment_token, card_mask }) */
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