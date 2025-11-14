import { apiGet } from "./http";
import type { Book } from "../data/books";

export type StockDTO = { id: string; stock: number; available: boolean };

// tipo mínimo retornado pelo backend
type BookFromApi = { id: string; stock?: number };

/**
 * Busca todos os livros da API
 * Retorna null se a API falhar (usa fallback para dados locais)
 */
export async function getAllBooks(): Promise<Book[] | null> {
  try {
    const books = await apiGet<Book[]>("/books");
    return books;
  } catch {
    // Se API falhar, retorna null para usar fallback local
    return null;
  }
}

/**
 * Busca informações de um livro por slug
 * O backend usa slug ao invés de id na URL
 */
export async function getStockById(id: string): Promise<StockDTO> {
  // O backend espera slug na URL: /api/v1/books/{slug}
  // Os IDs locais já são slugs (ex: "extase", "sempre", etc.)
  const data = await apiGet<BookFromApi>(`/books/${encodeURIComponent(id)}`);
  const stock = typeof data?.stock === "number" ? data.stock : 0;
  return { id: data?.id ?? id, stock, available: stock > 0 };
}

export async function getStockByIds(ids: string[]): Promise<Record<string, StockDTO>> {
  const uniq = Array.from(new Set(ids.filter(Boolean)));
  if (!uniq.length) return {};
  const results = await Promise.allSettled(uniq.map(getStockById));
  const map: Record<string, StockDTO> = {};
  results.forEach((r, i) => {
    const id = uniq[i];
    if (r.status === "fulfilled") map[id] = r.value;
  });
  return map;
}