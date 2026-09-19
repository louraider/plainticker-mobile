package com.plainticker.mobile.ui.list

/**
 * The analyzed section chaptered by sector (task A1, docs/plan-monetisation-2026-09-19.md section
 * 1.5): "a heading per sector with its count, sectors ordered by count, rows by composite within".
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
 * Sectors are ordered by how many rows they carry, heaviest first, so the biggest slices of the
 * list lead it; a tie is broken by the sector's own name, so the order does not depend on which
 * row `/summary` happened to serve first. A row `/summary` sent no sector for is not dropped: it
 * is grouped into one chapter of its own that always trails, whatever its count, because a sector
 * this app never named is not a sector to sort among the ones it did name.
 */
val ListUiState.analyzedChapters: List<SectorChapter> get() = analyzed.chapteredBySector()

internal fun List<ListRow>.chapteredBySector(): List<SectorChapter> {
    if (isEmpty()) return emptyList()
    val grouped = LinkedHashMap<String?, MutableList<ListRow>>()
    forEach { row -> grouped.getOrPut(row.sector) { mutableListOf() } += row }
    val named = grouped.entries
        .filter { it.key != null }
        .sortedWith(compareByDescending<Map.Entry<String?, MutableList<ListRow>>> { it.value.size }.thenBy { it.key })
        .map { SectorChapter(it.key, it.value) }
    val sectorless = grouped[null]?.let { SectorChapter(null, it) }
    return if (sectorless == null) named else named + sectorless
}
