package com.example.payments

import java.util.UUID

/**
 * Razorpay implementation of the PaymentProvider
 */
class RazorpayProvider(
    private val keyId: String,
    private val keySecret: String,
    private val testMode: Boolean,
    private val enabledMethods: List<String>
) : PaymentProvider {
    
    override val name: String = "Razorpay"

    override fun isAvailable(country: String, currency: String): Boolean {
        // Razorpay is globally available but optimized for INR and major global currencies
        val supportedCurrencies = listOf("INR", "USD", "EUR", "GBP", "AED", "SGD", "CAD", "AUD")
        return supportedCurrencies.contains(currency.uppercase())
    }

    override fun getSupportedMethods(): List<String> {
        return enabledMethods
    }

    override suspend fun createOrder(amount: Double, currency: String, receipt: String): PaymentOrder {
        // In a real app, this would call the Razorpay Order API on the backend
        // Here we simulate the order creation for the demo
        val orderId = "order_" + UUID.randomUUID().toString().substring(0, 12).replace("-", "")
        return PaymentOrder(
            orderId = orderId,
            amount = (amount * 100).toLong(), // convert to paise/cents
            currency = currency,
            status = "created"
        )
    }

    override fun getSettings(): ProviderSettings {
        return ProviderSettings(
            keyId = keyId,
            isTestMode = testMode,
            enabledMethods = enabledMethods
        )
    }
}
