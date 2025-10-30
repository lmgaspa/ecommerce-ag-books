// src/utils/cookieUtils.ts
import Cookies from "js-cookie";

const isBrowser = typeof document !== "undefined";

/**
 * Duracao padrao dos cookies (em dias).
 */
export const COOKIE_DAYS = 7;

/**
 * Le um cookie JSON de forma segura e retorna o valor parseado.
 * Se nao existir ou falhar o parse, retorna o fallback informado.
 */
export function getCookieJSON<T>(key: string, fallback: T): T {
  if (!isBrowser) return fallback;
  try {
    const value = Cookies.get(key);
    return value ? (JSON.parse(value) as T) : fallback;
  } catch {
    return fallback;
  }
}

/**
 * Salva um valor JSON em cookie (stringificando automaticamente).
 * Inclui opcoes seguras de SameSite e Secure.
 */
export function saveCookieJSON<T>(key: string, value: T) {
  if (!isBrowser) return;
  Cookies.set(key, JSON.stringify(value), {
    expires: COOKIE_DAYS,
    sameSite: "Lax",
    secure: typeof window !== "undefined" && window.location.protocol === "https:",
  });
}

/**
 * Remove um cookie com o nome especificado.
 */
export function removeCookie(key: string) {
  if (!isBrowser) return;
  Cookies.remove(key);
}

export const cookieStorage = {
  get<T>(key: string, fallback: T): T {
    return getCookieJSON(key, fallback);
  },
  set<T>(key: string, value: T) {
    saveCookieJSON(key, value);
  },
  remove(key: string) {
    removeCookie(key);
  },
};
