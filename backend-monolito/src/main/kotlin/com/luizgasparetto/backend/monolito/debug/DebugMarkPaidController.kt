package com.luizgasparetto.backend.monolito.debug

import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/__debug")
class DebugMarkPaidController(
    private val jdbc: JdbcTemplate
) {
    private fun authOk(key: String?): Boolean {
        val expected = System.getenv("DEBUG_KEY") ?: ""
        val enabled  = (System.getenv("DEBUG_ENABLED") ?: "true").toBoolean()
        return enabled && expected.isNotBlank() && key == expected
    }

    @PostMapping("/orders/{orderId}/mark-paid")
    fun markPaid(
        @PathVariable orderId: Long,
        @RequestParam(required = false) txid: String?,
        @RequestParam(defaultValue = "pix") method: String,
        @RequestHeader(name = "X-Debug-Key", required = false) key: String?
    ): ResponseEntity<Map<String,Any?>> {
        if (!authOk(key)) return ResponseEntity.status(401).body(mapOf("error" to "unauthorized"))

        val rows = jdbc.update(
            """
      UPDATE orders
      SET paid = TRUE,
          status = 'CONFIRMED',
          paid_at = NOW(),
          payment_method = COALESCE(?, payment_method),
          txid = COALESCE(?, txid)
      WHERE id = ?
      """.trimIndent(),
            method, txid, orderId
        )
        if (rows == 0) return ResponseEntity.status(404).body(mapOf("error" to "order not found"))
        return ResponseEntity.ok(mapOf("ok" to true, "orderId" to orderId, "txid" to txid, "method" to method))
    }
}
