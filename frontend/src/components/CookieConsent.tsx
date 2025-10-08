import { useState, useEffect } from "react";
import { createPortal } from "react-dom";

export default function CookieConsent() {
  const [visible, setVisible] = useState(false);

  // --- executa na montagem ---
  useEffect(() => {
    const consent = document.cookie.includes("cookie_consent=true");
    console.log("Cookie atual:", document.cookie, "| Consent existe?", consent);

    if (consent) {
      loadGA();
    } else {
      setVisible(true);
    }
  }, []);

  // --- define cookie ---
  const setCookie = (value: "true" | "false") => {
    const isLocalhost = window.location.hostname === "localhost";
    const secure = isLocalhost ? "" : "Secure; SameSite=None;";
    document.cookie = `cookie_consent=${value}; max-age=31536000; path=/; ${secure}`;
  };

  const loadGA = () => {
    if (window.gtag) return; // evita carregar duas vezes

    const id = "G-PBYHQSPV31"; // ✅ seu ID real do GA4

    // 1️⃣ Carrega o script externo do GA4
    const script1 = document.createElement("script");
    script1.async = true;
    script1.src = `https://www.googletagmanager.com/gtag/js?id=${id}`;
    document.head.appendChild(script1);

    // 2️⃣ Cria o script interno que inicializa o gtag()
    const script2 = document.createElement("script");
    script2.innerHTML = `
    window.dataLayer = window.dataLayer || [];
    function gtag(){dataLayer.push(arguments);}
    gtag('js', new Date());
    gtag('config', '${id}', {
      cookie_flags: 'SameSite=None;Secure',
      cookie_domain: 'auto'
    });
  `;
    document.head.appendChild(script2);

    console.log("✅ GA4 carregado com ID:", id);
  };

  // --- ações ---
  const acceptCookies = () => {
    setCookie("true");
    loadGA();
    setVisible(false);
  };

  const declineCookies = () => {
    setCookie("false");
    setVisible(false);
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
          LGPD (Lei Geral de Proteção de Dados Pessoais)
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
