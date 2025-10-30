const RAW = import.meta.env.VITE_API_BASE as string | undefined;
const BASE_URL = RAW ? RAW.replace(/\/+$/, "") : "";

/** Se BASE_URL não existir (dev), usamos o path puro e deixamos o proxy do Vite cuidar. */
function buildUrl(path: string) {
  if (!path.startsWith("/")) {
    throw new Error(`Path deve começar com '/': recebido "${path}"`);
  }
  return BASE_URL ? `${BASE_URL}${path}` : path;
}

export async function apiGet<T>(path: string): Promise<T> {
  const res = await fetch(buildUrl(path), { credentials: "omit" });
  if (!res.ok) throw new Error(`GET ${path} -> ${res.status}`);
  return res.json() as Promise<T>;
}
