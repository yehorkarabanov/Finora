package com.yehor.finora.domain.dataio

import com.yehor.finora.domain.money.Money
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CsvFormat {
    val HEADER: List<String> = listOf(
        "id",
        "type",
        "date",
        "time",
        "timestamp_utc",
        "account",
        "amount",
        "currency",
        "to_account",
        "to_amount",
        "to_currency",
        "category",
        "category_path",
        "tags",
        "description",
        "created_at_utc",
        "updated_at_utc",
    )

    private val FORMULA_CHARS = setOf('=', '+', '-', '@', '\t', '\r')

    fun writeRow(out: Appendable, fields: List<String>) {
        val encoded = fields.joinToString(",") { escapeField(it) }
        out.append(encoded).append("\r\n")
    }

    private fun escapeField(field: String): String {
        return if (field.contains(',') || field.contains('"') || field.contains('\r') || field.contains('\n')) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
    }

    fun textField(raw: String?): String {
        if (raw == null) return ""
        return if (raw.isNotEmpty() && raw[0] in FORMULA_CHARS) {
            "'$raw"
        } else {
            raw
        }
    }

    fun tagList(names: List<String>): String {
        return names.joinToString(";") { name ->
            name.replace("\\", "\\\\").replace(";", "\\;")
        }
    }

    fun amount(money: Money): String = money.toPlainString()

    fun dateParts(epochMs: Long, zone: ZoneId): Triple<String, String, String> {
        val instant = Instant.ofEpochMilli(epochMs)
        val zdt = instant.atZone(zone)
        val date = zdt.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val time = zdt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        val utcInstant = DateTimeFormatter.ISO_INSTANT.format(instant)
        return Triple(date, time, utcInstant)
    }
}
