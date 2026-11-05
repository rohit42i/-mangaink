package com.example.mangainker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class StudioPageOrderTest {
    private val pages = listOf("page-a", "page-b", "page-c")

    @Test
    fun movesCurrentPageUpAndDownWithoutReorderingOtherPages() {
        assertEquals(listOf("page-b", "page-a", "page-c"), StudioPageOrder.move(pages, "page-b", -1))
        assertEquals(listOf("page-a", "page-c", "page-b"), StudioPageOrder.move(pages, "page-b", 1))
    }

    @Test
    fun keepsOrderWhenMovingPastEitherBoundary() {
        assertEquals(pages, StudioPageOrder.move(pages, "page-a", -1))
        assertEquals(pages, StudioPageOrder.move(pages, "page-c", 1))
    }

    @Test
    fun unknownPageDoesNotChangeOrder() {
        assertEquals(pages, StudioPageOrder.move(pages, "missing", 1))
    }

    @Test
    fun deletingActivePageSelectsThePreviousPage() {
        assertEquals("page-a", StudioPageOrder.activeAfterRemoval(pages, "page-b", "page-b"))
        assertEquals("page-b", StudioPageOrder.activeAfterRemoval(pages, "page-c", "page-c"))
    }

    @Test
    fun deletingAnotherPageKeepsTheCurrentPageSelected() {
        assertEquals("page-c", StudioPageOrder.activeAfterRemoval(pages, "page-a", "page-c"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesToDeleteTheLastPage() {
        StudioPageOrder.remove(listOf("page-a"), "page-a")
    }
}
