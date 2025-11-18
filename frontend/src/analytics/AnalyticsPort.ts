// src/analytics/AnalyticsPort.ts
export type AnalyticsItem = {
  item_id: string;
  item_name: string;
  price: number;
  quantity: number;
  item_category?: string;
  discount?: number;
};

export interface AnalyticsPort {
  addToCart(params: {
    items: AnalyticsItem[];
    value: number;
    currency?: string;
  }): void;

  addShippingInfo(params: {
    items: AnalyticsItem[];
    value: number;
    shipping: number;
    shipping_tier?: string;
    currency?: string;
  }): void;

  beginCheckout(params: {
    items: AnalyticsItem[];
    value: number;
    coupon?: string;
    currency?: string;
  }): void;

  addPaymentInfo(params: {
    items: AnalyticsItem[];
    value: number;
    payment_type: string;
    installments?: number;
    currency?: string;
  }): void;

  // 🔽 aqui entra a extensão para suportar método de pagamento e multi-autor
  purchase(params: {
    transaction_id: string;
    value: number;
    currency?: string;
    shipping?: number;
    tax?: number;
    items: AnalyticsItem[];

    // novos, opcionais → não quebram nada existente
    payment_type?: string; // "pix" | "credit_card" | etc.
    author_id?: number;    // pronto para multi-autor
  }): void;

  pageView?(params: {
    page_location: string;
    page_path: string;
    page_title?: string;
  }): void;
}
