package com.example.payments

/**
 * Stripe implementation placeholder for future integration
 */
class StripeProvider(
    private val apiKey: String,
    private val isTest: Boolean
) : PaymentProvider {
    
    override val name: String = "Stripe"

    override fun isAvailable(country: String, currency: String): Boolean {
        // Stripe is available in many countries
        return true
    }

    override fun getSupportedMethods(): List<String> {
        return listOf("Card", "ApplePay", "GooglePay")
    }

    override suspend fun createOrder(amount: Double, currency: String, receipt: String): PaymentOrder {
        // Not implemented yet
        return PaymentOrder("stripe_pending", (amount * 100).toLong(), currency, "pending")
    }

    override fun getSettings(): ProviderSettings {
        return ProviderSettings(apiKey, isTest, listOf("Card"))
    }
}
