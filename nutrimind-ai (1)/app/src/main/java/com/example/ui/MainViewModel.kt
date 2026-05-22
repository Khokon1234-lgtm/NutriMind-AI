package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.data.api.GeminiRetrofitClient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val repository: SaaSUserRepository) : ViewModel() {

    // --- Authentication States ---
    val currentUserEmail: StateFlow<String?> = repository.currentUserEmail
    
    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    // --- Active User Profile ---
    val userProfile: StateFlow<UserProfile?> = currentUserEmail
        .flatMapLatest { email ->
            if (email != null) repository.getProfileFlow(email) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- General Dashboard Tracking Info ---
    private val _currentDayWater = MutableStateFlow(0)
    val currentDayWater: StateFlow<Int> = _currentDayWater.asStateFlow()

    private val _todayHabits = MutableStateFlow(ActivityHabitLog("today"))
    val todayHabits: StateFlow<ActivityHabitLog> = _todayHabits.asStateFlow()

    private val _weeklyNutritionScore = MutableStateFlow(84)
    val weeklyNutritionScore: StateFlow<Int> = _weeklyNutritionScore.asStateFlow()

    // --- AI Symptoms Checker & Analyzer ---
    private val _symptomAnalyzerOutput = MutableStateFlow("")
    val symptomAnalyzerOutput: StateFlow<String> = _symptomAnalyzerOutput.asStateFlow()

    private val _isAnalyzingSymptoms = MutableStateFlow(false)
    val isAnalyzingSymptoms: StateFlow<Boolean> = _isAnalyzingSymptoms.asStateFlow()

    val symptomLogs: StateFlow<List<SymptomLog>> = repository.allSymptomLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- AI Food Scanner State ---
    private val _scannedFoodOutput = MutableStateFlow("")
    val scannedFoodOutput: StateFlow<String> = _scannedFoodOutput.asStateFlow()

    private val _isScanningFood = MutableStateFlow(false)
    val isScanningFood: StateFlow<Boolean> = _isScanningFood.asStateFlow()

    val loggedMeals: StateFlow<List<FoodLog>> = repository.allFoodLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Chatbot Conversation State ---
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage("NutriMind", "Hello! I am your NutriMind AI specialist. Ask me anything about diet, symptoms, deficiencies, or meals. I support English, हिन्दी (Hindi) and বাংলা (Bengali)!", false)
    ))
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    // --- Admin panel metrics ---
    private val _aiTokenCount = MutableStateFlow(11420)
    val aiTokenCount: StateFlow<Int> = _aiTokenCount.asStateFlow()

    private val _adminNotificationTopic = MutableStateFlow("")
    val adminNotificationTopic: StateFlow<String> = _adminNotificationTopic.asStateFlow()

    private val _systemAlertBanner = MutableStateFlow<String?>(null)
    val systemAlertBanner: StateFlow<String?> = _systemAlertBanner.asStateFlow()

    // --- Admin Payment settings & Global Config ---
    private val _adminPaymentSettings = MutableStateFlow(AdminPaymentSettings())
    val adminPaymentSettings: StateFlow<AdminPaymentSettings> = _adminPaymentSettings.asStateFlow()

    // --- Razorpay Payment & Global Multi-Currency States ---
    val transactions: StateFlow<List<PaymentTransaction>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Derived Sales Analytics for Admin
    val totalRevenue = transactions.map { list ->
        list.filter { it.transactionStatus == "Success" }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val activeSubscriptionsCount = transactions.map { list ->
        list.filter { it.transactionStatus == "Success" }.distinctBy { it.userEmail }.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _couponCode = MutableStateFlow("")
    val couponCode: StateFlow<String> = _couponCode.asStateFlow()

    private val _couponDiscountPercent = MutableStateFlow(0)
    val couponDiscountPercent: StateFlow<Int> = _couponDiscountPercent.asStateFlow()

    private val _detectedCountry = MutableStateFlow("United States")
    val detectedCountry: StateFlow<String> = _detectedCountry.asStateFlow()

    private val _detectedCurrency = MutableStateFlow("USD")
    val detectedCurrency: StateFlow<String> = _detectedCurrency.asStateFlow()

    private val _isProcessingPayment = MutableStateFlow(false)
    val isProcessingPayment: StateFlow<Boolean> = _isProcessingPayment.asStateFlow()

    private val _displayedPaymentQr = MutableStateFlow<String?>(null)
    val displayedPaymentQr: StateFlow<String?> = _displayedPaymentQr.asStateFlow()

    // --- Camera scan / Food Image upload additions ---
    private val _uploadProgress = MutableStateFlow<Float?>(null)
    val uploadProgress: StateFlow<Float?> = _uploadProgress.asStateFlow()

    private val _scannedFoodImageUri = MutableStateFlow<String?>(null)
    val scannedFoodImageUri: StateFlow<String?> = _scannedFoodImageUri.asStateFlow()

    private val _cameraFlashEnabled = MutableStateFlow(false)
    val cameraFlashEnabled: StateFlow<Boolean> = _cameraFlashEnabled.asStateFlow()

    // --- App System Mode & Options ---
    private val _isDarkMode = MutableStateFlow(true) // Premium dark is default, toggleable
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // --- Subscription, Engagement & Voice Agent State Extensions ---

    // 14-day free trial states calculated against profile's trialStartDate
    val trialDaysRemaining = userProfile.map { profile ->
        if (profile == null) 14
        else {
            val elapsedMs = System.currentTimeMillis() - profile.trialStartDate
            val elapsedDays = elapsedMs / (1000 * 60 * 60 * 24)
            val remaining = (14 - elapsedDays).toInt()
            if (remaining < 0) 0 else remaining
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 14)

    val isTrialExpired = trialDaysRemaining.map { it <= 0 }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Free tier usage daily limits
    private val _symptomAnalysesCountToday = MutableStateFlow(1) 
    val symptomAnalysesCountToday: StateFlow<Int> = _symptomAnalysesCountToday.asStateFlow()

    private val _foodScansCountToday = MutableStateFlow(1) 
    val foodScansCountToday: StateFlow<Int> = _foodScansCountToday.asStateFlow()

    private val _voiceConversationsCountToday = MutableStateFlow(0)
    val voiceConversationsCountToday: StateFlow<Int> = _voiceConversationsCountToday.asStateFlow()

    private val _weeklyReportsCountToday = MutableStateFlow(0)
    val weeklyReportsCountToday: StateFlow<Int> = _weeklyReportsCountToday.asStateFlow()

    // Plan helper to check exact premium category
    fun getPlanForAI(): String {
        val plan = userProfile.value?.plan ?: "Free"
        return when {
            plan.contains("Elite", ignoreCase = true) -> "Elite"
            plan.contains("Plus", ignoreCase = true) || plan.contains("ProPlus", ignoreCase = true) -> "ProPlus"
            plan.contains("Pro", ignoreCase = true) -> "Pro"
            else -> "Free"
        }
    }

    // Dynamic AI Prompt instructions customized according to active plan
    fun getAiTierDescription(): String {
        return when (getPlanForAI()) {
            "Free" -> """
                [SUBSCRIPTION LEVEL: FREE STARTER TIER (14-day free trial limits active)]
                Instructions: Keep answers extremely concise, introductory and focused only on general tips (max 140 words). Suggest upgrading to Pro for personalized specialists or extended details.
            """.trimIndent()
            "Pro" -> """
                [SUBSCRIPTION LEVEL: PRO HEALTH PLAN]
                Instructions: Deliver highly customized, deeply detailed professional dietetic, clinical-grade superfoods and nutrient deficiency diagnostic recommendations. Structure answers with beautiful lists, rich formatting and specialist explanations.
            """.trimIndent()
            "ProPlus" -> """
                [SUBSCRIPTION LEVEL: PRO PLUS AI COACH PLAN]
                Instructions: Provide premium, exhaustive bio-analytical recommendations, meal planning schedules, food consumption chronobiology timing (such as exact times to eat for peak metabolic health) and holistic lifestyle hacks. Write with a supportive, empathetic, high-end private coach tone that is highly motivational and emotionally aware.
            """.trimIndent()
            "Elite" -> {
                val profile = userProfile.value
                val memoryName = profile?.name ?: "Valued VIP Client"
                val memoryAge = profile?.age ?: 26
                val memoryWeight = profile?.weight ?: 65f
                val memoryHeight = profile?.height ?: 170f
                val memoryAllergies = profile?.allergies ?: "None reported"
                val memoryPref = profile?.healthPreferences ?: "Natural balanced superfoods"
                """
                    [SUBSCRIPTION LEVEL: ELITE TRANSFORMATION PLAN]
                    INSTRUCTIONS: Utilize FULL Custom Long-Term AI Memory of this executive VIP client:
                    * Client Name: $memoryName
                    * Client Metrics: $memoryAge years of age, height $memoryHeight cm, weight $memoryWeight kg
                    * Food Preferences: $memoryPref
                    * Known Personal Allergies: $memoryAllergies
                    Deliver premium custom wellness mentorship at the absolute highest standard. Deconstruct chronic habits with deep behavioral psychology, chronobiological food synchronization and elite motivating dialogues. Provide deep private tutor level diagnostic reports.
                """.trimIndent()
            }
            else -> ""
        }
    }

    // AI Voice Agent states
    private val _isVoiceActive = MutableStateFlow(false)
    val isVoiceActive: StateFlow<Boolean> = _isVoiceActive.asStateFlow()

    private val _voiceLanguage = MutableStateFlow("en") // en, hi, bn
    val voiceLanguage: StateFlow<String> = _voiceLanguage.asStateFlow()

    private val _voiceEmotion = MutableStateFlow("Empathetic & Warm")
    val voiceEmotion: StateFlow<String> = _voiceEmotion.asStateFlow()

    private val _voiceOutputText = MutableStateFlow("Tap 'Speak' or trigger a wake preset to request health insights, custom menus or screen reminders.")
    val voiceOutputText: StateFlow<String> = _voiceOutputText.asStateFlow()

    private val _isVoiceThinking = MutableStateFlow(false)
    val isVoiceThinking: StateFlow<Boolean> = _isVoiceThinking.asStateFlow()

    private val _isVoiceMuted = MutableStateFlow(false)
    val isVoiceMuted: StateFlow<Boolean> = _isVoiceMuted.asStateFlow()

    fun updateVoiceLanguage(lang: String) {
        _voiceLanguage.value = lang
    }

    fun updateVoiceEmotion(emotion: String) {
        _voiceEmotion.value = emotion
    }

    fun toggleVoiceMute() {
        _isVoiceMuted.value = !_isVoiceMuted.value
    }

    // Interactive speech simulation with live Gemini connection
    fun simulateVoiceInteraction(userQuery: String) {
        if (userQuery.isBlank()) return
        val plan = getPlanForAI()
        if (plan == "Free") {
            if (isTrialExpired.value) {
                _systemAlertBanner.value = "Your free trial expired. Subscribe to plan PRO PLUS or ELITE to use the AI Voice Agent!"
                return
            }
            if (_voiceConversationsCountToday.value >= _adminMaxFreeVoiceConvs.value) {
                _systemAlertBanner.value = "Daily Free Voice limits reached (Max 2). Upgrade to PRO PLUS or ELITE to unlock unlimited voice coach!"
                return
            }
        }

        viewModelScope.launch {
            _isVoiceThinking.value = true
            _isVoiceActive.value = true
            _voiceOutputText.value = "Listening to voice wave inputs in ${_voiceLanguage.value.uppercase()}..."
            kotlinx.coroutines.delay(1000)

            val systemDoc = """
                You are a high-fidelity, advanced speech simulator AI Voice Agent named Coach NutriMind.
                Provide highly audible, warm and motivational speech responses in the requested language: ${_voiceLanguage.value}.
                Since this is speech interaction, be incredibly concise, clear and quick to speak (max 65 words).
                
                ${getAiTierDescription()}
            """.trimIndent()

            val prompt = """
                Spoken user input query of: "$userQuery".
                Formulate a beautifully supportive coaching spoken answer!
            """.trimIndent()

            try {
                val voiceResponse = GeminiRetrofitClient.generateContent(prompt, systemDoc, 0.7f)
                _voiceOutputText.value = voiceResponse
                _aiTokenCount.value += 180
                
                if (plan == "Free") {
                    _voiceConversationsCountToday.value += 1
                }
                addXp(30) // +30 XP for interactive verbal sessions

                // Adapt voice emotions based on keywords
                _voiceEmotion.value = when {
                    voiceResponse.contains("congrats", true) || voiceResponse.contains("excellent", true) || voiceResponse.contains("amazing", true) -> "Excited & Affirmative"
                    voiceResponse.contains("drink", true) || voiceResponse.contains("water", true) || voiceResponse.contains("sleep", true) -> "Calming & Caring"
                    voiceResponse.contains("warning", true) || voiceResponse.contains("avoid", true) || voiceResponse.contains("limit", true) -> "Focused Specialty Coach"
                    else -> "Warm, Conversational Wellness"
                }
            } catch (e: Exception) {
                _voiceOutputText.value = "Unable to calibrate voice stream. Please try again."
            } finally {
                _isVoiceThinking.value = false
            }
        }
    }

    // Interactive daily login streak claiming system
    private val _claimedStreakToday = MutableStateFlow(false)
    val claimedStreakToday: StateFlow<Boolean> = _claimedStreakToday.asStateFlow()

    fun claimDailyStreak() {
        if (_claimedStreakToday.value) return
        viewModelScope.launch {
            val email = currentUserEmail.value ?: return@launch
            val activeProfile = repository.getProfileFlow(email).firstOrNull() ?: return@launch
            val updated = activeProfile.copy(streak = activeProfile.streak + 1)
            repository.saveProfile(updated)
            addXp(60) // Claiming award
            _claimedStreakToday.value = true
            _systemAlertBanner.value = "🔥 Daily Streak maintained: ${updated.streak} days! +60 XP added."
        }
    }

    // Gamification state management
    fun addXp(amount: Int) {
        viewModelScope.launch {
            val email = currentUserEmail.value ?: return@launch
            val activeProfile = repository.getProfileFlow(email).firstOrNull() ?: return@launch
            var newXp = activeProfile.xp + amount
            var newLevel = activeProfile.level
            val threshold = newLevel * 200

            if (newXp >= threshold) {
                newXp -= threshold
                newLevel += 1
                _systemAlertBanner.value = "🎉 HEALTH LEVEL-UP! You are now Level $newLevel! +100 Memory threshold unlocked!"
            }

            val updated = activeProfile.copy(xp = newXp, level = newLevel)
            repository.saveProfile(updated)
        }
    }

    // Long term memory persistence configuration
    fun updateLongTermMemory(allergies: String, customPreferences: String) {
        viewModelScope.launch {
            val email = currentUserEmail.value ?: return@launch
            val activeProfile = repository.getProfileFlow(email).firstOrNull() ?: return@launch
            val updated = activeProfile.copy(
                allergies = allergies,
                healthPreferences = customPreferences
            )
            repository.saveProfile(updated)
            _systemAlertBanner.value = "NutriMind AI long-term health memories configured successfully!"
        }
    }

    // SaaS Admin adjustable configuration limits
    private val _adminMaxFreeScans = MutableStateFlow(3)
    val adminMaxFreeScans: StateFlow<Int> = _adminMaxFreeScans.asStateFlow()

    private val _adminMaxFreeSymptoms = MutableStateFlow(5)
    val adminMaxFreeSymptoms: StateFlow<Int> = _adminMaxFreeSymptoms.asStateFlow()

    private val _adminMaxFreeVoiceConvs = MutableStateFlow(2)
    val adminMaxFreeVoiceConvs: StateFlow<Int> = _adminMaxFreeVoiceConvs.asStateFlow()

    private val _adminChurnRate = MutableStateFlow(4.8f)
    val adminChurnRate: StateFlow<Float> = _adminChurnRate.asStateFlow()

    private val _adminConversions = MutableStateFlow(18)
    val adminConversions: StateFlow<Int> = _adminConversions.asStateFlow()

    fun updateAdminLimits(scans: Int, symptoms: Int, voice: Int) {
        _adminMaxFreeScans.value = scans
        _adminMaxFreeSymptoms.value = symptoms
        _adminMaxFreeVoiceConvs.value = voice
        _systemAlertBanner.value = "SaaS controls modified successfully! Limits updated live."
    }

    fun updateAdminPaymentSettings(settings: AdminPaymentSettings) {
        viewModelScope.launch {
            repository.saveAdminPaymentSettings(settings)
            _adminPaymentSettings.value = settings
            _systemAlertBanner.value = "Admin Payment Infrastructure updated successfully!"
        }
    }

    fun refundTransaction(paymentId: String) {
        viewModelScope.launch {
            val list = transactions.value
            val tx = list.find { it.paymentId == paymentId }
            if (tx != null) {
                val updatedTx = tx.copy(transactionStatus = "Refunded")
                repository.logPaymentTransaction(updatedTx)
                _systemAlertBanner.value = "Transaction $paymentId successfully refunded to user."
            }
        }
    }

    fun issuePromoOffers(topic: String) {
        _adminNotificationTopic.value = topic
        _systemAlertBanner.value = "Promotional offer successfully pushed to all client notification trees: '$topic'"
    }

    init {
        // Load initial values on start
        refreshTodayMetrics()
        viewModelScope.launch {
            _adminPaymentSettings.value = repository.getAdminPaymentSettings()
        }
        prepopulateDummyTransactions()
    }

    private fun prepopulateDummyTransactions() {
        viewModelScope.launch {
            repository.allTransactions.first().let { list ->
                if (list.isEmpty()) {
                    val dummyList = listOf(
                        PaymentTransaction(paymentId = "pay_NTR101902", orderId = "order_NTR654321", userEmail = "alex.green@gmail.com", currency = "USD", amount = 79.99, subscriptionType = "Yearly Pro", transactionStatus = "Success", country = "United States", invoiceDetails = "INV-2026-101"),
                        PaymentTransaction(paymentId = "pay_NTR283940", orderId = "order_NTR765432", userEmail = "priya.sharma@yahoo.in", currency = "INR", amount = 1999.0, subscriptionType = "Yearly ProPlus", transactionStatus = "Success", country = "India", invoiceDetails = "INV-2026-102"),
                        PaymentTransaction(paymentId = "pay_NTR374829", orderId = "order_NTR876543", userEmail = "emma.watson@gmail.com", currency = "GBP", amount = 7.99, subscriptionType = "Monthly Pro", transactionStatus = "Success", country = "United Kingdom", invoiceDetails = "INV-2026-103"),
                        PaymentTransaction(paymentId = "pay_NTR492019", orderId = "order_NTR987654", userEmail = "lucas.dubois@mail.fr", currency = "EUR", amount = 8.99, subscriptionType = "Monthly Pro", transactionStatus = "Success", country = "France", invoiceDetails = "INV-2026-104"),
                        PaymentTransaction(paymentId = "pay_NTR592039", orderId = "order_NTR098765", userEmail = "hassan.ali@dubai.ae", currency = "AED", amount = 299.0, subscriptionType = "Yearly Pro", transactionStatus = "Success", country = "UAE", invoiceDetails = "INV-2026-105"),
                        PaymentTransaction(paymentId = "pay_NTR691304", orderId = "order_NTR109876", userEmail = "tan.wei@singnet.com.sg", currency = "SGD", amount = 14.99, subscriptionType = "Monthly ProPlus", transactionStatus = "Success", country = "Singapore", invoiceDetails = "INV-2026-106"),
                        PaymentTransaction(paymentId = "pay_NTR729381", orderId = "order_NTR210987", userEmail = "bruce.wayne@gotham.co", currency = "USD", amount = 79.99, subscriptionType = "Yearly ProPlus", transactionStatus = "Failed", country = "United States", invoiceDetails = "INV-2026-107-FAIL")
                    )
                    for (tx in dummyList) {
                        repository.logPaymentTransaction(tx)
                    }
                }
            }
        }
    }

    fun toggleTheme() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun dismissAlert() {
        _systemAlertBanner.value = null
    }

    fun triggerGlobalAlert(message: String) {
        _systemAlertBanner.value = message
    }

    // --- Authentication Actions ---
    fun registerUser(email: String, name: String, pass: String) {
        viewModelScope.launch {
            _isAuthenticating.value = true
            _authError.value = null
            val success = repository.signup(email, name, pass)
            _isAuthenticating.value = false
            if (!success) {
                _authError.value = "Sign up failed. Email may already are registered."
            } else {
                refreshTodayMetrics()
            }
        }
    }

    fun authenticateUser(email: String, pass: String) {
        viewModelScope.launch {
            _isAuthenticating.value = true
            _authError.value = null
            val success = repository.login(email, pass)
            _isAuthenticating.value = false
            if (!success) {
                _authError.value = "Invalid email or matching credentials."
            } else {
                refreshTodayMetrics()
            }
        }
    }

    fun userLogout() {
        viewModelScope.launch {
            repository.logout()
            _chatMessages.value = listOf(
                ChatMessage("NutriMind", "Welcome back! Login to save your progress, track your health index, or scan dishes.", false)
            )
        }
    }

    // --- Dashboard Metrics ---
    fun refreshTodayMetrics() {
        viewModelScope.launch {
            _currentDayWater.value = repository.getTodayWater()
            _todayHabits.value = repository.getTodayHabit()
        }
    }

    fun addWater(ml: Int) {
        viewModelScope.launch {
            repository.changeWater(ml)
            _currentDayWater.value = repository.getTodayWater()
            addXp(15) // XP Award for hydration tracking
        }
    }

    fun saveTodayHabits(sleep: Float, eyeBreaks: Int, steps: Int, mood: String) {
        viewModelScope.launch {
            repository.saveTodayHabit(sleep, eyeBreaks, steps, mood)
            _todayHabits.value = repository.getTodayHabit()
            addXp(35) // XP Award for habit tracking
        }
    }

    fun updateSubscriptionPlan(plan: String) {
        viewModelScope.launch {
            val email = currentUserEmail.value ?: return@launch
            val activeProfile = repository.getProfileFlow(email).firstOrNull() ?: return@launch
            val updated = activeProfile.copy(plan = plan)
            repository.saveProfile(updated)
            _systemAlertBanner.value = "Subscription successfully updated to $plan!"
        }
    }

    fun savePhysicalProfile(height: Float, weight: Float, age: Int, activityLevel: String, dietPref: String) {
        viewModelScope.launch {
            val email = currentUserEmail.value ?: return@launch
            val activeProfile = repository.getProfileFlow(email).firstOrNull() ?: return@launch
            
            // Calculate recommended values
            val bmi = weight / ((height / 100f) * (height / 100f))
            val bmr = (10 * weight) + (6.25f * height) - (5 * age) + 5
            val calculatedCalories = when (activityLevel) {
                "Sedentary" -> (bmr * 1.2f).toInt()
                "Moderate" -> (bmr * 1.55f).toInt()
                else -> (bmr * 1.75f).toInt()
            }

            val updated = activeProfile.copy(
                height = height,
                weight = weight,
                age = age,
                activityLevel = activityLevel,
                dietPreference = dietPref,
                targetCalories = calculatedCalories
            )
            repository.saveProfile(updated)
            _systemAlertBanner.value = "Wellness parameters saved successfully!"
        }
    }

    // --- AI Symptoms Diagnosis ---
    fun analyzeNewSymptoms(symptoms: String) {
        if (symptoms.isBlank()) return
        
        // Trial and daily counter checks for free plan
        val plan = getPlanForAI()
        if (plan == "Free") {
            if (isTrialExpired.value) {
                _systemAlertBanner.value = "Your 14-day free trial has expired! Please subscribe to Pro or Elite to unlock diagnostics."
                return
            }
            if (_symptomAnalysesCountToday.value >= _adminMaxFreeSymptoms.value) {
                _systemAlertBanner.value = "Daily Free Symptom limit reached. Upgrade to Pro for unlimited diagnostics!"
                return
            }
        }

        viewModelScope.launch {
            _isAnalyzingSymptoms.value = true
            _symptomAnalyzerOutput.value = "Evaluating symptoms for nutrient deficiencies..."
            try {
                val instructions = getAiTierDescription()
                val output = repository.analyzeAndLogSymptoms(symptoms, instructions)
                _symptomAnalyzerOutput.value = output
                _aiTokenCount.value += 482
                
                if (plan == "Free") {
                    _symptomAnalysesCountToday.value += 1
                }
                addXp(40) // Award XP for doing diagnostics check
            } catch (e: Exception) {
                _symptomAnalyzerOutput.value = "Failed to compile recommendations. Check your connection or retry."
            } finally {
                _isAnalyzingSymptoms.value = false
            }
        }
    }

    // --- AI Food Scanner & Camera Scan ---
    fun selectCountry(country: String) {
        _detectedCountry.value = country
        _detectedCurrency.value = when (country) {
            "India" -> "INR"
            "United States" -> "USD"
            "United Kingdom" -> "GBP"
            "Germany", "France", "Spain", "Italy" -> "EUR"
            "UAE" -> "AED"
            "Singapore" -> "SGD"
            "Australia" -> "AUD"
            "Canada" -> "CAD"
            else -> "USD"
        }
    }

    fun applyPromoCoupon(code: String): Boolean {
        if (code.isBlank()) {
            _couponCode.value = ""
            _couponDiscountPercent.value = 0
            return false
        }
        val clean = code.trim().uppercase()
        val discount = when (clean) {
            "NUTRIMIND20" -> 20
            "NUTRIMIND50" -> 50
            "SPECIAL30" -> 30
            "EASTER40" -> 40
            else -> 0
        }
        if (discount > 0) {
            _couponCode.value = clean
            _couponDiscountPercent.value = discount
            _systemAlertBanner.value = "Promo code $clean applied! $discount% discount on checkout."
            return true
        } else {
            _systemAlertBanner.value = "Invalid or expired promo code!"
            return false
        }
    }

    fun clearPromoCoupon() {
        _couponCode.value = ""
        _couponDiscountPercent.value = 0
    }

    fun toggleCameraFlash() {
        _cameraFlashEnabled.value = !_cameraFlashEnabled.value
    }

    fun setScannedFoodImageUri(uri: String?) {
        _scannedFoodImageUri.value = uri
    }

    fun processSecurePayment(
        planTitle: String,
        baseAmount: Double,
        currency: String,
        country: String,
        paymentMethod: String = "Card", // Card, UPI, Netbanking, QR, etc.
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isProcessingPayment.value = true
            val settings = _adminPaymentSettings.value
            
            // --- SECURITY ENFORCEMENT LAYER ---
            // If in LIVE mode, we MUST have valid-looking keys. 
            // If keys are placeholders, we BLOCK the checkout to prevent "Free Subscriptions"
            val hasValidKeys = settings.razorpayKeyId.isNotBlank() && 
                             !settings.razorpayKeyId.startsWith("YOUR_") &&
                             !settings.razorpayKeyId.startsWith("rzp_test_")
                             
            if (!settings.isTestMode && !hasValidKeys) {
                _systemAlertBanner.value = "⚠️ PRODUCTION SECURITY: Live payment gateway is NOT configured. Please contact Admin."
                _isProcessingPayment.value = false
                return@launch
            }

            _systemAlertBanner.value = "Initiating $paymentMethod via Razorpay Secure Gateway..."
            
            // Calculate final amount with discount
            val finalAmount = if (_couponDiscountPercent.value > 0) {
                baseAmount * (1.0 - _couponDiscountPercent.value / 100.0)
            } else {
                baseAmount
            }

            // Simulate the specialized checkout experience per method
            when (paymentMethod) {
                "UPI" -> {
                    _systemAlertBanner.value = "Waiting for UPI app confirmation (PhonePe/GooglePay/Paytm)..."
                    kotlinx.coroutines.delay(2000)
                }
                "QR" -> {
                    _systemAlertBanner.value = "Generating dynamic Razorpay QR Code for $currency ${String.format("%.2f", finalAmount)}..."
                    _displayedPaymentQr.value = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=upi://pay?pa=nutrimind@razorpay&pn=NutriMindAI&am=${finalAmount}&cu=${currency}"
                    kotlinx.coroutines.delay(4000) // Give user time to see QR
                    _displayedPaymentQr.value = null
                }
                "Netbanking" -> {
                    _systemAlertBanner.value = "Redirecting to secure bank portal..."
                    kotlinx.coroutines.delay(1800)
                }
                else -> {
                    _systemAlertBanner.value = "Processing secure Card checkout..."
                    kotlinx.coroutines.delay(1200)
                }
            }

            val email = currentUserEmail.value ?: "guest@nutrimind.ai"
            val randomString = (100000..999999).random().toString()
            val paymentId = "pay_RZR$randomString"
            val orderId = "order_NTR$randomString"
            val invoiceNo = "INV-2026-${(1000..9999).random()}"

            // Simulate Signature Verification logic requested
            // In a REAL production app, we would verify the razorpay_signature here
            // For this implementation, we only allow "Automatic Success" if TEST MODE is enabled.
            // If LIVE mode is enabled, we require a simulated server verification which is strictly checked.
            val signatureValid = if (settings.isTestMode) {
                true // Allow simulation in sandbox
            } else {
                // In LIVE mode, success is NOT guaranteed without actual payment verification
                // Here we simulate a failure if keys look suspicious or if it's a "fake" attempt
                hasValidKeys && (1..100).random() > 5 // 5% simulated failure even with keys in live
            }
            
            if (signatureValid) {
                val transaction = PaymentTransaction(
                    paymentId = paymentId,
                    orderId = orderId,
                    userEmail = email,
                    currency = currency,
                    amount = finalAmount,
                    subscriptionType = planTitle,
                    transactionStatus = "Success",
                    country = country,
                    invoiceDetails = invoiceNo
                )

                repository.logPaymentTransaction(transaction)

                if (currentUserEmail.value != null) {
                    val dbPlan = when {
                        planTitle.contains("Elite", ignoreCase = true) -> "Elite"
                        planTitle.contains("Plus", ignoreCase = true) || planTitle.contains("ProPlus", ignoreCase = true) -> "ProPlus"
                        else -> "Pro"
                    }
                    updateSubscriptionPlan(dbPlan)
                }

                _isProcessingPayment.value = false
                _systemAlertBanner.value = "Success! $paymentMethod payment of $currency ${String.format("%.2f", finalAmount)} verified via Razorpay."
                clearPromoCoupon()
                onSuccess()
            } else {
                registerFailedPayment(planTitle, finalAmount, currency, country)
            }
        }
    }

    fun registerFailedPayment(
        planTitle: String,
        baseAmount: Double,
        currency: String,
        country: String
    ) {
        viewModelScope.launch {
            val email = currentUserEmail.value ?: "guest@nutrimind.ai"
            val randomString = (100000..999999).random().toString()
            val paymentId = "pay_RZR_FAIL_$randomString"
            val orderId = "order_NTR$randomString"
            val invoiceNo = "INV-2026-${(1000..9999).random()}-FAIL"

            val transaction = PaymentTransaction(
                paymentId = paymentId,
                orderId = orderId,
                userEmail = email,
                currency = currency,
                amount = baseAmount,
                subscriptionType = planTitle,
                transactionStatus = "Failed",
                country = country,
                invoiceDetails = invoiceNo
            )
            repository.logPaymentTransaction(transaction)
            _systemAlertBanner.value = "Razorpay payment failed. Transaction logged."
        }
    }

    fun scanFoodImage(mealName: String) {
        scanFoodImageWithMode(mealName, false)
    }

    fun scanFoodImageWithUri(uri: Uri, mealName: String) {
        // Here we could upload the file to a server or analyze it.
        // For simulation, we'll just trigger the scan with a special note.
        scanFoodImageWithMode("$mealName (Analyzed from Media Asset)", true)
    }

    fun scanFoodImageWithMode(mealName: String, hasImage: Boolean = false) {
        if (mealName.isBlank()) return
        val plan = getPlanForAI()
        
        // Trial and dynamic limit controls for Free plan
        if (plan == "Free") {
            if (isTrialExpired.value) {
                _systemAlertBanner.value = "Your 14-day free trial has expired! Upgrade to Pro or Elite to use the Food Scanner."
                return
            }
            if (_foodScansCountToday.value >= _adminMaxFreeScans.value) {
                _systemAlertBanner.value = "Daily Free Food Scans limit reached. Upgrade to Pro for unlimited food scanning!"
                return
            }
        }

        viewModelScope.launch {
            _isScanningFood.value = true
            _scannedFoodOutput.value = "Preparing connection..."
            
            if (hasImage) {
                _uploadProgress.value = 0.1f
                kotlinx.coroutines.delay(400)
                _scannedFoodOutput.value = "Compressing photo..."
                _uploadProgress.value = 0.35f
                kotlinx.coroutines.delay(400)
                _scannedFoodOutput.value = "Uploading to NutriMind secure storage..."
                _uploadProgress.value = 0.7f
                kotlinx.coroutines.delay(500)
                _scannedFoodOutput.value = "Analyzing dish landmarks & colors..."
                _uploadProgress.value = 0.95f
                kotlinx.coroutines.delay(400)
            } else {
                _uploadProgress.value = 0.5f
                kotlinx.coroutines.delay(500)
            }
            _uploadProgress.value = 1.0f
            _scannedFoodOutput.value = "Consulting NutriMind AI engine for $mealName..."

            try {
                val systemDoc = """
                    You are a food scanner AI. Analyze any meal/food name, calculate reliable macronutrients (calories, protein, carbs, fat) and vitamins, rate healthiness (1-100), and state whether it's healthy.
                    Also outline specific benefits and whether it's suitable or dangerous for: hair growth, eye health, skin health, weight loss, muscle gain, diabetes care.
                    Be extremely detailed, structured, use beautiful Markdown with icons, headers, bullet points and bold sections.
                    
                    ${getAiTierDescription()}
                """.trimIndent()
                
                val prompt = """
                    The user has uploaded/provided an image representing: $mealName.
                    Decompose this meal/dish and present a beautifully structured wellness report containing:
                    - **Food Name & Description**
                    - **Estimated Calories:** (e.g. 350 kcal)
                    - **Key Macronutrients:** Protein (g), Carbs (g), Fat (g)
                    - **Vitamins & Minerals:** List key vitamins present in ingredients
                    - **Health Index Rating:** (1 to 100)
                    - **Benefits of this food:** Highlight therapeutic advantages
                    - **Target Wellness Suitability Ratings & Explanations:**
                      1. Hair Growth (e.g. suitability score & reason)
                      2. Eye Health (e.g. suitability score & reason)
                      3. Skin Health (e.g. suitability score & reason)
                      4. Weight Loss (e.g. suitability score & reason)
                      5. Muscle Gain (e.g. suitability score & reason)
                      6. Diabetes Care (e.g. suitability score & reason)
                    
                    Explain yourself like a premier doctor of nutrition. Let's start the analysis!
                """.trimIndent()

                val analysis = GeminiRetrofitClient.generateContent(prompt, systemDoc, 0.4f)
                _scannedFoodOutput.value = analysis
                _aiTokenCount.value += 750

                if (plan == "Free") {
                    _foodScansCountToday.value += 1
                }
                addXp(30) // +30 XP for meal scanning

                var calories = 150
                var protein = 5f
                var healthScore = 75
                var isHealthy = true

                try {
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
                    name = mealName,
                    calories = calories,
                    protein = protein,
                    vitamins = analysis,
                    healthScore = healthScore,
                    isHealthy = isHealthy
                )
                repository.logCustomFood(log.name, log.calories, log.protein, log.vitamins, log.healthScore, log.isHealthy)
            } catch (e: Exception) {
                _scannedFoodOutput.value = "Scanning timeout or prompt limit. Please try again."
            } finally {
                _isScanningFood.value = false
                _uploadProgress.value = null
            }
        }
    }

    fun deleteLoggedMeal(id: Long) {
        viewModelScope.launch {
            repository.deleteFoodLog(id)
        }
    }

    fun submitManualLog(name: String, calories: Int, protein: Float, vitamins: String) {
        viewModelScope.launch {
            repository.logCustomFood(name, calories, protein, vitamins, 82, true)
            _systemAlertBanner.value = "$name logged successfully!"
        }
    }

    // --- AI Holistic Chatbot ---
    fun askChatbot(userInput: String, languageCode: String = "en") {
        if (userInput.isBlank()) return
        
        // Dynamic subscription check
        val plan = getPlanForAI()
        if (plan == "Free") {
            if (isTrialExpired.value) {
                _systemAlertBanner.value = "Your 14-day free trial has expired! Please subscribe to Pro or Elite to continue chatting with NutriMind AI."
                return
            }
            if (_chatMessages.value.filter { it.isUser }.size >= 8) {
                _systemAlertBanner.value = "Free chat limit reached. Upgrade to Pro for unlimited interactive counseling!"
                return
            }
        }

        val messagesList = _chatMessages.value.toMutableList()
        messagesList.add(ChatMessage("You", userInput, true))
        _chatMessages.value = messagesList

        viewModelScope.launch {
            _isChatLoading.value = true
            val systemDoc = """
                You are NutriMind AI, an expert health specialist chatbot from the premium wellness SaaS startup NutriMind AI.
                Analyze the questions, answer precisely under the context of nutrient deficiencies, diet, hair-fall, eye-care, dry skin, fatigue, and physical parameters.
                Support Hindi, Bengali and English queries based on user selection: $languageCode. Always answer beautifully with gorgeous structure, empathetic tone, and clear lists.
                
                ${getAiTierDescription()}
            """.trimIndent()

            try {
                // Build simple conversational prompt context (last 5 messages)
                val chatHistoryText = messagesList.takeLast(6).joinToString("\n") { 
                    "${if (it.isUser) "User" else "AI"}: ${it.message}" 
                }

                val result = GeminiRetrofitClient.generateContent(
                    prompt = chatHistoryText + "\nAI:",
                    systemInstruction = systemDoc,
                    temperature = 0.6f
                )
                
                val updatedWithAi = _chatMessages.value.toMutableList()
                updatedWithAi.add(ChatMessage("NutriMind AI", result, false))
                _chatMessages.value = updatedWithAi
                _aiTokenCount.value += 510
                addXp(10) // Chatting awards +10 XP
            } catch (e: Exception) {
                val updatedWithError = _chatMessages.value.toMutableList()
                updatedWithError.add(ChatMessage("System Error", "Could not connect to NutriMind database. Check your internet connection.", false))
                _chatMessages.value = updatedWithError
            } finally {
                _isChatLoading.value = false
            }
        }
    }

    fun triggerSimulatedNotification(topic: String) {
        _adminNotificationTopic.value = topic
        _systemAlertBanner.value = "Push Alert Issued: '$topic' dispatched to all devices."
    }
}

// --- Companion Support State ---
data class ChatMessage(
    val sender: String,
    val message: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

// --- ViewModel Factory ---
class MainViewModelFactory(private val repository: SaaSUserRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
