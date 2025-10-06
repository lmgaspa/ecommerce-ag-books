import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";
import App from "./App.tsx";
import { CartProvider } from "./context/CartContext";

// --- GA4 só após consentimento ---
function loadGA() {
  if (window.gtag) return; // evita recarregar

  const script1 = document.createElement("script");
  script1.async = true;
  script1.src = "https://www.googletagmanager.com/gtag/js?id=G-LZKNGE2JCM";
  document.head.appendChild(script1);

  const script2 = document.createElement("script");
  script2.innerHTML = `
    window.dataLayer = window.dataLayer || [];
    function gtag(){dataLayer.push(arguments);}
    gtag('js', new Date());
    gtag('config', 'G-LZKNGE2JCM', {
      cookie_flags: 'SameSite=None;Secure',
      cookie_domain: 'auto'
    });
  `;
  document.head.appendChild(script2);
}

function hasConsent() {
  return document.cookie.includes("cookie_consent=true");
}

// ✅ Carrega GA4 apenas se o visitante já aceitou
if (hasConsent()) {
  loadGA();
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <CartProvider>
      <App />
    </CartProvider>
  </StrictMode>
);
