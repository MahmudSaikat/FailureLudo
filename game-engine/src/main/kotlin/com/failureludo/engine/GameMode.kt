package com.failureludo.engine

/** How many players participate and whether teams are active. */
enum class GameMode {
    /** 2–4 individual players compete; first one to finish all pieces wins. */
    FREE_FOR_ALL,

    /** 2 teams of 2 (RED+YELLOW vs BLUE+GREEN). A team wins when both members
     *  have finished all their pieces. Only valid when exactly 4 colors are active. */
    TEAM
}
