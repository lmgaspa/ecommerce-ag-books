// src/components/AnalyticsGate.tsx
import { useEffect, useMemo, useRef } from "react";
import { useLocation } from "react-router-dom";

declare global {
  interface Window {
    dataLayer?: unknown[];
    gtag?: (...args: unknown[]) => void;
  }
}

/** Lê o cookie "cookie_consent" (true/false). */
function hasConsent(): boolean {
  return document.cookie
    .split(";")
    .some((c) => c.trim().startsWith("cookie_consent=true"));
}

/** Injeta GA4 de forma idempotente. */
function loadGA4Once(measurementId: string): void {
  if (!measurementId) {
    console.info("[AnalyticsGate] VITE_GA4_ID ausente; não carregando GA.");
    return;
  }
  if (typeof window === "undefined") return;
  if (window.dataLayer) return; // já carregado

  // script externo
  const s1 = document.createElement("script");
  s1.async = true;
  s1.src = `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(
    measurementId
  )}`;
  s1.setAttribute("data-analytics", "ga4");
  document.head.appendChild(s1);

  // bootstrap + config
  const s2 = document.createElement("script");
  s2.setAttribute("data-analytics", "ga4-init");
  s2.innerHTML = `
    window.dataLayer = window.dataLayer || [];
    function gtag(){ dataLayer.push(arguments); }
    gtag('js', new Date());
    // SPA: desativa page_view automático para evitar duplicidade
    gtag('config', '${measurementId}', { anonymize_ip: true, send_page_view: false });
  `;
  document.head.appendChild(s2);

  console.info(`[AnalyticsGate] GA4 carregado (${measurementId}).`);
}

type AnalyticsGateProps = {
  /** Força consentimento externamente (opcional). */
  forcedConsent?: boolean;
  /** Envia page_view a cada navegação (SPA). Default: true */
  trackPageviews?: boolean;
  /** ID do GA4 (fallback para VITE_GA4_ID). */
  measurementId?: string;
};

export default function AnalyticsGate({
  forcedConsent,
  trackPageviews = true,
  measurementId,
}: AnalyticsGateProps) {
  const gaId =
    measurementId ??
    (import.meta.env.VITE_GA4_ID as string | undefined) ??
    "";

  const location = useLocation();
  const loadedRef = useRef(false);

  const consentOk = useMemo(
    () => (typeof forcedConsent === "boolean" ? forcedConsent : hasConsent()),
    [forcedConsent]
  );

  // Carrega GA4 uma única vez quando houver consentimento
  useEffect(() => {
    if (!consentOk || loadedRef.current) return;
    loadGA4Once(String(gaId));
    loadedRef.current = true;
  }, [consentOk, gaId]);

  // Dispara page_view em SPA (rota mudou)
  useEffect(() => {
    if (!trackPageviews) return;
    if (!consentOk) return;
    const gtag = window.gtag;
    if (!gtag) return; // ainda não carregou

    gtag("event", "page_view", {
      page_location: window.location.href,
      page_path: `${location.pathname}${location.search}`,
      page_title: document.title,
    } as Record<string, unknown>);
  }, [location, consentOk, trackPageviews]);

  return null;
}
