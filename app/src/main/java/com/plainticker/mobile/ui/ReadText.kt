package com.plainticker.mobile.ui

import java.time.LocalDate

/**
 * The display side's own pass over the server's generated prose ("The read", "What to check
 * next"), so it keeps this app's copy rules even when the text was written before the server
 * learned them (device QA of 1.3.17: AAPLx's and JEFx's read carried em dashes, and "When AbbVie
 * reports on 2026-10-23" printed a machine date). Old answers live in the server's cache for days,
 * so the web fix alone does not reach every screen.
 *
 * Two rewrites, both conservative:
 *
 * 1. **A dash used as punctuation** (an em dash, a spaced en dash, or a spaced hyphen) becomes a
 *    comma or a colon. A pair inside one sentence is an aside, so both become commas. A lone dash
 *    before a joining word ("and", "but", "though", ...) becomes a comma; before anything else it
 *    introduces what follows, so it becomes a colon. An unspaced en dash between two numbers is a
 *    range and reads "to". A hyphen inside a word ("Forward-axis") or before a number ("-4.2%") is
 *    left alone.
 * 2. **An ISO date** ("2026-10-23") becomes the app's own "23 Oct", with the year only when it is
 *    not [currentYear]. A string that is not a real calendar day is left as it was.
 * 3. **References to what this app does not have** (device QA of 1.3.18): the web page numbers its
 *    sections and draws a same-sector table; the app has neither. "(see section 06)" and "in
 *    section 06" are dropped, and "same-sector table" reads "sector comparison". So do the other
 *    names the web gives that table (final QA of 1.3.19): "the sector table", "the table
 *    comparison", and a sector's own name before "table" ("the Information Technology table"
 *    reads "the Information Technology sector comparison").
 * 4. **"has beat"** reads "has beaten" (final QA of 1.3.19: "The company has beat consensus in 7
 *    of 7 recent quarters").
 *
 * A pair of dashes is found across an abbreviation: "AbbVie Inc. beat" is one sentence, so a stop
 * counts as a sentence end only before a capital or the end of the text (device QA of 1.3.18: the
 * pair around "AbbVie Inc. beat estimates in 6 of 6 recent quarters" was split at "Inc." and read
 * "quarters: and the Forward-axis"). A colon an older pass already put before a joining word
 * ("quarters: and") is put back to a comma.
 *
 * [sentenceCase] is a separate pass for the step titles of "What to check next", which some
 * answers carry in Title Case and others in sentence case.
 *
 * Pure: no Android, no clock; the caller passes the year.
 */
object ReadText {

    /** The eleven GICS sectors, the names `/summary` and the analysis carry. */
    private val GICS_SECTORS = listOf(
        "Communication Services", "Consumer Discretionary", "Consumer Staples", "Energy", "Financials",
        "Health Care", "Industrials", "Information Technology", "Materials", "Real Estate", "Utilities",
    )

    private val EM: Char = Char(0x2014)
    private val EN: Char = Char(0x2013)

    /** An unspaced en dash between digits: a range. */
    private val range = Regex("(?<=\\d)" + EN + "(?=\\d)")

    /** An em dash with or without spaces, a spaced en dash, or a spaced hyphen. */
    private val dash = Regex("\\s*" + EM + "\\s*|\\s+" + EN + "\\s+|\\s+-\\s+")

    private val isoDate = Regex("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b")

    private val joiningWords = setOf(
        "and", "but", "or", "nor", "so", "yet", "though", "although", "while", "which", "who",
        "whose", "where", "when", "because", "as", "since", "unless", "if", "then", "not",
        "especially", "including", "even", "rather", "plus",
    )

    /** [text] with its punctuation dashes, ISO dates and references rewritten; see the class doc. */
    fun normalize(text: String, currentYear: Int): String =
        references(colonsBeforeJoins(dates(dashes(text), currentYear)))

    /** A colon straight before a joining word: always a dash turned the wrong way, so a comma. */
    private val colonBeforeJoin = Regex(":\\s+(and|but|or|nor|yet|while|though|although)\\b")

    private fun colonsBeforeJoins(text: String): String = text.replace(colonBeforeJoin) { ", " + it.groupValues[1] }

    /** "(see section 06)", "(section 6)", "(see the same-sector table in section 06)". */
    private val sectionAside = Regex("\\s*\\((?:see |in )?(?:the )?[^()]*?section\\s+\\d+[^()]*\\)", RegexOption.IGNORE_CASE)

    /** " in section 06", ", see section 06" inside a sentence. */
    private val sectionClause = Regex(",?\\s+(?:in|see|from)\\s+section\\s+\\d+\\b", RegexOption.IGNORE_CASE)

    /** "same-sector table": a table the web page draws beside the read and the app does not. */
    private val sectorTable = Regex("\\b[Ss]ame[- ]sector table\\b")

    /** "sector table", "Sector table": the same table, named without "same". */
    private val plainSectorTable = Regex("\\b([Ss])ector table\\b")

    /** "the table comparison": the web's third name for it. */
    private val tableComparison = Regex("\\btable comparison\\b")

    /** A GICS sector's own name before "table": "the Information Technology table". */
    private val namedSectorTable = Regex(
        "\\b(" + GICS_SECTORS.joinToString("|") { Regex.escape(it) } + ") table\\b",
    )

    /** "has beat": the past participle is "beaten". Only this exact pair, so nothing else moves. */
    private val hasBeat = Regex("\\bhas beat\\b")

