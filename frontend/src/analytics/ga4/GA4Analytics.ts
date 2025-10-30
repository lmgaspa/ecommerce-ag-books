// src/analytics/ga4/GA4Analytics.ts
import type { AnalyticsItem, AnalyticsPort } from "../AnalyticsPort";

declare global {
  interface Window {
    gtag?: (...args: unknown[]) => void;
  }
}

/** Implementação concreta para GA4. Abordagem fail-safe: se gtag não existir, vira no-op. */
export class GA4Analytics implements AnalyticsPort {
  private readonly currencyDefault = "BRL";

  private gtagSafe(): ((...args: unknown[]) => void) | null {
    return typeof window !== "undefined" && typeof window.gtag === "function"
      ? window.gtag
      : null;
  }

  private send(event: string, params: Record<string, unknown>): void {
    const gtag = this.gtagSafe();
    if (!gtag) return; // sem consentimento/GA carregado => no-op
    gtag("event", event, params);
  }

  addToCart(params: { items: AnalyticsItem[]; value: number; currency?: string; }): void {
    this.send("add_to_cart", {
      currency: params.currency ?? this.currencyDefault,
      value: Number(params.value),
      items: params.items,
    });
  }

  addShippingInfo(params: {
    items: AnalyticsItem[];
    value: number;
    shipping: number;
    shipping_tier?: string;
    currency?: string;
  }): void {
    this.send("add_shipping_info", {
      currency: params.currency ?? this.currencyDefault,
      value: Number(params.value),
      shipping: Number(params.shipping),
      shipping_tier: params.shipping_tier,
      items: params.items,
    });
  }

  beginCheckout(params: {
    items: AnalyticsItem[];
    value: number;
    coupon?: string;
    currency?: string;
  }): void {
    this.send("begin_checkout", {
      currency: params.currency ?? this.currencyDefault,
      value: Number(params.value),
      coupon: params.coupon,
      items: params.items,
    });
  }

  addPaymentInfo(params: {
    items: AnalyticsItem[];
    value: number;
    payment_type: string;
    installments?: number;
    currency?: string;
  }): void {
    this.send("add_payment_info", {
      currency: params.currency ?? this.currencyDefault,
      value: Number(params.value),
      payment_type: params.payment_type,
      installments: params.installments,
      items: params.items,
    });
  }

  purchase(params: {
    transaction_id: string;
    value: number;
    currency?: string;
    shipping?: number;
    tax?: number;
    items: AnalyticsItem[];
  }): void {
    this.send("purchase", {
      transaction_id: params.transaction_id,
      value: Number(params.value),
      currency: params.currency ?? this.currencyDefault,
      shipping: Number(params.shipping ?? 0),
      tax: Number(params.tax ?? 0),
      items: params.items,
    });
  }

  pageView(params: { page_location: string; page_path: string; page_title?: string; }): void {
    this.send("page_view", params);
  }
}
