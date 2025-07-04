//package ai.mlc.mlcchat
//
//import android.content.Context
//import android.net.Uri
//import android.util.Log
//import kotlin.math.sqrt
//import ai.mlc.mlcllm.MLCEngine
//import ai.mlc.mlcllm.generateSync
//import ai.mlc.mlcllm.getEmbedding
//import java.io.File
//import java.time.LocalDate
//import java.time.ZonedDateTime
//import java.time.format.DateTimeFormatter
//import java.util.*
//import java.time.Duration
//
//data class EmbeddedText(val embedding: FloatArray, val text: String)
//
//class RagChatModel(private val context: Context) {
//    private var userName: String = "Unknown"
//    private var userRole: String = "User"
//    private var userTimezone: String = "PST"
//    private var userLocation: String = "Tempe, AZ"
//    private val TIME_KEYWORDS = listOf(
//        "next", "upcoming", "today", "tomorrow", "this week", "weekend",
//        "soon", "schedule", "calendar", "what's", "any events", "anything"
//    )
//    private fun isTimeSensitiveQuery(query: String): Boolean {
//        val q = query.lowercase()
//        return TIME_KEYWORDS.any { it in q }
//    }
//    fun setUserProfile(name: String, role: String, timezone: String) {
//        userName = name
//        userRole = role
//        userTimezone = timezone
//    }
//    fun setUserLocation(location: String) {
//        userLocation = location
//        Log.d("RAG_USER_LOCATION", "User location set to: $userLocation")
//    }
//
//    fun getUserLocation(): String {
//        return userLocation
//    }
//    private var embeddingList: List<EmbeddedText> = emptyList()
//    private val prefs = context.getSharedPreferences("RAG_PREFS", Context.MODE_PRIVATE)
//
//    fun loadEmbeddingsIfNeeded() {
//        val file = File(context.getExternalFilesDir(null), "Knowledge_graph.vec")
//        if (!file.exists()) return
//
//        val currentTimestamp = file.lastModified()
//        val savedTimestamp = prefs.getLong("last_vec_timestamp", 0)
//
//        if (currentTimestamp != savedTimestamp) {
//            embeddingList = loadVecFromKGProvider(context)
//            prefs.edit().putLong("last_vec_timestamp", currentTimestamp).apply()
//            Log.d("RAG", "Embeddings reloaded (file updated).")
//        } else {
//            Log.d("RAG", "Using cached embeddings (no update).")
//        }
//    }
//
//    fun clearEmbeddings() {
//        embeddingList = emptyList()
//        prefs.edit().remove("last_vec_timestamp").apply()
//        Log.d("RAG", "Embedding cache cleared.")
//    }
//
////
//    fun runRAGQuery(query: String, engine: MLCEngine): String {
//    Log.d("RAG_DEBUG", "runRAGQuery called with query: $query")
//
//    if (embeddingList.isEmpty()) {
//        embeddingList = loadVecFromKGProvider(context)
//    }
//
//    Log.d("RAG_DEBUG", "Embedding list size after load: ${embeddingList.size}")
//
////    val topChunks = retrieveTopK(query, engine, embeddingList, k = 5)
//    val todayChunks = embeddingList.filter { isTodayEvent(it.text) }
//    val topChunks = retrieveTopK(query, engine, todayChunks.ifEmpty { embeddingList }, k = 5)
//
//    Log.d("RAG_DEBUG", "Top chunks retrieved: ${topChunks.map { it.text }}")
//
//    val relevantContext = topChunks.joinToString("\n") {
//        it.text.trim().let { line -> if (!line.endsWith(".")) "$line." else line }
//    }
//
//
//    val now = ZonedDateTime.now()
//    val location = getUserLocation()
//    val timeFormatted = now.toLocalTime().toString()
//    val timeOfDay = when (now.hour) {
//        in 5..11 -> "morning"
//        12 -> "noon"
//        in 13..16 -> "afternoon"
//        in 17..20 -> "evening"
//        else -> "night"
//    }
//
//    val personalizedPrompt = """
//        [User: $userName | Role: $userRole | Location: $location | TZ: $userTimezone]
//        [Date: ${now.toLocalDate()} | Time: $timeFormatted (${now.zone}) | Part of Day: $timeOfDay]
//    """.trimIndent()
//    return """
//        $personalizedPrompt
//        $relevantContext
//    """.trimIndent()
//
//}
//
//    fun getUserHeader(): String {
//        val now = ZonedDateTime.now()
//        val timeFormatted = now.toLocalTime().toString()
//        val timeOfDay = when (now.hour) {
//            in 5..11 -> "morning"
//            12 -> "noon"
//            in 13..16 -> "afternoon"
//            in 17..20 -> "evening"
//            else -> "night"
//        }
//
//        return """
//        [User: $userName | Role: $userRole | Location: $userLocation | TZ: $userTimezone]
//        [Date: ${now.toLocalDate()} | Time: $timeFormatted (${now.zone}) | Part of Day: $timeOfDay]
//    """.trimIndent()
//    }
//
//    private fun loadVecFromKGProvider(context: Context): List<EmbeddedText> {
//        val uri = Uri.parse("content://com.example.knowledgegraph.kgprovider/knowledge_graph_vec")
//        val list = mutableListOf<EmbeddedText>()
//
//        try {
//            context.contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
//                lines.forEachIndexed { index, line ->
//                    val parts = line.split("\t")
//                    if (parts.size != 2) return@forEachIndexed
//
//                    val embeddingStr = parts[0]
//                    val text = parts[1]
//
//                    try {
//                        val embeddingList = embeddingStr.split(",")
//                            .mapNotNull { it.toFloatOrNull() }
//
//                        if (embeddingList.isNotEmpty()) {
//                            val norm = sqrt(embeddingList.fold(0f) { acc, x -> acc + x * x })
//                            val normalizedEmbedding = if (norm != 0f)
//                                embeddingList.map { it / norm }.toFloatArray()
//                            else
//                                embeddingList.toFloatArray()
//
//                            list.add(EmbeddedText(normalizedEmbedding, text))
//                        }
//
//                    } catch (e: Exception) {
//                        Log.e("RAG_PARSE", "Failed to parse embedding at line $index: ${e.message}")
//                    }
//                }
//            }
//        } catch (e: Exception) {
//            Log.e("RAG_FILE", "Failed to open .vec file: ${e.message}")
//        }
//
//        return list
//    }
//
//    private fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
//        var dot = 0f
//        var normA = 0f
//        var normB = 0f
//        for (i in vec1.indices) {
//            dot += vec1[i] * vec2[i]
//            normA += vec1[i] * vec1[i]
//            normB += vec2[i] * vec2[i]
//        }
//        return dot / (sqrt(normA) * sqrt(normB) + 1e-8f)
//    }
//
//    fun isTodayEvent(text: String): Boolean {
//        val today = LocalDate.now()
//        return Regex("""\b(?:starts at)\s+(.*)""").find(text)?.groupValues?.get(1)?.let { dateStr ->
//            try {
//                val parsed = ZonedDateTime.parse(dateStr, DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy", Locale.US))
//                parsed.toLocalDate() == today
//            } catch (e: Exception) {
//                false
//            }
//        } ?: false
//    }
//
//    fun extractUserContextFromVec(data: List<EmbeddedText>): String {
//        return data.mapNotNull { item ->
//            if (item.text.startsWith("User ")) item.text else null
//        }.joinToString(". ") + "."
//    }
//
//    fun isWithinNext7Days(text: String): Boolean {
//        val now = ZonedDateTime.now()
//        val in7Days = now.plusDays(7)
//
//        return Regex("""\bstarts at\s+(.*)""").find(text)?.groupValues?.get(1)?.let { dateStr ->
//            try {
//                val parsed = ZonedDateTime.parse(dateStr, DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy", Locale.US))
//                parsed.isAfter(now) && parsed.isBefore(in7Days)
//            } catch (e: Exception) {
//                false
//            }
//        } ?: false
//    }
//
//    fun isFutureEvent(text: String): Boolean {
//        return Regex("""\bstarts at\s+(.*)""").find(text)?.groupValues?.get(1)?.let { dateStr ->
//            try {
//                val parsed = ZonedDateTime.parse(dateStr, DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy", Locale.US))
//                parsed.isAfter(ZonedDateTime.now())
//            } catch (e: Exception) {
//                false
//            }
//        } ?: false
//    }
////    private fun retrieveTopK(
////        query: String,
////        engine: MLCEngine,
////        data: List<EmbeddedText>,
////        k: Int
////    ): List<EmbeddedText> {
////        val normalizedQuery = normalizeText(query)
//////        val queryVec = engine.getEmbedding(normalizedQuery) ?: return emptyList()
////
////        val now = ZonedDateTime.now()
////        val location = getUserLocation()
////        val timeFormatted = now.toLocalTime().toString()
////        val timeOfDay = when (now.hour) {
////            in 5..11 -> "morning"
////            12 -> "noon"
////            in 13..16 -> "afternoon"
////            in 17..20 -> "evening"
////            else -> "night"
////        }
////
////        val enrichedPrompt = """
////            User: $userName
////            Role: $userRole
////            Location: $location
////            Time Zone: $userTimezone
////            Date: ${now.toLocalDate()}
////            Current Time: $timeFormatted (${now.zone})
////            Part of Day: $timeOfDay
////
////            Query: $query
////        """.trimIndent()
////        val queryVec = engine.getEmbedding(enrichedPrompt) ?: return emptyList()
////
////        fun phraseOverlapScore(query: String, text: String): Float {
////            val queryTokens = query.split(" ").filter { it.length > 2 }.toSet()
////            val textTokens = normalizeText(text).split(" ").toSet()
////            val common = queryTokens.intersect(textTokens)
////            return common.size.toFloat() / (queryTokens.size + 1e-5f)
////        }
////        val filteredData = if (isTimeSensitiveQuery(query)) {
////            // Only consider events with valid start times
////            val eventsWithTime = data.filter { extractEventTime(it.text) != null }
////            val now = ZonedDateTime.now()
////            val (future, past) = eventsWithTime.partition { extractEventTime(it.text)!!.isAfter(now) }
////
////            when {
////                // Prefer next upcoming event(s) (can adjust window here)
////                future.isNotEmpty() -> {
////                    val soonest = future.minByOrNull { Duration.between(now, extractEventTime(it.text)!!).toMillis() }
////                    listOfNotNull(soonest)
////                }
////                // Else, fallback to most recent past event
////                past.isNotEmpty() -> {
////                    val latest = past.maxByOrNull { Duration.between(extractEventTime(it.text)!!, now).toMillis() }
////                    listOfNotNull(latest)
////                }
////                else -> emptyList()
////            }
////        } else {
////            data
////        }
////
////        fun temporalProximityScore(text: String): Float {
////            return Regex("""\bstarts at\s+(.*)""").find(text)?.groupValues?.get(1)?.let { dateStr ->
////                try {
////                    val now = ZonedDateTime.now()
////                    val eventTime = ZonedDateTime.parse(dateStr, DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy", Locale.US))
////                    val hoursUntil = Duration.between(now, eventTime).toHours().toFloat()
////
////                    return when {
////                        hoursUntil <= 0 -> 0f // Past events
////                        hoursUntil <= 2 -> 1.0f
////                        hoursUntil <= 12 -> 0.9f
////                        hoursUntil <= 24 -> 0.75f
////                        hoursUntil <= 48 -> 0.6f
////                        hoursUntil <= 72 -> 0.5f
////                        hoursUntil <= 168 -> 0.4f
////                        else -> 0.1f
////                    }
////                } catch (e: Exception) {
////                    0f
////                }
////            } ?: 0f
////        }
////        val scoredData = filteredData.map {
////            val cosine = cosineSimilarity(queryVec, it.embedding)
////            val overlap = phraseOverlapScore(normalizedQuery, it.text)
//////            val finalScore = 0.7f * cosine + 0.3f * overlap
////
////            val temporal = temporalProximityScore(it.text)
////            val finalScore = if (isTimeSensitiveQuery(query)) {
////                0.6f * cosine + 0.2f * overlap + 0.2f * temporal
////            } else {
////                0.7f * cosine + 0.3f * overlap
////            }
////
////            it to finalScore
////        }
////
////        val keywordMatches = filteredData.filter {
////            normalizedQuery.split(" ").any { word ->
////                word.length > 3 && it.text.lowercase().contains(word)
////            }
////        }
////
////
////        val topScored = scoredData
////            .filterNot { keywordMatches.contains(it.first) }
////            .sortedByDescending { it.second }
////            .map { it.first }
////            .take((k - keywordMatches.size).coerceAtLeast(0))
////
//////        val finalContext = (forcedMatches + topScored).distinctBy { it.text } val finalContext = (forcedMatches + topScored)
////        val finalContext = (keywordMatches + topScored)
////            .distinctBy { it.text }
////            .sortedBy { extractEventTime(it.text) } // sort chronologically
////        finalContext.forEach {
////            Log.d("RAG_SIMILARITY", "Included in final context: '${it.text}'")
////        }
////
////        return finalContext
////    }
//
//    private fun retrieveTopK(
//        query: String,
//        engine: MLCEngine,
//        data: List<EmbeddedText>,
//        k: Int
//    ): List<EmbeddedText> {
//        val normalizedQuery = normalizeText(query)
//        val now = ZonedDateTime.now()
//        val location = getUserLocation()
//        val timeFormatted = now.toLocalTime().toString()
//        val timeOfDay = when (now.hour) {
//            in 5..11 -> "morning"
//            12 -> "noon"
//            in 13..16 -> "afternoon"
//            in 17..20 -> "evening"
//            else -> "night"
//        }
//
//        val enrichedPrompt = """
//        User: $userName
//        Role: $userRole
//        Location: $location
//        Time Zone: $userTimezone
//        Date: ${now.toLocalDate()}
//        Current Time: $timeFormatted (${now.zone})
//        Part of Day: $timeOfDay
//
//        Query: $query
//    """.trimIndent()
//        val queryVec = engine.getEmbedding(enrichedPrompt) ?: return emptyList()
//
//        fun phraseOverlapScore(query: String, text: String): Float {
//            val queryTokens = query.split(" ").filter { it.length > 2 }.toSet()
//            val textTokens = normalizeText(text).split(" ").toSet()
//            val common = queryTokens.intersect(textTokens)
//            return common.size.toFloat() / (queryTokens.size + 1e-5f)
//        }
//
//        val filteredData = if (isTimeSensitiveQuery(query)) {
//            val eventsWithTime = data.filter { extractEventTime(it.text) != null }
//            val (future, past) = eventsWithTime.partition { extractEventTime(it.text)!!.isAfter(now) }
//            when {
//                future.isNotEmpty() -> {
//                    // Next k upcoming events
//                    future.sortedBy { Duration.between(now, extractEventTime(it.text)!!).toMillis() }
//                        .take(k)
//                }
//                past.isNotEmpty() -> {
//                    // Most recent past event
//                    past.sortedByDescending { Duration.between(extractEventTime(it.text)!!, now).toMillis() }
//                        .take(1)
//                }
//                else -> emptyList()
//            }
//        } else {
//            data
//        }
//
//        fun temporalProximityScore(text: String): Float {
//            return Regex("""\bstarts at\s+(.*)""").find(text)?.groupValues?.get(1)?.let { dateStr ->
//                try {
//                    val eventTime = ZonedDateTime.parse(dateStr, DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy", Locale.US))
//                    val hoursUntil = Duration.between(now, eventTime).toHours().toFloat()
//                    when {
//                        hoursUntil <= 0 -> 0f // Past events
//                        hoursUntil <= 2 -> 1.0f
//                        hoursUntil <= 12 -> 0.9f
//                        hoursUntil <= 24 -> 0.75f
//                        hoursUntil <= 48 -> 0.6f
//                        hoursUntil <= 72 -> 0.5f
//                        hoursUntil <= 168 -> 0.4f
//                        else -> 0.1f
//                    }
//                } catch (e: Exception) {
//                    0f
//                }
//            } ?: 0f
//        }
//
//        val scoredData = filteredData.map {
//            val cosine = cosineSimilarity(queryVec, it.embedding)
//            val overlap = phraseOverlapScore(normalizedQuery, it.text)
//            val temporal = temporalProximityScore(it.text)
//            val finalScore = if (isTimeSensitiveQuery(query)) {
//                0.6f * cosine + 0.2f * overlap + 0.2f * temporal
//            } else {
//                0.7f * cosine + 0.3f * overlap
//            }
//            it to finalScore
//        }
//
//        val keywordMatches = filteredData.filter {
//            normalizedQuery.split(" ").any { word ->
//                word.length > 3 && it.text.lowercase().contains(word)
//            }
//        }
//
//        val topScored = scoredData
//            .filterNot { keywordMatches.contains(it.first) }
//            .sortedByDescending { it.second }
//            .map { it.first }
//            .take((k - keywordMatches.size).coerceAtLeast(0))
//
//        val finalContext = (keywordMatches + topScored)
//            .distinctBy { it.text }
//            .sortedBy { extractEventTime(it.text) } // sort chronologically
//
//        finalContext.forEach {
//            Log.d("RAG_SIMILARITY", "Included in final context: '${it.text}'")
//        }
//
//        return finalContext
//    }
//
//
//    private fun extractEventTime(text: String): ZonedDateTime? {
//        val regex = Regex("""at\s+([0-9T:\-+Z/]+)""")
//        val match = regex.find(text)
//        val dateRange = match?.groupValues?.get(1)
//        val startDateStr = dateRange?.split("/")?.firstOrNull()
//        return try {
//            if (startDateStr != null) ZonedDateTime.parse(startDateStr) else null
//        } catch (e: Exception) {
//            null
//        }
//    }
//    fun normalizeText(text: String): String {
//        return text.lowercase()
//            .replace("-", " ")
//            .replace(Regex("\\s+"), " ")
//            .trim()
//    }
//
//}
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
    // ... User profile and config
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
//        val relevantContext = topChunks.joinToString("\n") {
//            it.text.trim().let { line -> if (!line.endsWith(".")) "$line." else line }
//        }
        val relevantContext = topChunks.joinToString("\n") {
            // Try to match: "Event Name at 2025-09-10T14:00:00-07:00/2025-09-10T16:00:00-07:00"
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

        // Match only on temporal cues in the query, not event types!
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

    // ----------------------
    // -- Supporting code below (unchanged from yours)
    // ----------------------
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