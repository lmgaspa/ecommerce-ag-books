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
  if (window.dataLayer) return; // já carregado (evita duplicidade)

  // script externo
  const s1 = document.createElement("script");
  s1.async = true;
  s1.src = `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(
    measurementId
  )}`;
  s1.setAttribute("data-analytics", "ga4");
  document.head.appendChild(s1);

  // Detecta ambiente local para evitar "invalid domain" ao setar cookies do GA
  const isLocal =
    window.location.hostname === "localhost" ||
    window.location.hostname === "127.0.0.1";

  // bootstrap + config
  const s2 = document.createElement("script");
  s2.setAttribute("data-analytics", "ga4-init");
  s2.innerHTML = `
    window.dataLayer = window.dataLayer || [];
    function gtag(){ dataLayer.push(arguments); }
    gtag('js', new Date());
    // SPA: desativa page_view automático para evitar duplicidade
    gtag('config', '${measurementId}', {
      anonymize_ip: true,
      send_page_view: false,
      ${isLocal ? "cookie_domain: 'none'," : ""}
    });
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

  // Carrega GA4 quando houver consentimento
  useEffect(() => {
    if (!consentOk || loadedRef.current) return;
    loadGA4Once(String(gaId));
    loadedRef.current = true;
  }, [consentOk, gaId]);

  // Reage a mudanças de consentimento em tempo real (evento custom)
  useEffect(() => {
    const onConsent = () => {
      if (!loadedRef.current && hasConsent()) {
        loadGA4Once(String(gaId));
        loadedRef.current = true;
      }
    };
    window.addEventListener("cookie-consent-changed", onConsent);
    return () =>
      window.removeEventListener("cookie-consent-changed", onConsent);
  }, [gaId]);

  // Fallback: reage a mudanças via localStorage (outra aba)
  useEffect(() => {
    const onStorage = (ev: StorageEvent) => {
      if (ev.key === "analyticsConsent" && ev.newValue === "true") {
        if (!loadedRef.current) {
          loadGA4Once(String(gaId));
          loadedRef.current = true;
        }
      }
    };
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, [gaId]);

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
