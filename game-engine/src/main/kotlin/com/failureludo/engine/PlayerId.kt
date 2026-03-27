package com.failureludo.engine

@JvmInline
value class PlayerId(val value: Int) {
    init {
        require(value in 1..4) { "PlayerId must be between 1 and 4." }
    }

    val displayLabel: String
        get() = "Player-$value"
}
