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
