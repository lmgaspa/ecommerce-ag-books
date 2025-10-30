const BASE_URL = import.meta.env.VITE_API_BASE?.replace(/\/+$/, "");

if (!BASE_URL) {
  throw new Error("VITE_API_BASE não configurado. Configure a variável de ambiente.");
}

export async function apiGet<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, { credentials: "omit" });
  if (!res.ok) throw new Error(`GET ${path} -> ${res.status}`);
  return res.json() as Promise<T>;
}