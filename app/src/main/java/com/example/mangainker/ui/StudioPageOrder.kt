package com.example.mangainker.ui

/** Pure page-sequence operations shared by the Studio store and JVM tests. */
internal object StudioPageOrder {
    fun move(ids: List<String>, pageId: String, offset: Int): List<String> {
        val index = ids.indexOf(pageId)
        if (index < 0 || offset == 0 || ids.isEmpty()) return ids
        val target = (index + offset).coerceIn(0, ids.lastIndex)
        if (target == index) return ids
        return ids.toMutableList().also { ordered ->
            val page = ordered.removeAt(index)
            ordered.add(target, page)
        }
    }

    fun remove(ids: List<String>, pageId: String): List<String> {
        require(ids.size > 1) { "A project must keep at least one page." }
        require(pageId in ids) { "The selected page no longer exists." }
        return ids.filterNot { it == pageId }
    }

    fun activeAfterRemoval(ids: List<String>, removedPageId: String, activePageId: String?): String {
        val remaining = remove(ids, removedPageId)
        if (activePageId != null && activePageId != removedPageId && activePageId in remaining) return activePageId
        val removedIndex = ids.indexOf(removedPageId)
        return remaining[(removedIndex - 1).coerceAtLeast(0).coerceAtMost(remaining.lastIndex)]
    }
}
