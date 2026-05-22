package com.example.data

import com.example.data.api.GeminiRetrofitClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SaaSUserRepository(private val dao: DbDao) {

    // --- In-Memory Authentication State ---
    private val _currentUserEmail = MutableStateFlow<String?>(null)
    val currentUserEmail: StateFlow<String?> = _currentUserEmail.asStateFlow()

    // --- Authentication Actions ---
    suspend fun signup(email: String, name: String, pass: String): Boolean {
        if (email.isBlank() || name.isBlank() || pass.length < 4) return false
        val existing = dao.getUserByEmail(email)
        if (existing != null) return false
        
        val userAuth = UserAuth(email, name, pass)
        dao.insertUser(userAuth)
        
        // Setup initial default profile
        val profile = UserProfile(
            email = email,
            name = name,
            plan = "Free"
        )
        dao.saveProfile(profile)
        
        _currentUserEmail.value = email
        return true
    }

    suspend fun login(email: String, pass: String): Boolean {
        val user = dao.getUserByEmail(email)
        if (user != null && user.passwordHash == pass) {
            _currentUserEmail.value = email
            return true
        }
        return false
    }

    fun logout() {
        _currentUserEmail.value = null
    }

    // --- Profile Management ---
    fun getProfileFlow(email: String): Flow<UserProfile?> = dao.getProfileFlow(email)

    suspend fun saveProfile(profile: UserProfile) {
        dao.saveProfile(profile)
    }

    // --- Symptom & AI Diagnostics Logs ---
    val allSymptomLogs: Flow<List<SymptomLog>> = dao.getAllSymptomLogs()

    suspend fun analyzeAndLogSymptoms(symptoms: String, aiTierInstructions: String = ""): String {
        val systemInstruction = """
            You are NutriMind AI, an expert clinical nutrition specialist and holistic wellness consultant.
            Provide detailed, professional deficiency analysis, recommended wellness foods, specific benefits, and healthy behavioral changes.
            Speak in Hindi, Bengali or English depending on how the user types, or default to friendly, readable English. Always structure your responses beautifully with headers, bullet points, and neat spacing.
            
            $aiTierInstructions
        """.trimIndent()

        val prompt = """
            The user is reporting following wellness symptoms:
            $symptoms
            
            Based on these, generate a comprehensive diagnostic wellness report detailing:
            1. Likely nutritional or environmental deficiencies/causes (e.g., Vitamin D, Iron, screentime etc.)
            2. Detailed explanations why they happen.
            3. Recommended therapeutic superfoods (at least 4-5 items).
            4. Benefits of each food (nutrients inside, and precisely how it helps the symptoms).
            5. Holistic lifestyle guidelines (e.g. hydration, screen eye care, sleep hacks).
        """.trimIndent()

        val response = GeminiRetrofitClient.generateContent(
            prompt = prompt,
            systemInstruction = systemInstruction,
            temperature = 0.5f
        )

        // Split response or save directly
        val log = SymptomLog(
            symptoms = symptoms,
            probableDeficiencies = "NutriMind Deficiencies Analysis",
            foodRecommendations = "Therapeutic Superfoods",
            generalAdvice = response
        )
        dao.insertSymptomLog(log)
        return response
    }

    // --- Food Tracker Logs ---
    val allFoodLogs: Flow<List<FoodLog>> = dao.getAllFoodLogs()

    suspend fun scanAndLogFood(foodName: String): String {
        val systemInstruction = "You are a food scanner AI. Analyze any meal/food name, calculate reliable macronutrients (calories, protein) and vitamins, rate healthiness (1-100), and state whether it's healthy."
        val prompt = """
            The user has uploaded/provided an image of: $foodName.
            Analyze this food/meal and return a structured text:
            - Calorie count (kcal)
            - Protein content (grams)
            - Essential vitamins / minerals present
            - Nutrition level (Healthy vs Unhealthy)
            - Explanation/Score of healthiness (1 to 100)
            
            Keep your answer short, concise, and informative.
        """.trimIndent()

        val analysis = GeminiRetrofitClient.generateContent(prompt, systemInstruction, 0.4f)

        // Try extracting stats
        var calories = 150
        var protein = 5f
        var healthScore = 75
        var isHealthy = true

        try {
            // Primitive extraction logic from response
            val lower = analysis.lowercase()
            val calMatch = Regex("(\\d+)\\s*(?:kcal|calories)").find(lower)
            if (calMatch != null) {
                calories = calMatch.groupValues[1].toInt()
            }
            val protMatch = Regex("(\\d+p?\\.?\\d*)\\s*(?:g|grams)\\s*protein").find(lower)
            if (protMatch != null) {
                protein = protMatch.groupValues[1].replace("g", "").toFloatOrNull() ?: 5f
            }
            if (lower.contains("unhealthy") || lower.contains("junk")) {
                isHealthy = false
                healthScore = 40
            }
        } catch (_: Exception) {}

        val log = FoodLog(
            name = foodName,
            calories = calories,
            protein = protein,
            vitamins = analysis,
            healthScore = healthScore,
            isHealthy = isHealthy
        )
        dao.insertFoodLog(log)
        return analysis
    }

    suspend fun logCustomFood(name: String, calories: Int, protein: Float, vitamins: String, score: Int, healthy: Boolean) {
        val log = FoodLog(
            name = name,
            calories = calories,
            protein = protein,
            vitamins = vitamins,
            healthScore = score,
            isHealthy = healthy
        )
        dao.insertFoodLog(log)
    }

    suspend fun deleteFoodLog(id: Long) = dao.deleteFoodLog(id)

    // --- Water Log Actions ---
    private fun getTodayDateString(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return formatter.format(Date())
    }

    suspend fun getTodayWater(): Int {
        val dateString = getTodayDateString()
        return dao.getWaterLog(dateString)?.amountMl ?: 0
    }

    val allWaterLogs: Flow<List<WaterLog>> = dao.getAllWaterLogs()

    suspend fun changeWater(deltaMl: Int) {
        val dateString = getTodayDateString()
        val current = dao.getWaterLog(dateString)?.amountMl ?: 0
        val newAmount = (current + deltaMl).coerceAtLeast(0)
        dao.saveWaterLog(WaterLog(dateString, newAmount))
    }

    // --- Habit & Lifestyle Tracking Actions ---
    val allHabitLogs: Flow<List<ActivityHabitLog>> = dao.getAllActivityHabitLogs()

    suspend fun getTodayHabit(): ActivityHabitLog {
        val dateString = getTodayDateString()
        return dao.getActivityHabitLog(dateString) ?: ActivityHabitLog(dateString)
    }

    suspend fun saveTodayHabit(sleep: Float, eyeBreaks: Int, steps: Int, mood: String) {
        val dateString = getTodayDateString()
        val log = ActivityHabitLog(
            dateString = dateString,
            sleepHours = sleep,
            eyeBreaks = eyeBreaks,
            steps = steps,
            mood = mood
        )
        dao.saveActivityHabitLog(log)
    }

    // --- Razorpay Payment Transactions ---
    val allTransactions: Flow<List<PaymentTransaction>> = dao.getAllTransactions()

    fun getTransactionsForUser(email: String): Flow<List<PaymentTransaction>> =
        dao.getTransactionsByUser(email)

    suspend fun logPaymentTransaction(tx: PaymentTransaction) {
        dao.insertTransaction(tx)
    }

    // --- Admin Payment Settings ---
    suspend fun getAdminPaymentSettings(): AdminPaymentSettings {
        return dao.getAdminSettings() ?: AdminPaymentSettings()
    }

    suspend fun saveAdminPaymentSettings(settings: AdminPaymentSettings) {
        dao.saveAdminSettings(settings)
    }
}
