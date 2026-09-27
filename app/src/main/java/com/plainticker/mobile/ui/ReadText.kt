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
 *
 * Pure: no Android, no clock; the caller passes the year.
 */
object ReadText {

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

    /** [text] with its punctuation dashes and ISO dates rewritten; see the class doc. */
    fun normalize(text: String, currentYear: Int): String = dates(dashes(text), currentYear)

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

    /** A sentence end: a stop followed by a space or the end, so "8.6%" is not one. */
    private val sentenceEnd = Regex("[.!?](?=\\s|$)")

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
