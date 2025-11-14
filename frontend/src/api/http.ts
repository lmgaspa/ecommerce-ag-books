const RAW = import.meta.env.VITE_API_BASE as string | undefined;
const BASE_URL = RAW ? RAW.replace(/\/+$/, "") : "";

// Prefixo global para todas as APIs
const API_VERSION = "/api/v1";

/** Constrói a URL completa com /api/v1 como prefixo */
function buildUrl(path: string): string {
  // Remove /api/ ou /api/v1 se já estiver presente no path
  const cleanPath = path.replace(/^\/api\/v1/, "").replace(/^\/api\//, "");
  
  // Garante que o path começa com /
  const normalizedPath = cleanPath.startsWith("/") ? cleanPath : `/${cleanPath}`;
  
  // Monta a URL final: BASE_URL + /api/v1 + path
  const fullPath = `${API_VERSION}${normalizedPath}`;
  
  // Se BASE_URL não existir (dev), usamos o path puro e deixamos o proxy do Vite cuidar
  return BASE_URL ? `${BASE_URL}${fullPath}` : fullPath;
}

/**
 * Exporta função para construir URLs da API (útil para EventSource, etc)
 */
export function buildApiUrl(path: string): string {
  return buildUrl(path);
}

export interface ApiRequestOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE" | "PATCH";
  headers?: Record<string, string>;
  body?: unknown;
  credentials?: RequestCredentials;
}

/**
 * Função genérica para fazer requisições à API
 */
async function apiRequest<T>(
  path: string,
  options: ApiRequestOptions = {}
): Promise<T> {
  const {
    method = "GET",
    headers = {},
    body,
    credentials = "omit",
  } = options;

  const config: RequestInit = {
    method,
    credentials,
    headers: {
      "Content-Type": "application/json",
      ...headers,
    },
  };

  if (body !== undefined) {
    config.body = JSON.stringify(body);
  }

  const res = await fetch(buildUrl(path), config);
  
  if (!res.ok) {
    const errorText = await res.text().catch(() => `HTTP ${res.status}`);
    throw new Error(`${method} ${path} -> ${res.status}: ${errorText}`);
  }

  // Se a resposta não tiver conteúdo, retorna void
  const contentType = res.headers.get("content-type");
  if (contentType?.includes("application/json")) {
    return res.json() as Promise<T>;
  }
  
  return undefined as T;
}

/**
 * GET request
 */
export async function apiGet<T>(path: string): Promise<T> {
  return apiRequest<T>(path, { method: "GET" });
}

/**
 * POST request
 */
export async function apiPost<T>(
  path: string,
  body?: unknown,
  options?: Omit<ApiRequestOptions, "method" | "body">
): Promise<T> {
  return apiRequest<T>(path, { ...options, method: "POST", body });
}

/**
 * PUT request
 */
export async function apiPut<T>(
  path: string,
  body?: unknown,
  options?: Omit<ApiRequestOptions, "method" | "body">
): Promise<T> {
  return apiRequest<T>(path, { ...options, method: "PUT", body });
}

/**
 * DELETE request
 */
export async function apiDelete<T>(
  path: string,
  options?: Omit<ApiRequestOptions, "method">
): Promise<T> {
  return apiRequest<T>(path, { ...options, method: "DELETE" });
}

/**
 * PATCH request
 */
export async function apiPatch<T>(
  path: string,
  body?: unknown,
  options?: Omit<ApiRequestOptions, "method" | "body">
): Promise<T> {
  return apiRequest<T>(path, { ...options, method: "PATCH", body });
}
