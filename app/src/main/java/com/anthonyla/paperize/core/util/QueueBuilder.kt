package com.anthonyla.paperize.core.util

import kotlin.random.Random

object QueueBuilder {
    /**
     * Album order starting half-way through, for a lock screen that shares the home screen's album
     * in sequential mode. Each new round starts at the same place, so the lock screen still steps
     * through the album in order.
     */
    fun startingHalfway(ids: List<String>): List<String> {
        val start = ids.size / 2
        return ids.drop(start) + ids.take(start)
    }

    /**
     * A shuffled round in which each of [favorites] comes up twice and every other image once
     * ("Show more often"). The round is two shuffled halves that each hold every favourite and half
     * of the others, so a favourite's two turns are spread apart; the same image never comes up
     * twice in a row where the halves meet.
     */
    fun weightedShuffle(ids: List<String>, favorites: Set<String>, random: Random = Random.Default): List<String> {
        val favored = ids.filter { it in favorites }
        // With no favourites, or only favourites, every image is equally likely anyway.
        if (favored.isEmpty() || favored.size == ids.size) return ids.shuffled(random)
        val others = ids.filterNot { it in favorites }.shuffled(random)
        val half = (others.size + random.nextInt(2)) / 2
        val first = (favored + others.take(half)).shuffled(random).toMutableList()
        val second = (favored + others.drop(half)).shuffled(random).toMutableList()
        if (second.first() == first.last()) {
            val inSecond = (1 until second.size).firstOrNull { second[it] != first.last() }
            if (inSecond != null) {
                second.swap(0, inSecond)
            } else {
                (first.lastIndex - 1 downTo 0).firstOrNull { first[it] != second.first() }
                    ?.let { first.swap(first.lastIndex, it) }
            }
        }
        return first + second
    }

    private fun MutableList<String>.swap(a: Int, b: Int) {
        this[a] = this[b].also { this[b] = this[a] }
    }

    /**
     * A round in progress after [ids] became favourites in "Show more often": each gets one more
     * turn at a random place, so it still comes up twice as often as the rest this round.
     */
    fun addExtraTurns(queue: List<String>, ids: Collection<String>, random: Random = Random.Default): List<String> {
        val merged = queue.toMutableList()
        ids.forEach { merged.add(random.nextInt(merged.size + 1), it) }
        return merged
    }

    /** A round in progress after [ids] stopped being favourites: a second queued turn is dropped. */
    fun removeExtraTurns(queue: List<String>, ids: Collection<String>): List<String> {
        val result = queue.toMutableList()
        ids.toSet().forEach { id ->
            if (result.count { it == id } > 1) result.removeAt(result.lastIndexOf(id))
        }
        return result
    }

    /**
     * Add [newIds] to a queue that is part-way through its round, keeping the progress made so far.
     *
     * A shuffled queue takes each new image at a random place. A sequential queue puts it straight
     * after the queued image that comes before it in [rotationOrder], or, when none does, in front
     * of the queued image that comes first; so a queue that started part-way through the album (the
     * lock screen's) still runs in album order.
     */
    fun mergeNew(
        queue: List<String>,
        newIds: List<String>,
        rotationOrder: List<String>,
        shuffle: Boolean,
        random: Random = Random.Default
    ): List<String> {
        val merged = queue.toMutableList()
        if (shuffle) {
            newIds.forEach { merged.add(random.nextInt(merged.size + 1), it) }
            return merged
        }
        val rank = rotationOrder.withIndex().associate { (index, id) -> id to index }
        newIds.sortedBy { rank[it] ?: Int.MAX_VALUE }.forEach { id ->
            val newRank = rank[id] ?: Int.MAX_VALUE
            val ranked = merged.withIndex().filter { rank[it.value] != null }
            val before = ranked.filter { rank.getValue(it.value) < newRank }.maxByOrNull { rank.getValue(it.value) }
            val position = when {
                before != null -> before.index + 1
                ranked.isNotEmpty() -> ranked.minBy { rank.getValue(it.value) }.index
                else -> merged.size
            }
            merged.add(position, id)
        }
        return merged
    }
}
