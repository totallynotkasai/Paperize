package com.anthonyla.paperize.core.util

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueBuilderTest {
    private val album = listOf("a", "b", "c", "d", "e", "f")

    @Test
    fun `lock screen rounds start half-way and still run in album order`() {
        assertEquals(listOf("d", "e", "f", "a", "b", "c"), QueueBuilder.startingHalfway(album))
        assertEquals(listOf("b", "c", "a"), QueueBuilder.startingHalfway(listOf("a", "b", "c")))
        assertEquals(listOf("a"), QueueBuilder.startingHalfway(listOf("a")))
        assertEquals(emptyList<String>(), QueueBuilder.startingHalfway(emptyList()))
    }

    @Test
    fun `new images join a sequential round at their place in the album`() {
        val rotation = listOf("a", "b", "new1", "c", "d", "e", "new2", "f")
        // "a" and "b" were already shown; "new1" belongs before "c", "new2" before "f".
        assertEquals(
            listOf("c", "d", "e", "new2", "f"),
            QueueBuilder.mergeNew(listOf("c", "d", "e", "f"), listOf("new2"), rotation, shuffle = false)
        )
        assertEquals(
            listOf("new1", "c", "d", "e", "new2", "f"),
            QueueBuilder.mergeNew(listOf("c", "d", "e", "f"), listOf("new2", "new1"), rotation, shuffle = false)
        )
    }

    @Test
    fun `a round that started half-way keeps wrapping round the album`() {
        val rotation = listOf("first", "a", "b", "c", "d", "e", "f", "last")
        // Lock round: d e f | a b c, with "d" already shown.
        assertEquals(
            listOf("e", "f", "last", "first", "a", "b", "c"),
            QueueBuilder.mergeNew(listOf("e", "f", "a", "b", "c"), listOf("first", "last"), rotation, shuffle = false)
        )
    }

    @Test
    fun `new images join a shuffled round at random places, each once`() {
        val queue = listOf("a", "b", "c")
        val merged = QueueBuilder.mergeNew(queue, listOf("x", "y"), album, shuffle = true, random = Random(7))
        assertEquals(5, merged.size)
        assertEquals(queue, merged.filter { it in queue })
        assertEquals(setOf("x", "y"), merged.filterNot { it in queue }.toSet())
    }

    @Test
    fun `an empty queue simply takes the new images in order`() {
        assertEquals(listOf("b", "e"), QueueBuilder.mergeNew(emptyList(), listOf("e", "b"), album, shuffle = false))
    }

    @Test
    fun `show more often gives each favourite two turns per round, one in each half`() {
        repeat(200) { seed ->
            val favorites = setOf("b", "e")
            val round = QueueBuilder.weightedShuffle(album, favorites, Random(seed))
            assertEquals(album.size + favorites.size, round.size)
            album.forEach { id -> assertEquals(if (id in favorites) 2 else 1, round.count { it == id }) }
            // The halves meet without showing the same image twice in a row.
            round.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
            val firstHalf = round.take(favorites.size + (album.size - favorites.size) / 2 + 1)
            favorites.forEach { assertTrue(it in firstHalf) }
        }
    }

    @Test
    fun `a single favourite next to a single other image never repeats back to back`() {
        repeat(100) { seed ->
            val round = QueueBuilder.weightedShuffle(listOf("fav", "x"), setOf("fav"), Random(seed))
            assertEquals(listOf("fav", "x", "fav"), round)
        }
    }

    @Test
    fun `with no favourites, or only favourites, a weighted round is a plain shuffle`() {
        assertEquals(album.sorted(), QueueBuilder.weightedShuffle(album, emptySet(), Random(1)).sorted())
        assertEquals(album.sorted(), QueueBuilder.weightedShuffle(album, album.toSet(), Random(1)).sorted())
        // Favourites that aren't in the round (excluded, say) don't add turns.
        assertEquals(album.sorted(), QueueBuilder.weightedShuffle(album, setOf("gone"), Random(1)).sorted())
    }

    @Test
    fun `a new favourite gains a turn and a former one loses its second`() {
        val queue = listOf("a", "b", "c")
        val gained = QueueBuilder.addExtraTurns(queue, listOf("b", "z"), Random(3))
        assertEquals(listOf("a", "b", "b", "c", "z").sorted(), gained.sorted())
        assertEquals(queue, QueueBuilder.removeExtraTurns(listOf("a", "b", "c"), listOf("b")))
        assertEquals(listOf("b", "a", "c"), QueueBuilder.removeExtraTurns(listOf("b", "a", "b", "c"), listOf("b", "c")))
    }
}
