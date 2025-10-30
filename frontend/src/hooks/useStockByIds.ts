// src/hooks/useStockByIds.ts
import { useEffect, useMemo, useState } from "react";
import { getStockByIds } from "../api/stock";
import type { StockDTO } from "../api/stock";

const cache = new Map<string, StockDTO>();

export function useStockByIds(ids: string[]) {
  // cria uma string estável só com ids filtrados
  const idsKey = useMemo(() => {
    const uniq = Array.from(new Set(ids.filter(Boolean)));
    return uniq.join("|");
  }, [ids]); // ✅ depende diretamente de ids

  const uniq = useMemo(() => (idsKey ? idsKey.split("|") : []), [idsKey]);

  const [data, setData] = useState<Record<string, StockDTO | undefined>>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    let cancelled = false;
    if (uniq.length === 0) {
      setData({});
      setError(null);
      setLoading(false);
      return;
    }

    (async () => {
      try {
        const missing = uniq.filter((id) => !cache.has(id));
        if (missing.length) {
          setLoading(true);
          const fresh = await getStockByIds(missing);
          Object.entries(fresh).forEach(([id, dto]) => cache.set(id, dto));
        }

        if (!cancelled) {
          const map: Record<string, StockDTO | undefined> = {};
          uniq.forEach((id) => { map[id] = cache.get(id); });
          setData(map);
          setError(null);
        }
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e : new Error(String(e)));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => { cancelled = true; };
  }, [idsKey, uniq]); // deps simples

  return { data, loading, error };
}