    private fun references(text: String): String = text
        .replace(sectionAside, "")
        .replace(sectionClause, "")
        .replace(sectorTable) { if (it.value.first().isUpperCase()) "Sector comparison" else "sector comparison" }
        .replace(plainSectorTable) { it.groupValues[1] + "ector comparison" }
        .replace(tableComparison, "sector comparison")
        .replace(namedSectorTable) { it.groupValues[1] + " sector comparison" }
        .replace(hasBeat, "has beaten")

    // ---- Sentence case for step titles ---------------------------------------------------------

    private val properAlways = setOf(
        "January", "February", "March", "April", "May", "June", "July", "August", "September",
        "October", "November", "December", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday",
        "Saturday", "Sunday",
    )

    /** A plain capitalised word: one capital, then lower case, with an optional possessive. */
    private val capitalised = Regex("^([A-Z][a-z]+)(?:'s)?$")
    private val lowerWord = Regex("^[a-z]+(?:'s)?$")

    /**
     * [title] in sentence case when it arrived in Title Case ("Compare Jefferies Against Sector Peers
     * by ROE and Operating Margin" reads "Compare Jefferies against sector peers by ROE and operating
     * margin"), unchanged when it is already in sentence case (device QA of 1.3.18: one answer's
     * headings were one way and the next answer's the other).
     *
     * Only a plain capitalised word is lowered: an acronym (ROE, EPS), a mixed word (F-Score, 10-K),
     * the first word, a month or weekday, a word of one of [names] (the company), and a word
     * [evidence] writes with a capital in the middle of a sentence ("Management's Discussion") keep
     * their capitals. A title is judged to
     * be in Title Case when more of its longer words (four letters and up, after the first) are
     * capitalised than not, so a sentence-case title naming a company is never lowered.
     */
    fun sentenceCase(title: String, evidence: String = "", names: List<String> = emptyList()): String {
        val words = title.split(' ')
        if (words.size < 2) return title
        val cores = words.map { it.trim { c -> !c.isLetterOrDigit() && c != '\'' && c != '-' } }
        val longer = cores.drop(1).filter { it.length >= 4 && it.all { c -> c.isLetter() || c == '\'' } }
        val caps = longer.count { capitalised.matches(it) }
        val lower = longer.count { lowerWord.matches(it) }
        if (caps <= lower) return title
        val keep = properNouns(evidence) + names.flatMap { it.split(' ') }.mapNotNull { capitalised.matchEntire(it.trim(',', '.'))?.groupValues?.get(1) }
        return words.mapIndexed { index, word ->
            val core = cores[index]
            val stem = capitalised.matchEntire(core)?.groupValues?.get(1)
            when {
                index == 0 || stem == null -> word
                stem in properAlways || stem in keep -> word
                else -> word.replaceFirst(core, core.replaceFirstChar { it.lowercaseChar() })
            }
        }.joinToString(" ")
    }

    /** The capitalised words [evidence] writes in the middle of a sentence: names, not openings. */
    private fun properNouns(evidence: String): Set<String> {
        if (evidence.isBlank()) return emptySet()
        val out = HashSet<String>()
        var previous = ""
        for (token in evidence.split(Regex("\\s+"))) {
            val core = token.trim { c -> !c.isLetterOrDigit() && c != '\'' }
            val stem = capitalised.matchEntire(core)?.groupValues?.get(1)
            val opensSentence = previous.isEmpty() || previous.last() in ".!?:"
            if (stem != null && !opensSentence) out += stem
            if (token.isNotEmpty()) previous = token
        }
        return out
    }

    private fun dashes(text: String): String {
        val ranged = text.replace(range, " to ")
        val matches = dash.findAll(ranged).toList()
        if (matches.isEmpty()) return ranged
        val out = StringBuilder(ranged.length)
        var last = 0
        matches.forEachIndexed { index, match ->
            out.append(ranged, last, match.range.first)
            val following = ranged.substring(match.range.last + 1)
            val nextWord = following.trimStart().takeWhile { it.isLetter() }.lowercase()
            val paired = pairedInSentence(ranged, matches, index)
            // A dash that closes a sentence or the whole text has nothing to introduce.
            val atEnd = following.isBlank() || following.trimStart().firstOrNull()?.let { it in ".!?" } == true
            when {
                atEnd -> Unit
                paired || nextWord in joiningWords -> out.append(", ")
                else -> out.append(": ")
            }
            last = match.range.last + 1
        }
        out.append(ranged, last, ranged.length)
        return out.toString()
    }

    /**
     * A sentence end: a stop followed by a space and a capital, or by the end of the text. So
     * "8.6%" is not one, and neither is the stop in "AbbVie Inc. beat" or "Op. margin".
     */
    private val sentenceEnd = Regex("[.!?](?=\\s+[A-Z]|\\s*$)")

    /** True when another dash sits in the same sentence as the one at [index]: the two frame an aside. */
    private fun pairedInSentence(text: String, matches: List<MatchResult>, index: Int): Boolean {
        val here = matches[index].range
        val ends = sentenceEnd.findAll(text).map { it.range.first }.toList()
        val start = ends.lastOrNull { it < here.first } ?: 0
        val end = ends.firstOrNull { it > here.last } ?: text.length
        return matches.withIndex().any { (i, m) -> i != index && m.range.first >= start && m.range.last <= end }
    }

    private fun dates(text: String, currentYear: Int): String = isoDate.replace(text) { match ->
        val (y, m, d) = match.destructured
        val date = runCatching { LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }.getOrNull()
        when {
            date == null -> match.value
            date.year == currentYear -> Fmt.dayMonth(date)
            else -> Fmt.day(date)
        }
    }
}
