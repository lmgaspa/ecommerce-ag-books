// src/components/AnalyticsGate.tsx
import { useEffect, useRef, useState } from "react";
import { useLocation } from "react-router-dom";

/** Lê consentimento de forma segura: primeiro localStorage, depois cookie. */
function hasConsentFromStorage(): boolean {
  if (typeof window === "undefined" || typeof document === "undefined") {
    return false;
  }

  // 1) Tenta via localStorage (setado pelo CookieConsent)
  try {
    const ls = window.localStorage?.getItem("analyticsConsent");
    if (ls === "true") return true;
    if (ls === "false") return false;
  } catch {
    // se der erro (modo privado, etc.), ignora e tenta cookie
  }

  // 2) Fallback: cookie_consent
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

  if (document.querySelector('script[data-analytics="ga4-src"]')) {
    return;
  }

  const s1 = document.createElement("script");
  s1.async = true;
  s1.src = `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(
    measurementId
  )}`;
  s1.setAttribute("data-analytics", "ga4-src");
  document.head.appendChild(s1);

  const host = window.location.hostname;
  const isLocal = host === "localhost" || host === "127.0.0.1";
  const cookieDomain = isLocal ? "none" : "auto";

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
    });
  `;
  document.head.appendChild(s2);

  console.info(
    `[AnalyticsGate] GA4 carregado (${measurementId}) com cookie_domain=${cookieDomain}.`
  );
}

type AnalyticsGateProps = {
  forcedConsent?: boolean;
  trackPageviews?: boolean;
  measurementId?: string;
};

export default function AnalyticsGate({
  forcedConsent,
  trackPageviews = true,
  measurementId,
}: AnalyticsGateProps) {
  const gaId =
    measurementId ?? ((import.meta.env.VITE_GA4_ID as string | undefined) ?? "");

  const location = useLocation();
  const loadedRef = useRef(false);

  // Estado reativo de consentimento
  const [consentOk, setConsentOk] = useState<boolean>(() => {
    if (typeof forcedConsent === "boolean") return forcedConsent;
    return hasConsentFromStorage();
  });

  // Se forcedConsent mudar, respeita ele como override
  useEffect(() => {
    if (typeof forcedConsent === "boolean") {
      setConsentOk(forcedConsent);
    } else {
      setConsentOk(hasConsentFromStorage());
    }
  }, [forcedConsent]);

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
    if (typeof window === "undefined") return;

    const onConsent = () => {
      const ok = hasConsentFromStorage();
      if (!ok || loadedRef.current) return;
      if (!gaId) {
        console.info(
          "[AnalyticsGate] GA4 não carregado no evento cookie-consent-changed: gaId vazio."
        );
        return;
      }
      loadGA4Once(String(gaId));
      loadedRef.current = true;
      setConsentOk(true);
    };

    window.addEventListener("cookie-consent-changed", onConsent);
    return () => {
      window.removeEventListener("cookie-consent-changed", onConsent);
    };
  }, [gaId]);

  // Page views em SPA (só com consentimento + GA carregado)
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
