package com.luizgasparetto.backend.monolito.config.g4

import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.Base64

@Configuration
class BigQueryConfig {

    @Bean
    fun bigQuery(): BigQuery {
        // 1) Preferir GA4_SA_JSON (conteúdo puro do JSON)
        System.getenv("GA4_SA_JSON")?.takeIf { it.isNotBlank() }?.let { json ->
            ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8)).use { input ->
                val creds: GoogleCredentials = ServiceAccountCredentials.fromStream(input)
                return BigQueryOptions.newBuilder().setCredentials(creds).build().service
            }
        }

        // 2) Alternativa: GA4_SA_JSON_B64 (JSON em Base64)
        System.getenv("GA4_SA_JSON_B64")?.takeIf { it.isNotBlank() }?.let { b64 ->
            val decoded = Base64.getDecoder().decode(b64)
            ByteArrayInputStream(decoded).use { input ->
                val creds: GoogleCredentials = ServiceAccountCredentials.fromStream(input)
                return BigQueryOptions.newBuilder().setCredentials(creds).build().service
            }
        }

        // 3) Fallback: GOOGLE_APPLICATION_CREDENTIALS (arquivo .json montado como secret)
        return BigQueryOptions.getDefaultInstance().service
    }
}
