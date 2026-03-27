package com.failureludo.engine

/** Simple dice roller. Decoupled so it can be replaced with a seeded version in tests. */
class DiceRoller {
    // Gentle bias: 5 and 6 are slightly more likely than 1-4.
    // Probabilities: 1-4 -> 15% each, 5-6 -> 20% each.
    private val weightedFaces = listOf(
        1, 1, 1,
        2, 2, 2,
        3, 3, 3,
        4, 4, 4,
        5, 5, 5, 5,
        6, 6, 6, 6,
    )

    fun roll(): Int = weightedFaces.random()
}
