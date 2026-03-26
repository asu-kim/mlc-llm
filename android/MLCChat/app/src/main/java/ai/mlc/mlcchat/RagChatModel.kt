package ai.mlc.mlcchat

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlin.math.sqrt
import ai.mlc.mlcllm.MLCEngine
import ai.mlc.mlcllm.generateSync
import ai.mlc.mlcllm.getEmbedding
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.Duration
import java.time.format.DateTimeFormatter

data class EmbeddedText(val embedding: FloatArray, val text: String)

class RagChatModel(private val context: Context) {
   
    private var userName: String = "Unknown"
    private var userRole: String = "User"
    private var userTimezone: String = "PST"
    private var userLocation: String = "Tempe, AZ"

    fun setUserProfile(name: String, role: String, timezone: String) {
        userName = name
        userRole = role
        userTimezone = timezone
    }
    fun setUserLocation(location: String) {
        userLocation = location
        Log.d("RAG_USER_LOCATION", "User location set to: $userLocation")
    }
    fun getUserLocation(): String = userLocation

    private var embeddingList: List<EmbeddedText> = emptyList()
    private val prefs = context.getSharedPreferences("RAG_PREFS", Context.MODE_PRIVATE)

    fun loadEmbeddingsIfNeeded() {
        val file = File(context.getExternalFilesDir(null), "Knowledge_graph.vec")
        if (!file.exists()) return

        val currentTimestamp = file.lastModified()
        val savedTimestamp = prefs.getLong("last_vec_timestamp", 0)

        if (currentTimestamp != savedTimestamp) {
            embeddingList = loadVecFromKGProvider(context)
            prefs.edit().putLong("last_vec_timestamp", currentTimestamp).apply()
            Log.d("RAG", "Embeddings reloaded (file updated).")
        } else {
            Log.d("RAG", "Using cached embeddings (no update).")
        }
    }

    fun clearEmbeddings() {
        embeddingList = emptyList()
        prefs.edit().remove("last_vec_timestamp").apply()
        Log.d("RAG", "Embedding cache cleared.")
    }

    fun runRAGQuery(query: String, engine: MLCEngine): String {
        if (embeddingList.isEmpty()) {
            embeddingList = loadVecFromKGProvider(context)
        }
        val now = ZonedDateTime.now()
        val filtered = getEventsForQuery(query, now, embeddingList)

        val topChunks = retrieveTopK(query, engine, filtered.ifEmpty { embeddingList }, k = 5)

        val relevantContext = topChunks.joinToString("\n") {
            
            val regex = Regex("""(.+?) at ([^/]+)/([^/]+)""")
            val match = regex.matchEntire(it.text.trim())
            if (match != null) {
                val title = match.groupValues[1]
                val startIso = match.groupValues[2]
                val endIso = match.groupValues[3]
                try {
                    val start = ZonedDateTime.parse(startIso)
                    val end = ZonedDateTime.parse(endIso)
                    val dateFmt = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
                    val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
                    "$title: ${dateFmt.format(start)}, ${timeFmt.format(start)}–${timeFmt.format(end)}."
                } catch (e: Exception) {
                    it.text.trim()
                }
            } else {
                it.text.trim()
            }
        }

        val timeFormatted = now.toLocalTime().toString()
        val timeOfDay = when (now.hour) {
            in 5..11 -> "morning"
            12 -> "noon"
            in 13..16 -> "afternoon"
            in 17..20 -> "evening"
            else -> "night"
        }

        val personalizedPrompt = """
            [User: $userName | Role: $userRole | Location: $userLocation | TZ: $userTimezone]
            [Date: ${now.toLocalDate()} | Time: $timeFormatted (${now.zone}) | Part of Day: $timeOfDay]
        """.trimIndent()
        return """
            $personalizedPrompt
            $relevantContext
        """.trimIndent()
    }

    private fun getEventsForQuery(query: String, now: ZonedDateTime, data: List<EmbeddedText>): List<EmbeddedText> {
        val normalized = normalizeText(query)
        val eventsWithTime = data.filter { extractEventTime(it.text) != null }

       
        return when {
            "next week" in normalized -> {
                // Monday to Sunday of *next* week
                val nextWeekStart = now.plusWeeks(1).with(java.time.DayOfWeek.MONDAY).toLocalDate()
                val nextWeekEnd = nextWeekStart.plusDays(6)
                eventsWithTime.filter {
                    val eventDate = extractEventTime(it.text)?.toLocalDate()
                    eventDate != null && !eventDate.isBefore(nextWeekStart) && !eventDate.isAfter(nextWeekEnd)
                }
            }
            "tomorrow" in normalized -> {
                val tomorrow = now.plusDays(1).toLocalDate()
                eventsWithTime.filter {
                    extractEventTime(it.text)?.toLocalDate() == tomorrow
                }
            }
            "today" in normalized -> {
                val today = now.toLocalDate()
                eventsWithTime.filter {
                    extractEventTime(it.text)?.toLocalDate() == today
                }
            }
            "next" in normalized || "upcoming" in normalized || "soon" in normalized -> {
                // Only *future* events, sorted by soonest
                eventsWithTime.filter {
                    val eventStart = extractEventTime(it.text)
                    eventStart != null && eventStart.isAfter(now)
                }.sortedBy { extractEventTime(it.text) }
            }
            else -> data // No filtering! Just return all events.
        }
    }

    
    private fun loadVecFromKGProvider(context: Context): List<EmbeddedText> {
        val uri = Uri.parse("content://com.example.knowledgegraph.kgprovider/knowledge_graph_vec")
        val list = mutableListOf<EmbeddedText>()
        try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    val parts = line.split("\t")
                    if (parts.size != 2) return@forEachIndexed
                    val embeddingStr = parts[0]
                    val text = parts[1]
                    try {
                        val embeddingList = embeddingStr.split(",")
                            .mapNotNull { it.toFloatOrNull() }
                        if (embeddingList.isNotEmpty()) {
                            val norm = sqrt(embeddingList.fold(0f) { acc, x -> acc + x * x })
                            val normalizedEmbedding = if (norm != 0f)
                                embeddingList.map { it / norm }.toFloatArray()
                            else
                                embeddingList.toFloatArray()
                            list.add(EmbeddedText(normalizedEmbedding, text))
                        }
                    } catch (e: Exception) {
                        Log.e("RAG_PARSE", "Failed to parse embedding at line $index: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RAG_FILE", "Failed to open .vec file: ${e.message}")
        }
        return list
    }

