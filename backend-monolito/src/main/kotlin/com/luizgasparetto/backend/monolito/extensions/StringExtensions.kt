package com.luizgasparetto.backend.monolito.extensions

import java.text.Normalizer
import java.util.regex.Pattern

/**
 * Normaliza um código de cupom removendo acentos e convertendo para maiúsculas. Ex: "Lançamento" ->
 * "LANCAMENTO"
 */
fun String.normalizeCouponCode(): String {
    val nfdNormalizedString = Normalizer.normalize(this, Normalizer.Form.NFD)
    val pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
    return pattern.matcher(nfdNormalizedString).replaceAll("").uppercase().trim()
}
