package com.failureludo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class SelfPlayDatasetExporterTest {

    @Test
    fun episodeToJsonlLines_matchesStepCount() {
        val episode = SelfPlayRunner.playEpisode(
            config = SelfPlayConfig(
                activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE),
                maxPly = 60,
                seed = 12L
            ),
            defaultPolicy = RandomSelfPlayPolicy
        )

        val lines = SelfPlayDatasetExporter.episodeToJsonlLines(episode)

        assertEquals(episode.steps.size, lines.size)
        assertTrue(lines.all { line -> line.startsWith("{") && line.endsWith("}") })
        assertTrue(lines.all { line -> line.contains("\"version\":1") })
    }

    @Test
    fun exportEpisodesToJsonl_writesAllLines() {
        val episodes = SelfPlayRunner.playEpisodes(
            episodeCount = 2,
            config = SelfPlayConfig(
                activeColors = listOf(PlayerColor.RED, PlayerColor.BLUE),
                maxPly = 40,
                seed = 21L
            ),
            defaultPolicy = HeuristicSelfPlayPolicy
        )

        val outputPath = Files.createTempFile("self-play-dataset", ".jsonl")
        val outputFile = outputPath.toFile()

        try {
            val written = SelfPlayDatasetExporter.exportEpisodesToJsonl(
                episodes = episodes,
                outputFile = outputFile
            )

            val lines = outputFile.readLines(Charsets.UTF_8)
            assertEquals(written, lines.size)
            assertTrue(lines.isNotEmpty())
            assertTrue(lines.any { line -> line.contains("\"actionType\":\"MOVE\"") })
        } finally {
            outputFile.delete()
        }
    }
}