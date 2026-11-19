package com.yehor.finora.ui.operations

/** The three tabs of the unified transaction editor. [arg] is the `kind` route argument. */
enum class EditorMode(val arg: String) {
    EXPENSE("EXPENSE"),
    INCOME("INCOME"),
    TRANSFER("TRANSFER"),
    ;

    companion object {
        fun fromArg(kind: String?): EditorMode = when (kind) {
            "INCOME" -> INCOME
            "TRANSFER" -> TRANSFER
            else -> EXPENSE
        }
    }
}
