package com.luizgasparetto.backend.monolito.debug

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/__debug")
class DebugManualReturnController(
    private val jdbc: JdbcTemplate
) {
    private fun authOk(key: String?): Boolean {
        val expected = System.getenv("DEBUG_KEY") ?: ""
        val enabled  = (System.getenv("DEBUG_ENABLED") ?: "true").toBoolean()
        return enabled && expected.isNotBlank() && key == expected
    }

    private val sql = """
    WITH oi AS (
      SELECT oi.order_id,
             json_agg(json_build_object(
               'id', oi.id, 'book_id', oi.book_id, 'qty', oi.quantity, 'price', oi.price
             ) ORDER BY oi.id) AS items
      FROM order_items oi
      GROUP BY oi.order_id
    )
    SELECT json_build_object(
      'order', to_jsonb(o.*),
      'items', coalesce(oi.items, '[]'::json)
    )::text
    FROM orders o
    LEFT JOIN oi ON oi.order_id = o.id
    WHERE o.id = ?
  """.trimIndent()

    @GetMapping("/manual-return/{orderId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun manualReturn(
        @PathVariable orderId: Long,
        @RequestHeader(name = "X-Debug-Key", required = false) key: String?
    ): ResponseEntity<String> {
        if (!authOk(key)) return ResponseEntity.status(401).body("""{"error":"unauthorized"}""")
        val payload = jdbc.query(sql, { rs, _ -> rs.getString(1) }, orderId).firstOrNull()
            ?: return ResponseEntity.status(404).body("""{"error":"order not found","orderId":$orderId}""")
        return ResponseEntity.ok(payload)
    }
}
