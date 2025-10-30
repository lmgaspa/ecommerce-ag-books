// src/components/AnalyticsGate.tsx
import { useEffect, useMemo, useRef } from "react";
import { useLocation } from "react-router-dom";

declare global {
  interface Window {
    dataLayer?: unknown[];
    gtag?: (...args: unknown[]) => void;
  }
}

/** Read cookie_consent cookie (true/false). */
function hasConsent(): boolean {
  return document.cookie
    .split(";")
    .some((c) => c.trim().startsWith("cookie_consent=true"));
}

/** Idempotently inject GA4. */
function loadGA4Once(measurementId: string): void {
  if (!measurementId) {
    console.info("[AnalyticsGate] VITE_GA4_ID missing; GA will not load.");
    return;
  }
  if (typeof window === "undefined") return;
  if (window.dataLayer) return; // already loaded (avoid duplicates)

  // ---- external gtag script
  const s1 = document.createElement("script");
  s1.async = true;
  s1.src = `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(
    measurementId
  )}`;
  s1.setAttribute("data-analytics", "ga4");
  document.head.appendChild(s1);

  // ---- decide cookie domain
  const host = window.location.hostname;
  const isLocal = host === "localhost" || host === "127.0.0.1";

  // Use the registrable apex so cookies work on both apex and www.
  // Adjust this to your real apex if you have a different domain.
  const apexForAll = ".agenorgasparetto.com.br";

  // If on localhost: use 'none' to avoid invalid-domain errors.
  // If on our domain: force the apex; otherwise let GA decide with 'auto'.
  const cookieDomain = isLocal
    ? "none"
    : host.endsWith("agenorgasparetto.com.br")
    ? apexForAll
    : "auto";

  // ---- bootstrap + config
  const s2 = document.createElement("script");
  s2.setAttribute("data-analytics", "ga4-init");
  s2.innerHTML = `
    window.dataLayer = window.dataLayer || [];
    function gtag(){ dataLayer.push(arguments); }
    gtag('js', new Date());

    // SPA: disable automatic page_view to avoid duplicates
    gtag('config', '${measurementId}', {
      anonymize_ip: true,
      send_page_view: false,
      cookie_domain: '${cookieDomain}',
      cookie_update: true
      // If you need cross-site/iframe support, uncomment:
      // , cookie_flags: 'SameSite=None;Secure'
    });
  `;
  document.head.appendChild(s2);

  console.info(
    `[AnalyticsGate] GA4 loaded (${measurementId}) with cookie_domain=${cookieDomain}.`
  );
}

type AnalyticsGateProps = {
  /** Force consent externally (optional). */
  forcedConsent?: boolean;
  /** Send page_view on each SPA navigation. Default: true */
  trackPageviews?: boolean;
  /** GA4 ID (fallback to VITE_GA4_ID). */
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

  const consentOk = useMemo(
    () => (typeof forcedConsent === "boolean" ? forcedConsent : hasConsent()),
    [forcedConsent]
  );

  // Load GA4 when consent is granted
  useEffect(() => {
    if (!consentOk || loadedRef.current) return;
    loadGA4Once(String(gaId));
    loadedRef.current = true;
  }, [consentOk, gaId]);

  // React to consent changes (custom event)
  useEffect(() => {
    const onConsent = () => {
      if (!loadedRef.current && hasConsent()) {
        loadGA4Once(String(gaId));
        loadedRef.current = true;
      }
    };
    window.addEventListener("cookie-consent-changed", onConsent);
    return () => window.removeEventListener("cookie-consent-changed", onConsent);
  }, [gaId]);

  // Fallback: react to localStorage changes (other tabs)
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

  // Send page_view for SPA route changes
  useEffect(() => {
    if (!trackPageviews) return;
    if (!consentOk) return;
    const gtag = window.gtag;
    if (!gtag) return; // not loaded yet

    gtag("event", "page_view", {
      page_location: window.location.href,
      page_path: `${location.pathname}${location.search}`,
      page_title: document.title,
    } as Record<string, unknown>);
  }, [location, consentOk, trackPageviews]);

  return null;
}
