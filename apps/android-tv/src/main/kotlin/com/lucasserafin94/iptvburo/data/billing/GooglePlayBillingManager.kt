package com.lucasserafin94.iptvburo.data.billing

import android.app.Activity
import android.provider.Settings
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.lucasserafin94.iptvburo.BuildConfig
import com.lucasserafin94.iptvburo.data.licensing.AndroidLicenseService
import com.lucasserafin94.iptvburo.data.licensing.GooglePlayPurchaseSubmission
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Period
import java.util.ArrayDeque
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The native Google Play purchase adapter for Android/Android TV.
 *
 * It never grants a licence and never acknowledges a purchase locally. Tokens go directly to the
 * Worker, which verifies them with Google, commits the entitlement, then acknowledges delivery.
 */
class GooglePlayBillingManager(
    private val activity: Activity,
    private val licenseService: AndroidLicenseService,
    private val onOutcome: (GooglePlayBillingOutcome) -> Unit,
) : PurchasesUpdatedListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val readyActions = ArrayDeque<() -> Unit>()
    private var connecting = false
    private var restoreInFlight = false
    private var requestedDeviceId: String? = null

    private val billingClient =
        BillingClient.newBuilder(activity)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build(),
            )
            .build()

    fun launchPurchase(deviceId: String) {
        requestedDeviceId = deviceId.takeIf(String::isNotBlank)
        val accountId = currentAccountId() ?: return onOutcome(GooglePlayBillingOutcome.Unavailable)
        whenReady {
            val product =
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(BuildConfig.GOOGLE_PLAY_PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            billingClient.queryProductDetailsAsync(
                QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build(),
            ) { result, queryResult ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    onOutcome(GooglePlayBillingOutcome.Unavailable)
                    return@queryProductDetailsAsync
                }
                val details =
                    queryResult.productDetailsList.singleOrNull {
                        it.productId == BuildConfig.GOOGLE_PLAY_PRODUCT_ID
                    } ?: run {
                        onOutcome(GooglePlayBillingOutcome.Unavailable)
                        return@queryProductDetailsAsync
                    }
                val offer = selectRentalOffer(details) ?: run {
                    onOutcome(GooglePlayBillingOutcome.Unavailable)
                    return@queryProductDetailsAsync
                }
                val offerToken = offer.offerToken ?: run {
                    onOutcome(GooglePlayBillingOutcome.Unavailable)
                    return@queryProductDetailsAsync
                }
                val productParams =
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build()
                val flow =
                    BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(listOf(productParams))
                        .setObfuscatedAccountId(accountId)
                        .setIsOfferPersonalized(false)
                        .build()
                val launched = billingClient.launchBillingFlow(activity, flow)
                if (launched.responseCode != BillingClient.BillingResponseCode.OK) {
                    if (launched.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                        restorePurchases(deviceId)
                    } else {
                        onOutcome(GooglePlayBillingOutcome.Unavailable)
                    }
                }
            }
        }
    }

    /** Queries Play on resume so completed pending payments and same-device reinstalls are restored. */
    fun restorePurchases(deviceId: String) {
        requestedDeviceId = deviceId.takeIf(String::isNotBlank)
        if (restoreInFlight || requestedDeviceId == null) return
        val accountId = currentAccountId() ?: return
        restoreInFlight = true
        whenReady {
            val params =
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                restoreInFlight = false
                if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
                processPurchases(purchases, accountId, notifyWhenEmpty = false)
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val accountId = currentAccountId()
                    ?: return onOutcome(GooglePlayBillingOutcome.Unavailable)
                processPurchases(purchases.orEmpty(), accountId, notifyWhenEmpty = true)
            }

            BillingClient.BillingResponseCode.USER_CANCELED ->
                onOutcome(GooglePlayBillingOutcome.Cancelled)

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                requestedDeviceId?.let(::restorePurchases)

            else -> onOutcome(GooglePlayBillingOutcome.Unavailable)
        }
    }

    fun close() {
        readyActions.clear()
        billingClient.endConnection()
        scope.cancel()
    }

    private fun processPurchases(
        purchases: List<Purchase>,
        accountId: String,
        notifyWhenEmpty: Boolean,
    ) {
        val relevant = purchases.filter { BuildConfig.GOOGLE_PLAY_PRODUCT_ID in it.products }
        if (relevant.isEmpty()) {
            if (notifyWhenEmpty) onOutcome(GooglePlayBillingOutcome.Rejected)
            return
        }
        for (purchase in relevant) {
            // PENDING is still sent to the Worker so it is recorded, but cannot become ACTIVE there.
            if (
                purchase.purchaseState != Purchase.PurchaseState.PURCHASED &&
                purchase.purchaseState != Purchase.PurchaseState.PENDING
            ) {
                continue
            }
            scope.launch {
                val submission =
                    withContext(Dispatchers.IO) {
                        licenseService.submitGooglePlayPurchase(purchase.purchaseToken, accountId)
                    }
                onOutcome(
                    when (submission) {
                        is GooglePlayPurchaseSubmission.Verified -> GooglePlayBillingOutcome.Verified
                        GooglePlayPurchaseSubmission.Pending -> GooglePlayBillingOutcome.Pending
                        GooglePlayPurchaseSubmission.Rejected -> GooglePlayBillingOutcome.Rejected
                        GooglePlayPurchaseSubmission.Unreachable -> GooglePlayBillingOutcome.Unavailable
                    },
                )
            }
        }
    }

    private fun selectRentalOffer(details: ProductDetails): ProductDetails.OneTimePurchaseOfferDetails? =
        details.oneTimePurchaseOfferDetailsList
            .orEmpty()
            .firstOrNull { offer ->
                offer.purchaseOptionId == BuildConfig.GOOGLE_PLAY_PURCHASE_OPTION_ID &&
                    offer.offerToken?.isNotBlank() == true &&
                    offer.rentalDetails?.rentalPeriod
                        ?.let { runCatching { Period.parse(it) }.getOrNull() }
                        ?.let { period -> period == Period.parse(BuildConfig.GOOGLE_PLAY_RENTAL_PERIOD) } == true
            }

    private fun whenReady(action: () -> Unit) {
        if (billingClient.isReady) {
            action()
            return
        }
        readyActions.addLast(action)
        if (connecting) return
        connecting = true
        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connecting = false
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        readyActions.clear()
                        restoreInFlight = false
                        onOutcome(GooglePlayBillingOutcome.Unavailable)
                        return
                    }
                    while (readyActions.isNotEmpty()) readyActions.removeFirst().invoke()
                }

                override fun onBillingServiceDisconnected() {
                    connecting = false
                }
            },
        )
    }

    private fun currentAccountId(): String? {
        val androidId =
            Settings.Secure.getString(activity.contentResolver, Settings.Secure.ANDROID_ID)
                ?.takeIf(String::isNotBlank)
                ?: return null
        return obfuscatedPlayAccountId(androidId, BuildConfig.APPLICATION_ID.removeSuffix(".debug"))
    }
}

enum class GooglePlayBillingOutcome {
    Verified,
    Pending,
    Cancelled,
    Rejected,
    Unavailable,
}

internal fun obfuscatedPlayAccountId(androidId: String, applicationId: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(
            "iptvburo-play-account-v1\n$applicationId\n$androidId"
                .toByteArray(StandardCharsets.UTF_8),
        ).joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
