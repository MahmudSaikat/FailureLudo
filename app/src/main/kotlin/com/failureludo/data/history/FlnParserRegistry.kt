package com.failureludo.data.history

interface FlnParser {
    val supportedMajorVersion: Int

    fun parse(text: String): FlnParseResult
}

class FlnParserRegistry(
    parsers: Collection<FlnParser>
) {
    private val parsersByMajor = parsers.associateBy { it.supportedMajorVersion }

    constructor(vararg parsers: FlnParser) : this(parsers.toList())

    fun parse(text: String): FlnParseResult {
        val preview = previewMetadata(text)
        val format = preview.format
        if (format != null && format != FLN_FORMAT) {
            return FlnParseResult.InvalidFormat(
                reason = "Unsupported format '$format'."
            )
        }

        val detectedVersion = preview.flnVersion
            ?: return FlnParseResult.InvalidFormat(
                reason = "Missing FLN header 'FlnVersion'."
            )

        val majorVersion = parseMajorVersion(detectedVersion)
            ?: return FlnParseResult.InvalidFormat(
                reason = "Invalid FLN version '$detectedVersion'."
            )

        val parser = parsersByMajor[majorVersion]
            ?: return FlnParseResult.UnsupportedVersion(
                detectedVersion = detectedVersion,
                supportedMajorVersions = parsersByMajor.keys,
                metadata = preview
            )

        return parser.parse(text)
    }

    private fun previewMetadata(text: String): FlnMetadataPreview {
        val valuesByTag = linkedMapOf<String, String>()
        val players = mutableListOf<String>()

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                if (valuesByTag.isNotEmpty() || players.isNotEmpty()) {
                    break
                }
                continue
            }

            val parsed = parseHeaderLine(line) ?: continue
            if (parsed.first == "Player") {
                players += parsed.second
            } else {
                valuesByTag[parsed.first] = parsed.second
            }
        }

        return FlnMetadataPreview(
            format = valuesByTag["Format"],
            flnVersion = valuesByTag["FlnVersion"],
            rulesetVersion = valuesByTag["RulesetVersion"],
            gameId = valuesByTag["GameId"],
            status = valuesByTag["Status"],
            createdAt = valuesByTag["CreatedAt"],
            updatedAt = valuesByTag["UpdatedAt"],
            players = players
        )
    }

    private fun parseMajorVersion(version: String): Int? {
        return version.substringBefore('.').toIntOrNull()
    }

    companion object {
        fun default(): FlnParserRegistry = FlnParserRegistry(FlnV1Codec())
    }
}
