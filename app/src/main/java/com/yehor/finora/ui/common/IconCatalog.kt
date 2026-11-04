package com.yehor.finora.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Maps the stable keys of [IconKeys] to vectors. Unknown or null keys render the default icon. */
object IconCatalog {

    private val icons: Map<String, ImageVector> = mapOf(
        "wallet" to Icons.Filled.AccountBalanceWallet,
        "bank" to Icons.Filled.AccountBalance,
        "card" to Icons.Filled.CreditCard,
        "cash" to Icons.Filled.Money,
        "savings" to Icons.Filled.Savings,
        "trending_up" to Icons.Filled.TrendingUp,
        "chart" to Icons.Filled.ShowChart,
        "business" to Icons.Filled.Business,
        "phone" to Icons.Filled.PhoneAndroid,
        "paid" to Icons.Filled.Paid,
        "lock" to Icons.Filled.Lock,
        "star" to Icons.Filled.Star,
        "food" to Icons.Filled.Fastfood,
        "cart" to Icons.Filled.ShoppingCart,
        "restaurant" to Icons.Filled.Restaurant,
        "coffee" to Icons.Filled.LocalCafe,
        "home" to Icons.Filled.Home,
        "car" to Icons.Filled.DirectionsCar,
        "bus" to Icons.Filled.DirectionsBus,
        "flight" to Icons.Filled.Flight,
        "shopping" to Icons.Filled.ShoppingBag,
        "entertainment" to Icons.Filled.Movie,
        "health" to Icons.Filled.LocalHospital,
        "fitness" to Icons.Filled.FitnessCenter,
        "pets" to Icons.Filled.Pets,
        "school" to Icons.Filled.School,
        "build" to Icons.Filled.Build,
        "bolt" to Icons.Filled.Bolt,
        "subscription" to Icons.Filled.Subscriptions,
        "fee" to Icons.Filled.ReceiptLong,
        "work" to Icons.Filled.Work,
        "laptop" to Icons.Filled.Laptop,
        "gift" to Icons.Filled.CardGiftcard,
        "refund" to Icons.Filled.Replay,
        "swap" to Icons.Filled.SwapHoriz,
        "exchange" to Icons.Filled.CurrencyExchange,
        IconKeys.DEFAULT to Icons.Filled.Category,
    )

    fun get(key: String?): ImageVector = icons.getValue(IconKeys.resolve(key))

    /** Keys that have a vector. Must equal [IconKeys.all]; checked by a test. */
    val keys: Set<String> get() = icons.keys
}
