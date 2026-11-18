package com.yehor.finora.domain.dataio

object ImportReportFormatter {
    const val MAX_DISPLAYED_PROBLEMS = 50

    fun format(problems: List<ImportProblem>, totalProblems: Int): List<String> {
        val displayed = problems.take(MAX_DISPLAYED_PROBLEMS).map { "${it.check}: ${it.subject}" }
        val remaining = totalProblems - displayed.size
        return if (remaining > 0) {
            displayed + "and $remaining more"
        } else {
            displayed
        }
    }

    fun enrichSubject(
        subject: String,
        accounts: Map<Long, String> = emptyMap(),
        categories: Map<Long, String> = emptyMap(),
        tags: Map<Long, String> = emptyMap(),
        schedules: Map<Long, String> = emptyMap(),
    ): String {
        val parts = subject.trim().split(" ", limit = 2)
        if (parts.size != 2) return subject
        val kind = parts[0]
        val id = parts[1].toLongOrNull() ?: return subject
        val name = when (kind) {
            "account" -> accounts[id]
            "category" -> categories[id]
            "tag" -> tags[id]
            "schedule" -> schedules[id]
            else -> null
        }
        return if (name != null) {
            "$kind $id \"$name\""
        } else {
            subject
        }
    }
}
