// src/components/AnalyticsGate.tsx
import { useEffect, useMemo, useRef } from "react";
import { useLocation } from "react-router-dom";

/** Lê cookie_consent=true|false. */
function hasConsent(): boolean {
  if (typeof document === "undefined") return false;

  return document.cookie
    .split(";")
    .some((c) => c.trim().startsWith("cookie_consent=true"));
}

/** Injeta GA4 idempotente (sem duplicar). */
function loadGA4Once(measurementId: string): void {
  if (!measurementId) {
    console.info("[AnalyticsGate] VITE_GA4_ID ausente; GA não será carregado.");
    return;
  }
  if (typeof window === "undefined" || typeof document === "undefined") return;

  // Já injetado? Conferimos uma flag no DOM ao invés de checar window.dataLayer,
  // porque outros scripts podem mexer no dataLayer.
  if (document.querySelector('script[data-analytics="ga4-src"]')) {
    return;
  }

  // Script externo oficial do GA4
  const s1 = document.createElement("script");
  s1.async = true;
  s1.src = `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(
    measurementId
  )}`;
  s1.setAttribute("data-analytics", "ga4-src");
  document.head.appendChild(s1);

  // Definir cookie_domain: "none" para localhost; "auto" no restante
  const host = window.location.hostname;
  const isLocal = host === "localhost" || host === "127.0.0.1";
  const cookieDomain = isLocal ? "none" : "auto";

  // Bootstrap + config (com send_page_view:false para SPA)
  const s2 = document.createElement("script");
  s2.setAttribute("data-analytics", "ga4-init");
  s2.innerHTML = `
    window.dataLayer = window.dataLayer || [];
    function gtag(){ window.dataLayer.push(arguments); }
    gtag('js', new Date());
    gtag('config', '${measurementId}', {
      anonymize_ip: true,
      send_page_view: false,
      cookie_domain: '${cookieDomain}',
      cookie_update: true
      // Para cross-site/iframe, se necessário:
      // , cookie_flags: 'SameSite=None;Secure'
    });
  `;
  document.head.appendChild(s2);

  console.info(
    `[AnalyticsGate] GA4 carregado (${measurementId}) com cookie_domain=${cookieDomain}.`
  );
}

type AnalyticsGateProps = {
  /** Força consentimento externamente (opcional). */
  forcedConsent?: boolean;
  /** Envia page_view a cada navegação SPA. Padrão: true */
  trackPageviews?: boolean;
  /**
   * GA4 ID específico (se não informado, usa VITE_GA4_ID).
   *
   * Opção A (recomendada agora):
   *  - Não passar essa prop.
   *  - Deixar cada deploy/autor definir VITE_GA4_ID no .env.
   */
  measurementId?: string;
};

export default function AnalyticsGate({
  forcedConsent,
  trackPageviews = true,
  measurementId,
}: AnalyticsGateProps) {
  // Opção A: usar VITE_GA4_ID por deploy/autor, com possibilidade de override via prop.
  const gaId =
    measurementId ?? ((import.meta.env.VITE_GA4_ID as string | undefined) ?? "");

  const location = useLocation();
  const loadedRef = useRef(false);

  const consentOk = useMemo(
    () =>
      typeof forcedConsent === "boolean" ? forcedConsent : hasConsent(),
    [forcedConsent]
  );

  // Carrega GA4 quando o consentimento existir
  useEffect(() => {
    if (!consentOk || loadedRef.current) return;
    if (!gaId) {
      console.info(
        "[AnalyticsGate] GA4 não carregado: gaId vazio (verifique VITE_GA4_ID)."
      );
      return;
    }
    loadGA4Once(String(gaId));
    loadedRef.current = true;
  }, [consentOk, gaId]);

  // Reage ao evento custom disparado pelo CookieConsent
  useEffect(() => {
    const onConsent = () => {
      if (loadedRef.current) return;
      if (!hasConsent()) return;
      if (!gaId) {
        console.info(
          "[AnalyticsGate] GA4 não carregado no evento cookie-consent-changed: gaId vazio."
        );
        return;
      }
      loadGA4Once(String(gaId));
      loadedRef.current = true;
    };

    if (typeof window === "undefined") return;

    window.addEventListener("cookie-consent-changed", onConsent);
    return () =>
      window.removeEventListener("cookie-consent-changed", onConsent);
  }, [gaId]);

  // Page views em SPA
  useEffect(() => {
    if (!trackPageviews || !consentOk) return;
    if (typeof window === "undefined") return;
    if (typeof window.gtag !== "function") return;

    window.gtag("event", "page_view", {
      page_location: window.location.href,
      page_path: `${location.pathname}${location.search}`,
      page_title: document.title,
    } as Record<string, unknown>);
  }, [location, consentOk, trackPageviews]);

  return null;
}