    private fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in vec1.indices) {
            dot += vec1[i] * vec2[i]
            normA += vec1[i] * vec1[i]
            normB += vec2[i] * vec2[i]
        }
        return dot / (sqrt(normA) * sqrt(normB) + 1e-8f)
    }

    private fun retrieveTopK(
        query: String,
        engine: MLCEngine,
        data: List<EmbeddedText>,
        k: Int
    ): List<EmbeddedText> {
        val normalizedQuery = normalizeText(query)
        val now = ZonedDateTime.now()
        val location = getUserLocation()
        val timeFormatted = now.toLocalTime().toString()
        val timeOfDay = when (now.hour) {
            in 5..11 -> "morning"
            12 -> "noon"
            in 13..16 -> "afternoon"
            in 17..20 -> "evening"
            else -> "night"
        }

        val enrichedPrompt = """
        User: $userName
        Role: $userRole
        Location: $location
        Time Zone: $userTimezone
        Date: ${now.toLocalDate()}
        Current Time: $timeFormatted (${now.zone})
        Part of Day: $timeOfDay

        Query: $query
        """.trimIndent()
        val queryVec = engine.getEmbedding(enrichedPrompt) ?: return emptyList()

        fun phraseOverlapScore(query: String, text: String): Float {
            val queryTokens = query.split(" ").filter { it.length > 2 }.toSet()
            val textTokens = normalizeText(text).split(" ").toSet()
            val common = queryTokens.intersect(textTokens)
            return common.size.toFloat() / (queryTokens.size + 1e-5f)
        }

        fun temporalProximityScore(text: String): Float {
            val eventTime = extractEventTime(text)
            return if (eventTime != null) {
                val hoursUntil = Duration.between(now, eventTime).toHours().toFloat()
                when {
                    hoursUntil <= 0 -> 0f // Past events
                    hoursUntil <= 2 -> 1.0f
                    hoursUntil <= 12 -> 0.9f
                    hoursUntil <= 24 -> 0.75f
                    hoursUntil <= 48 -> 0.6f
                    hoursUntil <= 72 -> 0.5f
                    hoursUntil <= 168 -> 0.4f
                    else -> 0.1f
                }
            } else 0f
        }

        val scoredData = data.map {
            val cosine = cosineSimilarity(queryVec, it.embedding)
            val overlap = phraseOverlapScore(normalizedQuery, it.text)
            val temporal = temporalProximityScore(it.text)
            val finalScore = if (isTimeSensitiveQuery(query)) {
                0.6f * cosine + 0.2f * overlap + 0.2f * temporal
            } else {
                0.7f * cosine + 0.3f * overlap
            }
            it to finalScore
        }

        val keywordMatches = data.filter {
            normalizedQuery.split(" ").any { word ->
                word.length > 3 && it.text.lowercase().contains(word.lowercase())
            }
        }

        val topScored = scoredData
            .filterNot { keywordMatches.contains(it.first) }
            .sortedByDescending { it.second }
            .map { it.first }
            .take((k - keywordMatches.size).coerceAtLeast(0))

        val finalContext = (keywordMatches + topScored)
            .distinctBy { it.text }
            .sortedBy { extractEventTime(it.text) } // sort chronologically

        return finalContext
    }

    private fun extractEventTime(text: String): ZonedDateTime? {
        val regex = Regex("""at\s+([0-9T:\-+Z/]+)""")
        val match = regex.find(text)
        val dateRange = match?.groupValues?.get(1)
        val startDateStr = dateRange?.split("/")?.firstOrNull()
        return try {
            if (startDateStr != null) {
                ZonedDateTime.parse(startDateStr)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun normalizeText(text: String): String {
        return text.lowercase()
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isTimeSensitiveQuery(query: String): Boolean {
        val TIME_KEYWORDS = listOf(
            "next", "upcoming", "today", "tomorrow", "this week", "weekend",
            "soon", "schedule", "calendar", "what's", "any events", "anything"
        )
        val q = query.lowercase()
        return TIME_KEYWORDS.any { it in q }
    }
    fun getUserHeader(): String {
        val now = ZonedDateTime.now()
        val timeFormatted = now.toLocalTime().toString()
        val timeOfDay = when (now.hour) {
            in 5..11 -> "morning"
            12 -> "noon"
            in 13..16 -> "afternoon"
            in 17..20 -> "evening"
            else -> "night"
        }

        return """
        [User: $userName | Role: $userRole | Location: $userLocation | TZ: $userTimezone]
        [Date: ${now.toLocalDate()} | Time: $timeFormatted (${now.zone}) | Part of Day: $timeOfDay]
    """.trimIndent()
    }
}

