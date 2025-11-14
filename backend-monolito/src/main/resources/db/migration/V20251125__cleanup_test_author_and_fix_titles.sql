-- V20251124__cleanup_test_author_and_fix_utf_titles.sql
-- 1) Remove autor de teste "Teste A" se estiver sobrando sem livros
DELETE FROM authors a
WHERE a.name  = 'Teste A'
  AND a.email = 'user@example.com'
  AND NOT EXISTS (
      SELECT 1
      FROM books b
      WHERE b.author_id = a.id
  );

-- 2) Corrige título do livro 'extase' com UTF quebrada
--    (idempotente: só atualiza se ainda estiver diferente do texto correto)
UPDATE books
SET title = 'Êxtase, de birra com Jorge Amado e outras crônicas grapiúnas'
WHERE id = 'extase'
  AND title <> 'Êxtase, de birra com Jorge Amado e outras crônicas grapiúnas';
