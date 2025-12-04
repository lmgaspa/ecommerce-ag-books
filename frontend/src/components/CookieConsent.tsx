// src/components/CookieConsent.tsx
import { useState, useEffect } from "react";
import { createPortal } from "react-dom";
import { apiGet, apiPost } from "../api/http";

type ConsentView = {
  analytics: boolean;
  marketing: boolean;
  version: number;
  ts: number;
};

export default function CookieConsent() {
  const [visible, setVisible] = useState(false);

  const notifyGate = (value: "true" | "false") => {
    try {
      localStorage.setItem(
        "analyticsConsent",
        value === "true" ? "true" : "false"
      );
    } catch (err) {
      if (import.meta.env.DEV) {
        console.warn(
          "CookieConsent: não foi possível usar localStorage.",
          err
        );
      }
    }
    window.dispatchEvent(new Event("cookie-consent-changed"));
  };

  const setCookie = (value: "true" | "false") => {
    const isLocalhost =
      window.location.hostname === "localhost" ||
      window.location.hostname === "127.0.0.1";
    const extra = isLocalhost ? "SameSite=Lax;" : "Secure; SameSite=None;";
    document.cookie = `cookie_consent=${value}; max-age=31536000; path=/; ${extra}`;
  };

  // Verificar consentimento no backend ao carregar
  useEffect(() => {
    const checkConsent = async () => {
      try {
        const response = await apiGet<ConsentView>("/privacy/consent");

        if (response) {
          // Regra: consideramos "consentido" se ao menos um tipo está ON
          const accepted = response.analytics || response.marketing;

          setCookie(accepted ? "true" : "false");
          notifyGate(accepted ? "true" : "false");
          setVisible(false);
          return;
        }
      } catch {
        // Se API falhar, cai pro cookie local
      }

      const consent = document.cookie.includes("cookie_consent=true");
      if (!consent) setVisible(true);
    };

    checkConsent();
  }, []);

  const acceptCookies = async () => {
    setCookie("true");
    notifyGate("true");
    setVisible(false);

    try {
      await apiPost("/privacy/consent", {
        analytics: true,
        marketing: true, // se quiser marketing separado, depois você muda aqui
        version: 1,
      });
    } catch {
      // Silencioso – UX já está ok para o usuário
    }
  };

  const declineCookies = async () => {
    setCookie("false");
    notifyGate("false");
    setVisible(false);

    try {
      await apiPost("/privacy/consent", {
        analytics: false,
        marketing: false,
        version: 1,
      });
    } catch {
      // Silencioso
    }
  };

  if (!visible) return null;

  return createPortal(
    <div
      className="
        fixed bottom-0 left-0 w-full
        bg-[var(--color-background2)] border-t border-border
        text-text-primary p-4 sm:p-5 
        flex flex-col sm:flex-row sm:items-center justify-between
      "
      role="dialog"
      aria-live="polite"
    >
      <p className="text-sm leading-relaxed sm:max-w-[70%]">
        Usamos cookies para melhorar sua experiência e analisar as métricas do
        site. Você pode aceitar ou recusar conforme a{" "}
        <span className="font-semibold">
          LGPD - Lei Geral de Proteção de Dados Pessoais
        </span>
        .
      </p>

      <div className="flex gap-3 sm:shrink-0">
        <button
          onClick={acceptCookies}
          className="px-5 py-2 rounded-md shadow-md text-background font-semibold bg-primary hover:bg-secondary transition"
        >
          Aceitar
        </button>

        <button
          onClick={declineCookies}
          className="px-5 py-2 rounded-md border border-border bg-surface-muted text-text-secondary hover:bg-surface transition"
        >
          Recusar
        </button>
      </div>
    </div>,
    document.body
  );
}
  