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
//import java.time.Duration
//import java.time.format.DateTimeFormatter
//
//data class EmbeddedText(val embedding: FloatArray, val text: String)
//
//class RagChatModel(private val context: Context) {
//    // ... User profile and config
//    private var userName: String = "Unknown"
//    private var userRole: String = "User"
//    private var userTimezone: String = "PST"
//    private var userLocation: String = "Tempe, AZ"
//
//    fun setUserProfile(name: String, role: String, timezone: String) {
//        userName = name
//        userRole = role
//        userTimezone = timezone
//    }
//    fun setUserLocation(location: String) {
//        userLocation = location
//        Log.d("RAG_USER_LOCATION", "User location set to: $userLocation")
//    }
//    fun getUserLocation(): String = userLocation
//
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
//    fun runRAGQuery(query: String, engine: MLCEngine): String {
//        if (embeddingList.isEmpty()) {
//            embeddingList = loadVecFromKGProvider(context)
//        }
//        val now = ZonedDateTime.now()
//        val filtered = getEventsForQuery(query, now, embeddingList)
//
//        val topChunks = retrieveTopK(query, engine, filtered.ifEmpty { embeddingList }, k = 5)
////        val relevantContext = topChunks.joinToString("\n") {
////            it.text.trim().let { line -> if (!line.endsWith(".")) "$line." else line }
////        }
//        val relevantContext = topChunks.joinToString("\n") {
//            // Try to match: "Event Name at 2025-09-10T14:00:00-07:00/2025-09-10T16:00:00-07:00"
//            val regex = Regex("""(.+?) at ([^/]+)/([^/]+)""")
//            val match = regex.matchEntire(it.text.trim())
//            if (match != null) {
//                val title = match.groupValues[1]
//                val startIso = match.groupValues[2]
//                val endIso = match.groupValues[3]
//                try {
//                    val start = ZonedDateTime.parse(startIso)
//                    val end = ZonedDateTime.parse(endIso)
//                    val dateFmt = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
//                    val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
//                    "$title: ${dateFmt.format(start)}, ${timeFmt.format(start)}–${timeFmt.format(end)}."
//                } catch (e: Exception) {
//                    it.text.trim()
//                }
//            } else {
//                it.text.trim()
//            }
//        }
//
//        val timeFormatted = now.toLocalTime().toString()
//        val timeOfDay = when (now.hour) {
//            in 5..11 -> "morning"
//            12 -> "noon"
//            in 13..16 -> "afternoon"
//            in 17..20 -> "evening"
//            else -> "night"
//        }
//
//        val personalizedPrompt = """
//            [User: $userName | Role: $userRole | Location: $userLocation | TZ: $userTimezone]
//            [Date: ${now.toLocalDate()} | Time: $timeFormatted (${now.zone}) | Part of Day: $timeOfDay]
//        """.trimIndent()
//        return """
//            $personalizedPrompt
//            $relevantContext
//        """.trimIndent()
//    }
//
//    private fun getEventsForQuery(query: String, now: ZonedDateTime, data: List<EmbeddedText>): List<EmbeddedText> {
//        val normalized = normalizeText(query)
//        val eventsWithTime = data.filter { extractEventTime(it.text) != null }
//
//        // Match only on temporal cues in the query, not event types!
//        return when {
//            "next week" in normalized -> {
//                // Monday to Sunday of *next* week
//                val nextWeekStart = now.plusWeeks(1).with(java.time.DayOfWeek.MONDAY).toLocalDate()
//                val nextWeekEnd = nextWeekStart.plusDays(6)
//                eventsWithTime.filter {
//                    val eventDate = extractEventTime(it.text)?.toLocalDate()
//                    eventDate != null && !eventDate.isBefore(nextWeekStart) && !eventDate.isAfter(nextWeekEnd)
//                }
//            }
//            "tomorrow" in normalized -> {
//                val tomorrow = now.plusDays(1).toLocalDate()
//                eventsWithTime.filter {
//                    extractEventTime(it.text)?.toLocalDate() == tomorrow
//                }
//            }
//            "today" in normalized -> {
//                val today = now.toLocalDate()
//                eventsWithTime.filter {
//                    extractEventTime(it.text)?.toLocalDate() == today
//                }
//            }
//            "next" in normalized || "upcoming" in normalized || "soon" in normalized -> {
//                // Only *future* events, sorted by soonest
//                eventsWithTime.filter {
//                    val eventStart = extractEventTime(it.text)
//                    eventStart != null && eventStart.isAfter(now)
//                }.sortedBy { extractEventTime(it.text) }
//            }
//            else -> data // No filtering! Just return all events.
//        }
//    }
//
//    // ----------------------
//    // -- Supporting code below (unchanged from yours)
//    // ----------------------
//    private fun loadVecFromKGProvider(context: Context): List<EmbeddedText> {
//        val uri = Uri.parse("content://com.example.knowledgegraph.kgprovider/knowledge_graph_vec")
//        val list = mutableListOf<EmbeddedText>()
//        try {
//            context.contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
//                lines.forEachIndexed { index, line ->
//                    val parts = line.split("\t")
//                    if (parts.size != 2) return@forEachIndexed
//                    val embeddingStr = parts[0]
//                    val text = parts[1]
//                    try {
//                        val embeddingList = embeddingStr.split(",")
//                            .mapNotNull { it.toFloatOrNull() }
//                        if (embeddingList.isNotEmpty()) {
//                            val norm = sqrt(embeddingList.fold(0f) { acc, x -> acc + x * x })
//                            val normalizedEmbedding = if (norm != 0f)
//                                embeddingList.map { it / norm }.toFloatArray()
//                            else
//                                embeddingList.toFloatArray()
//                            list.add(EmbeddedText(normalizedEmbedding, text))
//                        }
//                    } catch (e: Exception) {
//                        Log.e("RAG_PARSE", "Failed to parse embedding at line $index: ${e.message}")
//                    }
//                }
//            }
//        } catch (e: Exception) {
//            Log.e("RAG_FILE", "Failed to open .vec file: ${e.message}")
//        }
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
//        """.trimIndent()
//        val queryVec = engine.getEmbedding(enrichedPrompt) ?: return emptyList()
//
//        fun phraseOverlapScore(query: String, text: String): Float {
//            val queryTokens = query.split(" ").filter { it.length > 2 }.toSet()
//            val textTokens = normalizeText(text).split(" ").toSet()
//            val common = queryTokens.intersect(textTokens)
//            return common.size.toFloat() / (queryTokens.size + 1e-5f)
//        }
//
//        fun temporalProximityScore(text: String): Float {
//            val eventTime = extractEventTime(text)
//            return if (eventTime != null) {
//                val hoursUntil = Duration.between(now, eventTime).toHours().toFloat()
//                when {
//                    hoursUntil <= 0 -> 0f // Past events
//                    hoursUntil <= 2 -> 1.0f
//                    hoursUntil <= 12 -> 0.9f
//                    hoursUntil <= 24 -> 0.75f
//                    hoursUntil <= 48 -> 0.6f
//                    hoursUntil <= 72 -> 0.5f
//                    hoursUntil <= 168 -> 0.4f
//                    else -> 0.1f
//                }
//            } else 0f
//        }
//
//        val scoredData = data.map {
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
//        val keywordMatches = data.filter {
//            normalizedQuery.split(" ").any { word ->
//                word.length > 3 && it.text.lowercase().contains(word.lowercase())
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
//        return finalContext
//    }
//
//    private fun extractEventTime(text: String): ZonedDateTime? {
//        val regex = Regex("""at\s+([0-9T:\-+Z/]+)""")
//        val match = regex.find(text)
//        val dateRange = match?.groupValues?.get(1)
//        val startDateStr = dateRange?.split("/")?.firstOrNull()
//        return try {
//            if (startDateStr != null) {
//                ZonedDateTime.parse(startDateStr)
//            } else null
//        } catch (e: Exception) {
//            null
//        }
//    }
//
//    fun normalizeText(text: String): String {
//        return text.lowercase()
//            .replace("-", " ")
//            .replace(Regex("\\s+"), " ")
//            .trim()
//    }
//
//    private fun isTimeSensitiveQuery(query: String): Boolean {
//        val TIME_KEYWORDS = listOf(
//            "next", "upcoming", "today", "tomorrow", "this week", "weekend",
//            "soon", "schedule", "calendar", "what's", "any events", "anything"
//        )
//        val q = query.lowercase()
//        return TIME_KEYWORDS.any { it in q }
//    }
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
//}
//
package ai.mlc.mlcchat

