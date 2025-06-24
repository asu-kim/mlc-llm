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
import java.time.format.DateTimeFormatter
import java.util.*

data class EmbeddedText(val embedding: FloatArray, val text: String)

class RagChatModel(private val context: Context) {
    private var userName: String = "Unknown"
    private var userRole: String = "User"
    private var userTimezone: String = "PST"
    fun setUserProfile(name: String, role: String, timezone: String) {
        userName = name
        userRole = role
        userTimezone = timezone
    }

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

//    fun runRAGQuery(query: String, engine: MLCEngine): String {
//        Log.d("RAG_DEBUG", "runRAGQuery called with query: $query")
//
//        if (embeddingList.isEmpty()) {
//            embeddingList = loadVecFromKGProvider(context)
//        }
//
//        Log.d("RAG_DEBUG", "Embedding list size after load: ${embeddingList.size}")
//
//        val topChunks = retrieveTopK(query, engine, embeddingList, k = 6)
//        Log.d("RAG_DEBUG", "Top chunks retrieved: ${topChunks.map { it.text }}")
//
////        val formattedEvents = topChunks.joinToString("\n") {
////            "- ${formatEvent(it.text)}"
////        }
//
//        val formattedEvents = topChunks.joinToString("\n") {
//            it.text.trim().let { line -> if (!line.endsWith(".")) "$line." else line }
//        }
//        val prompt = """
//            $formattedEvents
//
//            $query
//        """.trimIndent()
////        val prompt = """
////
////
////            Calendar Data:
////            ${topChunks.joinToString("\n") { "- ${it.text}" }}
////
////            prompt:
////            $query
////
////
////            """.trimIndent()
//
//        Log.d("RAG_DEBUG", "Final prompt to LLM:\n$prompt")
//
//        return engine.generateSync(prompt)
//    }
    fun runRAGQuery(query: String, engine: MLCEngine): String {
        Log.d("RAG_DEBUG", "runRAGQuery called with query: $query")

        if (embeddingList.isEmpty()) {
            embeddingList = loadVecFromKGProvider(context)
        }

        Log.d("RAG_DEBUG", "Embedding list size after load: ${embeddingList.size}")

//        val topChunks = retrieveTopK(query, engine, embeddingList, k = 5)
    val todayChunks = embeddingList.filter { isTodayEvent(it.text) }
    val topChunks = retrieveTopK(query, engine, todayChunks.ifEmpty { embeddingList }, k = 5)

    Log.d("RAG_DEBUG", "Top chunks retrieved: ${topChunks.map { it.text }}")

        val relevantContext = topChunks.joinToString("\n") {
            it.text.trim().let { line -> if (!line.endsWith(".")) "$line." else line }
        }

//        return relevantContext
    return """
        [User: $userName | Role: $userRole | TZ: $userTimezone]
        $relevantContext
    
        
    """.trimIndent()
    }

