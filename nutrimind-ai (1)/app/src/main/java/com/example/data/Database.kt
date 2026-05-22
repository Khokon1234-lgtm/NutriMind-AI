package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- Base Entities ---

@Entity(tableName = "users")
data class UserAuth(
    @PrimaryKey val email: String,
    val name: String,
    val passwordHash: String
)

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val email: String,
    val name: String,
    val plan: String = "Free", // Free, Pro, ProPlus, Elite
    val height: Float = 170f, // in cm
    val weight: Float = 65f, // in kg
    val age: Int = 26,
    val activityLevel: String = "Moderate", // Sedentary, Moderate, Active
    val dietPreference: String = "Balanced (Veg/Non-Veg)", // Veg, Non-Veg, Vegan, Balanced
    val targetCalories: Int = 2100,
    val targetWater: Int = 2500, // in ml
    // Added for subscription memory, progress & gamification
    val xp: Int = 120,
    val level: Int = 1,
    val streak: Int = 3,
    val trialStartDate: Long = System.currentTimeMillis(),
    val allergies: String = "None",
    val healthPreferences: String = "High protein, Organic, Low sugar"
)

@Entity(tableName = "symptom_logs")
data class SymptomLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val symptoms: String,
    val probableDeficiencies: String,
    val foodRecommendations: String,
    val generalAdvice: String
)

@Entity(tableName = "food_logs")
data class FoodLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val name: String,
    val calories: Int,
    val protein: Float, // grams
    val vitamins: String,
    val healthScore: Int, // 1-100
    val isHealthy: Boolean
)

@Entity(tableName = "water_logs")
data class WaterLog(
    @PrimaryKey val dateString: String, // "YYYY-MM-DD"
    val amountMl: Int
)

@Entity(tableName = "activity_habit_logs")
data class ActivityHabitLog(
    @PrimaryKey val dateString: String, // "YYYY-MM-DD"
    val sleepHours: Float = 0f,
    val eyeBreaks: Int = 0,
    val steps: Int = 0,
    val mood: String = "Serene" // Serene, Energetic, Neutral, Stressed, Tired
)

// --- DAO Definition ---

@Dao
interface DbDao {
    // Authentication & Profile Query
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserAuth?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserAuth)

    @Query("SELECT * FROM user_profiles WHERE email = :email LIMIT 1")
    fun getProfileFlow(email: String): Flow<UserProfile?>

    @Query("SELECT * FROM user_profiles WHERE email = :email LIMIT 1")
    suspend fun getProfileByEmail(email: String): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: UserProfile)

    // Symptom Tracer Logs
    @Query("SELECT * FROM symptom_logs ORDER BY timestamp DESC")
    fun getAllSymptomLogs(): Flow<List<SymptomLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSymptomLog(log: SymptomLog)

    // Food Scanner / Diet History Logs
    @Query("SELECT * FROM food_logs ORDER BY timestamp DESC")
    fun getAllFoodLogs(): Flow<List<FoodLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodLog(log: FoodLog)

    @Query("DELETE FROM food_logs WHERE id = :id")
    suspend fun deleteFoodLog(id: Long)

    // Water tracker queries
    @Query("SELECT * FROM water_logs WHERE dateString = :dateString LIMIT 1")
    suspend fun getWaterLog(dateString: String): WaterLog?

    @Query("SELECT * FROM water_logs ORDER BY dateString DESC")
    fun getAllWaterLogs(): Flow<List<WaterLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWaterLog(log: WaterLog)

    // Habit/Activity logs
    @Query("SELECT * FROM activity_habit_logs WHERE dateString = :dateString LIMIT 1")
    suspend fun getActivityHabitLog(dateString: String): ActivityHabitLog?

    @Query("SELECT * FROM activity_habit_logs ORDER BY dateString DESC")
    fun getAllActivityHabitLogs(): Flow<List<ActivityHabitLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveActivityHabitLog(log: ActivityHabitLog)

    // --- Razorpay Payment Transactions ---
    @Query("SELECT * FROM payment_transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<PaymentTransaction>>

    @Query("SELECT * FROM payment_transactions WHERE userEmail = :userEmail")
    fun getTransactionsByUser(userEmail: String): Flow<List<PaymentTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(tx: PaymentTransaction)

    // --- Admin Payment Settings ---
    @Query("SELECT * FROM admin_payment_settings WHERE id = 0 LIMIT 1")
    suspend fun getAdminSettings(): AdminPaymentSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAdminSettings(settings: AdminPaymentSettings)
}

// --- Admin Payment Settings Entity ---
@Entity(tableName = "admin_payment_settings")
data class AdminPaymentSettings(
    @PrimaryKey val id: Int = 0, // Singleton setting
    val razorpayKeyId: String = "",
    val razorpayKeySecret: String = "",
    val isRazorpayEnabled: Boolean = true,
    val isStripeEnabled: Boolean = false,
    val isTestMode: Boolean = true,
    val supportedCurrencies: String = "INR,USD,EUR,GBP,AED,SGD,CAD,AUD",
    val enabledPaymentMethods: String = "UPI,Card,Netbanking,Wallet,QR",
    val webhookSecret: String = ""
)

// --- Razorpay Transaction Entity ---
@Entity(tableName = "payment_transactions")
data class PaymentTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paymentId: String,
    val orderId: String,
    val userEmail: String,
    val currency: String,
    val amount: Double,
    val subscriptionType: String, // "Monthly" or "Yearly"
    val transactionStatus: String, // "Success", "Failed", "Refunded"
    val country: String,
    val invoiceDetails: String,
    val timestamp: Long = System.currentTimeMillis()
)

// --- App Database Configuration ---

@Database(
    entities = [
        UserAuth::class,
        UserProfile::class,
        SymptomLog::class,
        FoodLog::class,
        WaterLog::class,
        ActivityHabitLog::class,
        PaymentTransaction::class,
        AdminPaymentSettings::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): DbDao
}