import android.content.Context
import android.net.Uri
import android.util.Log
import ai.mlc.mlcllm.MLCEngine
import ai.mlc.mlcllm.getEmbedding
import java.io.File
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt

data class EmbeddedText(
    val embedding: FloatArray,
    val text: String,   // original line content stored alongside the vector
    val tag: String     // normalized tag derived from subject
)

class RagChatModel(private val context: Context) {
    // ----------------- user profile -----------------
    private var userName: String = "Unknown"
    private var userRole: String = "User"
    private var userTimezone: String = "PST"
    private var userLocation: String = "Tempe, AZ"

    fun setUserProfile(name: String, role: String, timezone: String) {
        userName = name
        userRole = role
        userTimezone = timezone
    }
    fun setUserLocation(location: String) { userLocation = location }
    fun getUserLocation(): String = userLocation

    // ----------------- cache -----------------
    private var embeddingList: List<EmbeddedText> = emptyList()
    private val prefs = context.getSharedPreferences("RAG_PREFS", Context.MODE_PRIVATE)

    fun loadEmbeddingsIfNeeded() {
        val file = File(context.getExternalFilesDir(null), "Knowledge_graph.vec")
        if (!file.exists()) return
        val currentTs = file.lastModified()
        val savedTs = prefs.getLong("last_vec_timestamp", 0L)
        if (currentTs != savedTs) {
            embeddingList = loadVecFromKGProvider(context)
            prefs.edit().putLong("last_vec_timestamp", currentTs).apply()
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

    // ----------------- main entry -----------------
    fun runRAGQuery(query: String, engine: MLCEngine): String {
        if (embeddingList.isEmpty()) {
            embeddingList = loadVecFromKGProvider(context)
        }

        val now = ZonedDateTime.now()

        // Optional temporal pre-filter
        val filtered = getEventsForQuery(query, now, embeddingList)

        // Measure retrieval time
        val t0 = System.currentTimeMillis()
        val topChunks = retrieveTopK(
            query = query,
            engine = engine,
            data = if (filtered.isEmpty()) embeddingList else filtered,
            k = 5
        )
        val t1 = System.currentTimeMillis()
        val retrievalMs = t1 - t0
        Log.d("RETRIEVAL_TIME", "RAG retrieval took ${retrievalMs}ms")

        val relevantContext = topChunks.joinToString("\n") {
            // Expect formats like: "<Subject> at <ISO>/<ISO>" or "<Subject> location <...>"
            val regex = Regex("""(.+?) at ([^/]+)/([^/]+)""")
            val m = regex.matchEntire(it.text.trim())
            if (m != null) {
                val title = m.groupValues[1]
                val startIso = m.groupValues[2]
                val endIso = m.groupValues[3]
                try {
                    val start = ZonedDateTime.parse(startIso)
                    val end = ZonedDateTime.parse(endIso)
                    val dateFmt = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
                    val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
                    "$title: ${dateFmt.format(start)}, ${timeFmt.format(start)}–${timeFmt.format(end)}."
                } catch (_: Exception) {
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

        val personalizedHeader = """
            [User: $userName | Role: $userRole | Location: $userLocation | TZ: $userTimezone]
            [Date: ${now.toLocalDate()} | Time: $timeFormatted (${now.zone}) | Part of Day: $timeOfDay]
        """.trimIndent()

        return """
            $personalizedHeader
            $relevantContext
        """.trimIndent()
    }

    // ----------------- temporal filter -----------------
    private fun getEventsForQuery(
        query: String,
        now: ZonedDateTime,
        data: List<EmbeddedText>
    ): List<EmbeddedText> {
        val normalized = normalizeText(query)
        val eventsWithTime = data.filter { extractEventTime(it.text) != null }

        return when {
            "next week" in normalized -> {
                val nextWeekStart = now.plusWeeks(1).with(java.time.DayOfWeek.MONDAY).toLocalDate()
                val nextWeekEnd = nextWeekStart.plusDays(6)
                eventsWithTime.filter {
                    val d = extractEventTime(it.text)?.toLocalDate()
                    d != null && !d.isBefore(nextWeekStart) && !d.isAfter(nextWeekEnd)
                }
            }
            "tomorrow" in normalized -> {
                val tomorrow = now.plusDays(1).toLocalDate()
                eventsWithTime.filter { extractEventTime(it.text)?.toLocalDate() == tomorrow }
            }
            "today" in normalized -> {
                val today = now.toLocalDate()
                eventsWithTime.filter { extractEventTime(it.text)?.toLocalDate() == today }
            }
            "next" in normalized || "upcoming" in normalized || "soon" in normalized -> {
                eventsWithTime.filter {
                    val start = extractEventTime(it.text)
                    start != null && start.isAfter(now)
                }.sortedBy { extractEventTime(it.text) }
            }
            else -> data
        }
    }

    // ----------------- vec loader (tag-aware) -----------------
    private fun loadVecFromKGProvider(context: Context): List<EmbeddedText> {
        val uri = Uri.parse("content://com.example.knowledgegraph.kgprovider/knowledge_graph_vec")
        val out = mutableListOf<EmbeddedText>()
        try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
                lines.forEachIndexed { idx, line ->
                    val parts = line.split("\t")
                    if (parts.size != 2) return@forEachIndexed
                    val embStr = parts[0]
                    val rawText = parts[1]

                    try {
                        val emb = embStr.split(",").mapNotNull { it.toFloatOrNull() }
                        if (emb.isEmpty()) return@forEachIndexed
                        // L2-normalize
                        val norm = sqrt(emb.fold(0f) { acc, x -> acc + x * x })
                        val vec = if (norm > 0f) emb.map { it / norm }.toFloatArray() else emb.toFloatArray()

                        // Derive subject, then tag = normalizedKey(subject)
                        val subject = extractSubject(rawText) ?: ""
                        val tag = normalizedKey(subject)
                        out.add(EmbeddedText(vec, rawText, tag))
                    } catch (e: Exception) {
                        Log.e("RAG_PARSE", "Parse fail @ line $idx: ${e.message}")
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e("RAG_FILE", "Permission error opening .vec: ${e.message}")
        } catch (e: Exception) {
            Log.e("RAG_FILE", "Failed to open .vec file: ${e.message}")
        }
        Log.d("RAG_VEC", "Loaded ${out.size} vectors (with tags for ${out.count { it.tag.isNotBlank() }})")
        return out
    }

    // Extract the subject from raw text like:
    //   "<Subject> at <ISO>/<ISO>"  OR  "<Subject> location <Somewhere>"
    private fun extractSubject(text: String): String? {
        val t = text.trim()
        val atIdx = t.lowercase().indexOf(" at ")
        val locIdx = t.lowercase().indexOf(" location ")
        val cut = listOf(atIdx, locIdx).filter { it >= 0 }.minOrNull() ?: -1
        return if (cut >= 0) t.substring(0, cut).trim().trim('"') else null
    }

    // Same normalization you used to create the tag column
    private fun normalizedKey(s: String): String =
        java.text.Normalizer.normalize(s.trim().lowercase(), java.text.Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .replace("[^a-z0-9]+".toRegex(), " ")
            .trim()

    // ----------------- retrieval -----------------
    private fun retrieveTopK(
        query: String,
        engine: MLCEngine,
        data: List<EmbeddedText>,
        k: Int
    ): List<EmbeddedText> {
        val normalizedQuery = normalizeText(query)
        val now = ZonedDateTime.now()
        val queryVec = engine.getEmbedding(enrichForEmbedding(query)) ?: return emptyList()

        // Optional explicit tag filter: support "tag:xxx" in the query
        val explicitTag = parseExplicitTag(normalizedQuery) // returns normalized tag or null
        val pool = if (explicitTag != null) {
            data.filter { it.tag.contains(explicitTag) || explicitTag.contains(it.tag) }
        } else data

        // Helper scores
        fun phraseOverlapScore(q: String, text: String): Float {
            val qs = q.split(" ").filter { it.length > 2 }.toSet()
            val ts = normalizeText(text).split(" ").toSet()
            val common = qs.intersect(ts).size
            return common.toFloat() / (qs.size + 1e-5f)
        }

        fun temporalProximityScore(text: String): Float {
            val eventTime = extractEventTime(text) ?: return 0f
            val hoursUntil = Duration.between(now, eventTime).toHours().toFloat()
            return when {
                hoursUntil <= 0 -> 0f
                hoursUntil <= 2 -> 1.0f
                hoursUntil <= 12 -> 0.9f
                hoursUntil <= 24 -> 0.75f
                hoursUntil <= 48 -> 0.6f
                hoursUntil <= 72 -> 0.5f
                hoursUntil <= 168 -> 0.4f
                else -> 0.1f
            }
        }

        // Tag similarity = token overlap between query words and item.tag words
        fun tagMatchScore(q: String, tag: String): Float {
            if (tag.isBlank()) return 0f
            val qTok = q.split(" ").filter { it.length > 2 }.toSet()
            val tTok = tag.split(" ").toSet()
            val common = qTok.intersect(tTok).size.toFloat()
            return common / (tTok.size + 1e-5f)
        }

        val timeSensitive = isTimeSensitiveQuery(query)

        val scored = pool.map { et ->
            val cosine = cosineSimilarity(queryVec, et.embedding)
            val overlap = phraseOverlapScore(normalizedQuery, et.text)
            val temporal = temporalProximityScore(et.text)
            val tagScore = tagMatchScore(normalizedQuery, et.tag)

            // Blend weights: give tag some weight; more if explicitTag was used
            val tagW = if (explicitTag != null) 0.35f else 0.2f
            val final = if (timeSensitive) {
                0.5f * cosine + 0.15f * overlap + 0.15f * temporal + tagW * tagScore
            } else {
                0.6f * cosine + 0.2f * overlap + tagW * tagScore
            }
            et to final
        }

        return scored.sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.text }
            .take(k)
    }

    private fun parseExplicitTag(q: String): String? {
        // Accept forms: "tag:xxx", "tag:\"art history\"" or "#arthistory" / "#art history"
        val tagColon = Regex("""tag:\s*("?)([^"]+)\1""").find(q)?.groupValues?.get(2)
        if (!tagColon.isNullOrBlank()) return normalizedKey(tagColon)

        val hash = Regex("""#([a-z0-9][a-z0-9 ]*)""").find(q)?.groupValues?.get(1)
        if (!hash.isNullOrBlank()) return normalizedKey(hash)

        return null
    }

    private fun enrichForEmbedding(query: String): String {
        val now = ZonedDateTime.now()
        val timeFormatted = now.toLocalTime().toString()
        val partOfDay = when (now.hour) {
            in 5..11 -> "morning"
            12 -> "noon"
            in 13..16 -> "afternoon"
            in 17..20 -> "evening"
            else -> "night"
        }
        return """
            User: $userName
            Role: $userRole
            Location: $userLocation
            Time Zone: $userTimezone
            Date: ${now.toLocalDate()}
            Current Time: $timeFormatted (${now.zone})
            Part of Day: $partOfDay

            Query: $query
        """.trimIndent()
    }

    // ----------------- utilities -----------------
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var na = 0f; var nb = 0f
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        return dot / (sqrt(na) * sqrt(nb) + 1e-8f)
    }

    private fun extractEventTime(text: String): ZonedDateTime? {
        val m = Regex("""\bat\s+([0-9T:\-+Z/]+)""").find(text) ?: return null
        val range = m.groupValues[1]
        val start = range.split("/").firstOrNull() ?: return null
        return try { ZonedDateTime.parse(start) } catch (_: Exception) { null }
    }

    fun normalizeText(text: String): String =
        text.lowercase()
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun isTimeSensitiveQuery(query: String): Boolean {
        val keys = listOf(
            "next", "upcoming", "today", "tomorrow", "this week", "weekend",
            "soon", "schedule", "calendar", "what's", "any events", "anything"
        )
        val q = query.lowercase()
        return keys.any { it in q }
    }

    fun getUserHeader(): String {
        val now = ZonedDateTime.now()
        val timeFormatted = now.toLocalTime().toString()
        val partOfDay = when (now.hour) {
            in 5..11 -> "morning"
            12 -> "noon"
            in 13..16 -> "afternoon"
            in 17..20 -> "evening"
            else -> "night"
        }
        return """
            [User: $userName | Role: $userRole | Location: $userLocation | TZ: $userTimezone]
            [Date: ${now.toLocalDate()} | Time: $timeFormatted (${now.zone}) | Part of Day: $partOfDay]
        """.trimIndent()
    }
}