package com.plainticker.mobile.ui.list

/**
 * The analyzed section chaptered by sector (task A1, docs/plan-monetisation-2026-09-19.md section
 * 1.5): "a heading per sector with its count, sectors ordered by count, rows by composite within".
 *
 * The plan's own words order sectors by count, heaviest first; this reads them alphabetically
 * instead, a deliberate departure. Coverage is not static: the weekly vote (section 1.4) and `S4`
 * grow it every round, so a count-ordered heading would reshuffle release over release with no
 * relationship to anything the reader did, while an alphabetical one is the same list a returning
 * reader saw last time, forever, at the cost of never fronting the single biggest sector. Once the
 * bundled snapshot itself carries `sector` (task A1 follow-up), first paint chapters correctly
 * from the day it was captured, so this ordering is live from the first frame, not just once the
 * network answers.
 *
 * `ListUiState.analyzed` already carries every row in the order the join sorts it, composite
 * descending ([ListViewModel.republish]), so grouping here never re-sorts a row: each chapter
 * keeps the rows in the order they arrive, which is that same composite order.
 */

/** One sector's slice of the analyzed section. [sector] is null only for the trailing chapter. */
data class SectorChapter(val sector: String?, val rows: List<ListRow>)

/**
 * [ListUiState.analyzed], chaptered by sector. Empty exactly when [ListUiState.analyzed] is.
 *
 * Sectors are ordered alphabetically by their own name, so the heading order never depends on
 * coverage, which sector `/summary` happened to grow this week, or which row arrived first. A row
 * `/summary` sent no sector for is not dropped: it is grouped into one chapter of its own that
 * always trails, whatever its count, because a sector this app never named is not a sector to sort
 * among the ones it did name.
 */
val ListUiState.analyzedChapters: List<SectorChapter> get() = analyzed.chapteredBySector()

internal fun List<ListRow>.chapteredBySector(): List<SectorChapter> {
    if (isEmpty()) return emptyList()
    val grouped = LinkedHashMap<String?, MutableList<ListRow>>()
    forEach { row -> grouped.getOrPut(row.sector) { mutableListOf() } += row }
    val named = grouped.entries
        .filter { it.key != null }
        .sortedBy { it.key }
        .map { SectorChapter(it.key, it.value) }
    val sectorless = grouped[null]?.let { SectorChapter(null, it) }
    return if (sectorless == null) named else named + sectorless
}
