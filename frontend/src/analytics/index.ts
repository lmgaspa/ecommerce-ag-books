// src/analytics/index.ts
import type { AnalyticsItem, AnalyticsPort } from "./AnalyticsPort";
import { GA4Analytics } from "./ga4/GA4Analytics";

/** Facade estável para o app (OCP): trocar implementação sem tocar nas telas. */
export const analytics: AnalyticsPort = new GA4Analytics();

/** Helpers de mapeamento (não dependem de GA) */
export function mapCartItems(
  items: Array<{ id: string; title: string; price: number; quantity: number; category?: string; discount?: number; }>
): AnalyticsItem[] {
  return items.map((i) => ({
    item_id: i.id,
    item_name: i.title,
    price: Number(i.price),
    quantity: Number(i.quantity),
    item_category: i.category,
    discount: i.discount,
  }));
}
