package com.failureludo.engine

/** Simple dice roller. Decoupled so it can be replaced with a seeded version in tests. */
class DiceRoller {
    fun roll(): Int = (1..6).random()
}
