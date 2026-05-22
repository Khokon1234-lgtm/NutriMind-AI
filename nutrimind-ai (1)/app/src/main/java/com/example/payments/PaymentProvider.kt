package com.example.payments

/**
 * Common interface for all payment gateways (Razorpay, Stripe, etc.)
 */
interface PaymentProvider {
    val name: String
    
    fun isAvailable(country: String, currency: String): Boolean
    
    fun getSupportedMethods(): List<String>
    
    suspend fun createOrder(
        amount: Double,
        currency: String,
        receipt: String
    ): PaymentOrder
    
    fun getSettings(): ProviderSettings
}

data class PaymentOrder(
    val orderId: String,
    val amount: Long, // in smallest unit
    val currency: String,
    val status: String
)

data class ProviderSettings(
    val keyId: String,
    val isTestMode: Boolean,
    val enabledMethods: List<String>
)
