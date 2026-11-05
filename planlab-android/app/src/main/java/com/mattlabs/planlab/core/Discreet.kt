package com.mattlabs.planlab.core

/**
 * Words that stay inside the app. Anything shown outside it (notifications, the lock screen, the
 * home-screen widget) says what to do and for how long, never what it is for.
 */
object Discreet {
    private val RULES = listOf(
        Regex("""pelvic[- ]floor( exercises?| sets?)?""", RegexOption.IGNORE_CASE) to "Core set",
        Regex("""\bpelvic\b""", RegexOption.IGNORE_CASE) to "Core",
        Regex("""\bkegels?\b""", RegexOption.IGNORE_CASE) to "Core set",
    )

    fun text(s: String): String = RULES.fold(s) { acc, (re, to) -> re.replace(acc, to) }
}
