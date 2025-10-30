// src/types/global.d.ts
export {};

declare global {
  interface Window {
    /** Usado pelo GA4 (gtag). */
    dataLayer?: unknown[];
    /** Função gtag injetada pelo script oficial do GA4. */
    gtag?: (...args: unknown[]) => void;
  }

  /** Permite tipar o evento custom "cookie-consent-changed". */
  interface WindowEventMap {
    "cookie-consent-changed": Event;
  }
}
