package com.luizgasparetto.backend.monolito.services.email.common

import java.time.Year

/**
 * Utilitário global para gerar o footer padrão de todos os emails
 */
object EmailFooter {

    /**
     * Gera o HTML do footer padrão usado em todos os emails
     */
    fun build(): String {
        return """
            <div style="background:linear-gradient(135deg,#0a2239,#0e4b68);color:#fff;
                        padding:6px 18px;text-align:center;font-size:14px;line-height:1;">
              <span role="img" aria-label="raio"
                    style="color:#ffd200;font-size:22px;vertical-align:middle;">&#x26A1;&#xFE0E;</span>
              <span style="vertical-align:middle;">© ${Year.now()} · Powered by
                <strong>AndesCore Software</strong>
              </span>
            </div>
        """.trimIndent()
    }
}

