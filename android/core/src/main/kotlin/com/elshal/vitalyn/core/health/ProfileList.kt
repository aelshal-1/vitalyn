package com.elshal.vitalyn.core

/** Lists (medications, allergies, other conditions) stored as one text field, one item per line. */
object ProfileList {
    fun items(text: String): List<String> =
        text.split('\n', ';', '،')
            .map { it.trimSwiftWhitespace() }
            .filter { it.isNotEmpty() }

    fun text(items: List<String>): String =
        items.map { it.trimSwiftWhitespace() }.filter { it.isNotEmpty() }.joinToString("\n")
}