    private fun formatEvent(raw: String): String {
        return try {
            if (!raw.contains("starts at")) {
                // No timestamp in this entry, return as-is
                return raw
            }

            val inputFormatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy", Locale.US)
            val outputFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' hh:mm a z", Locale.US)

            val dateStr = raw.substringAfter("starts at ").trim()
            val parsed = ZonedDateTime.parse(dateStr, inputFormatter)

            val prefix = raw.substringBefore("starts at").trim()
            "$prefix starts at ${parsed.format(outputFormatter)}"
        } catch (e: Exception) {
            Log.e("RAG_FORMAT", "Failed to parse event: $raw", e)
            raw
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

    fun isTodayEvent(text: String): Boolean {
        val today = LocalDate.now()
        return Regex("""\b(?:starts at)\s+(.*)""").find(text)?.groupValues?.get(1)?.let { dateStr ->
            try {
                val parsed = ZonedDateTime.parse(dateStr, DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy", Locale.US))
                parsed.toLocalDate() == today
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    fun extractUserContextFromVec(data: List<EmbeddedText>): String {
        return data.mapNotNull { item ->
            if (item.text.startsWith("User ")) item.text else null
        }.joinToString(". ") + "."
    }
    private fun retrieveTopK(
        query: String,
        engine: MLCEngine,
        data: List<EmbeddedText>,
        k: Int
    ): List<EmbeddedText> {
        val normalizedQuery = normalizeText(query)
//        val queryVec = engine.getEmbedding(normalizedQuery) ?: return emptyList()
        val userContext = extractUserContextFromVec(embeddingList)

        val enrichedQuery = """
            User: $userName
            Role: $userRole
            Time Zone: $userTimezone
            Date: ${LocalDate.now()} (${ZonedDateTime.now().zone})
            
            Query: $query
        """.trimIndent()

        val queryVec = engine.getEmbedding(enrichedQuery) ?: return emptyList()
        fun phraseOverlapScore(query: String, text: String): Float {
            val queryTokens = query.split(" ").filter { it.length > 2 }.toSet()
            val textTokens = normalizeText(text).split(" ").toSet()
            val common = queryTokens.intersect(textTokens)
            return common.size.toFloat() / (queryTokens.size + 1e-5f)
        }

        val scoredData = data.map {
            val cosine = cosineSimilarity(queryVec, it.embedding)
            val overlap = phraseOverlapScore(normalizedQuery, it.text)
//            val editDistance = levenshtein(normalizedQuery, normalizeText(it.text))
//            val maxLen = maxOf(normalizedQuery.length, it.text.length)
//            val levenshteinScore = 1f - (editDistance.toFloat() / (maxLen + 1e-5f))
            val finalScore = 0.9f * cosine + 0.1f * overlap

//            val finalScore = 0.6f * cosine + 0.3f * overlap + 0.1f * levenshteinScore
//            Log.d("RAG_SCORE", "Final: $finalScore")
            it to finalScore
        }

        val forcedMatches = data.filter {
            normalizedQuery.split(" ").any { word ->
                word.length > 3 && it.text.lowercase().contains(word)
            }
        }

        val topScored = scoredData
            .filterNot { forcedMatches.contains(it.first) }
            .sortedByDescending { it.second }
            .map { it.first }
            .take((k - forcedMatches.size).coerceAtLeast(0))

        val finalContext = (forcedMatches + topScored).distinctBy { it.text }

        finalContext.forEach {
            //Log.d("RAG_SIMILARITY", "Included in final context: '${it.text}'")
        }

        return finalContext
    }

    fun normalizeText(text: String): String {
        return text.lowercase()
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

}
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
//import java.util.*
//import java.util.stream.Collectors
//import java.util.stream.IntStream
//
//data class EmbeddedText(val embedding: FloatArray, val text: String)
//
//class RagChatModel(private val context: Context) {
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
//        Log.d("RAG_DEBUG", "runRAGQuery called with query: $query")
//
//        if (embeddingList.isEmpty()) {
//            embeddingList = loadVecFromKGProvider(context)
//        }
//
//        Log.d("RAG_DEBUG", "Embedding list size after load: ${embeddingList.size}")
//
//        val topChunks = retrieveTopK(query, engine, embeddingList, k = 5)
//        Log.d("RAG_DEBUG", "Top chunks retrieved: ${topChunks.map { it.text }}")
//
//        val relevantContext = topChunks.joinToString("\n") {
//            it.text.trim().let { line -> if (!line.endsWith(".")) "$line." else line }
//        }
//
//        return relevantContext
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
//    class BM25(private val corpus: List<String>, private val k1: Float = 1.5f, private val b: Float = 0.75f) {
//        private val avgdl: Float
//        private val docFreqs: List<Map<String, Int>>
//        private val idf: Map<String, Float>
//        private val docLengths: List<Int>
//
//        init {
//            docFreqs = corpus.map { doc ->
//                doc.lowercase().split(Regex("\\W+")).groupingBy { it }.eachCount()
//            }
//            docLengths = docFreqs.map { it.values.sum() }
//            avgdl = docLengths.average().toFloat()
//
//            val df = mutableMapOf<String, Int>()
//            docFreqs.forEach { freqMap ->
//                freqMap.keys.forEach { term -> df[term] = df.getOrDefault(term, 0) + 1 }
//            }
//
//            val totalDocs = corpus.size
//            idf = df.mapValues { (_, freq) ->
//                kotlin.math.ln((1 + totalDocs).toFloat() / (1 + freq)) + 1
//            }
//        }
//
//        fun score(query: String, docIndex: Int): Float {
//            val doc = docFreqs[docIndex]
//            val dl = docLengths[docIndex]
//            val terms = query.lowercase().split(Regex("\\W+"))
//
//            return terms.sumOf { term ->
//                val f = doc[term] ?: 0
//                val idfScore = idf[term] ?: 0f
//                (idfScore * (f * (k1 + 1)) / (f + k1 * (1 - b + b * dl / avgdl))).toDouble()
//            }.toFloat()
//        }
//
//        fun topK(query: String, k: Int): List<Int> {
//            return corpus.indices
//                .map { it to score(query, it) }
//                .sortedByDescending { it.second }
//                .take(k)
//                .map { it.first }
//        }
//    }
//
//    private val k1 = 1.5f
//    private val b = 0.75f
//
//    private fun retrieveTopK(
//        query: String,
//        engine: MLCEngine,
//        data: List<EmbeddedText>,
//        k: Int
//    ): List<EmbeddedText> {
//        val corpus = data.map { normalizeText(it.text) }
//        val bm25 = BM25(corpus)  // Use your BM25 class
//        val topKIndices = bm25.topK(query, k)
//        return topKIndices.map { data[it] }
//    }
//
//    fun normalizeText(text: String): String {
//        return text.lowercase()
//            .replace("-", " ")
//            .replace(Regex("\\s+"), " ")
//            .trim()
//    }
//}