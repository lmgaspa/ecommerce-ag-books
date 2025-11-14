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

export async function getStockById(id: string): Promise<StockDTO> {
  const data = await apiGet<BookFromApi>(`/books/${id}`);
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