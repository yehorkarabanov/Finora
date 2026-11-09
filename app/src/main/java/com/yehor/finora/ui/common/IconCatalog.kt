package com.yehor.finora.ui.common

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.yehor.finora.R

/** One user-content icon: the vector and its readable name (search, TalkBack). */
class IconEntry(val vector: ImageVector, @StringRes val label: Int)

/**
 * Maps the stable keys of [IconKeys] to vectors. Unknown or null keys render the default icon.
 * User-content icons use the Rounded family only.
 */
object IconCatalog {

    private val icons: Map<String, IconEntry> = mapOf(
        "wallet" to IconEntry(Icons.Rounded.AccountBalanceWallet, R.string.icon_wallet),
        "bank" to IconEntry(Icons.Rounded.AccountBalance, R.string.icon_bank),
        "card" to IconEntry(Icons.Rounded.CreditCard, R.string.icon_card),
        "cash" to IconEntry(Icons.Rounded.Money, R.string.icon_cash),
        "savings" to IconEntry(Icons.Rounded.Savings, R.string.icon_savings),
        "investments" to IconEntry(Icons.Rounded.TrendingUp, R.string.icon_investments),
        "business" to IconEntry(Icons.Rounded.Business, R.string.icon_business),
        "phone" to IconEntry(Icons.Rounded.PhoneAndroid, R.string.icon_phone),
        "restaurant" to IconEntry(Icons.Rounded.Restaurant, R.string.icon_restaurant),
        "coffee" to IconEntry(Icons.Rounded.LocalCafe, R.string.icon_coffee),
        "home" to IconEntry(Icons.Rounded.Home, R.string.icon_home),
        "car" to IconEntry(Icons.Rounded.DirectionsCar, R.string.icon_car),
        "bus" to IconEntry(Icons.Rounded.DirectionsBus, R.string.icon_bus),
        "flight" to IconEntry(Icons.Rounded.Flight, R.string.icon_flight),
        "shopping" to IconEntry(Icons.Rounded.ShoppingBag, R.string.icon_shopping),
        "entertainment" to IconEntry(Icons.Rounded.Movie, R.string.icon_entertainment),
        "health" to IconEntry(Icons.Rounded.LocalHospital, R.string.icon_health),
        "fitness" to IconEntry(Icons.Rounded.FitnessCenter, R.string.icon_fitness),
        "pets" to IconEntry(Icons.Rounded.Pets, R.string.icon_pets),
        "school" to IconEntry(Icons.Rounded.School, R.string.icon_school),
        "repairs" to IconEntry(Icons.Rounded.Build, R.string.icon_repairs),
        "subscriptions" to IconEntry(Icons.Rounded.Subscriptions, R.string.icon_subscriptions),
        "fees" to IconEntry(Icons.AutoMirrored.Rounded.ReceiptLong, R.string.icon_fees),
        "freelance" to IconEntry(Icons.Rounded.Laptop, R.string.icon_freelance),
        "gift" to IconEntry(Icons.Rounded.CardGiftcard, R.string.icon_gift),
        "refund" to IconEntry(Icons.Rounded.Replay, R.string.icon_refund),
        "transfer" to IconEntry(Icons.Rounded.SwapHoriz, R.string.icon_transfer),
        "exchange" to IconEntry(Icons.Rounded.CurrencyExchange, R.string.icon_exchange),
        "other" to IconEntry(Icons.Rounded.Category, R.string.icon_other),
        "groceries" to IconEntry(Icons.Rounded.LocalGroceryStore, R.string.icon_groceries),
        "bakery" to IconEntry(Icons.Rounded.BakeryDining, R.string.icon_bakery),
        "bar" to IconEntry(Icons.Rounded.LocalBar, R.string.icon_bar),
        "delivery" to IconEntry(Icons.Rounded.DeliveryDining, R.string.icon_delivery),
        "pizza" to IconEntry(Icons.Rounded.LocalPizza, R.string.icon_pizza),
        "dessert" to IconEntry(Icons.Rounded.Icecream, R.string.icon_dessert),
        "fuel" to IconEntry(Icons.Rounded.LocalGasStation, R.string.icon_fuel),
        "train" to IconEntry(Icons.Rounded.Train, R.string.icon_train),
        "taxi" to IconEntry(Icons.Rounded.LocalTaxi, R.string.icon_taxi),
        "bike" to IconEntry(Icons.Rounded.PedalBike, R.string.icon_bike),
        "scooter" to IconEntry(Icons.Rounded.ElectricScooter, R.string.icon_scooter),
        "motorcycle" to IconEntry(Icons.Rounded.TwoWheeler, R.string.icon_motorcycle),
        "parking" to IconEntry(Icons.Rounded.LocalParking, R.string.icon_parking),
        "ev_charging" to IconEntry(Icons.Rounded.EvStation, R.string.icon_ev_charging),
        "car_repair" to IconEntry(Icons.Rounded.CarRepair, R.string.icon_car_repair),
        "ship" to IconEntry(Icons.Rounded.DirectionsBoat, R.string.icon_ship),
        "rent" to IconEntry(Icons.Rounded.Apartment, R.string.icon_rent),
        "utilities" to IconEntry(Icons.Rounded.Lightbulb, R.string.icon_utilities),
        "water" to IconEntry(Icons.Rounded.WaterDrop, R.string.icon_water),
        "gas" to IconEntry(Icons.Rounded.LocalFireDepartment, R.string.icon_gas),
        "internet" to IconEntry(Icons.Rounded.Wifi, R.string.icon_internet),
        "tv" to IconEntry(Icons.Rounded.Tv, R.string.icon_tv),
        "furniture" to IconEntry(Icons.Rounded.Chair, R.string.icon_furniture),
        "cleaning" to IconEntry(Icons.Rounded.CleaningServices, R.string.icon_cleaning),
        "garden" to IconEntry(Icons.Rounded.Yard, R.string.icon_garden),
        "kitchen" to IconEntry(Icons.Rounded.Kitchen, R.string.icon_kitchen),
        "clothing" to IconEntry(Icons.Rounded.Checkroom, R.string.icon_clothing),
        "electronics" to IconEntry(Icons.Rounded.Devices, R.string.icon_electronics),
        "beauty" to IconEntry(Icons.Rounded.Spa, R.string.icon_beauty),
        "jewelry" to IconEntry(Icons.Rounded.Diamond, R.string.icon_jewelry),
        "books" to IconEntry(Icons.Rounded.MenuBook, R.string.icon_books),
        "pharmacy" to IconEntry(Icons.Rounded.Medication, R.string.icon_pharmacy),
        "doctor" to IconEntry(Icons.Rounded.MedicalServices, R.string.icon_doctor),
        "mental_health" to IconEntry(Icons.Rounded.SelfImprovement, R.string.icon_mental_health),
        "vaccine" to IconEntry(Icons.Rounded.Vaccines, R.string.icon_vaccine),
        "courses" to IconEntry(Icons.Rounded.Class, R.string.icon_courses),
        "kids" to IconEntry(Icons.Rounded.ChildCare, R.string.icon_kids),
        "toys" to IconEntry(Icons.Rounded.Toys, R.string.icon_toys),
        "baby" to IconEntry(Icons.Rounded.ChildFriendly, R.string.icon_baby),
        "music" to IconEntry(Icons.Rounded.MusicNote, R.string.icon_music),
        "games" to IconEntry(Icons.Rounded.SportsEsports, R.string.icon_games),
        "sports" to IconEntry(Icons.Rounded.SportsSoccer, R.string.icon_sports),
        "travel" to IconEntry(Icons.Rounded.Luggage, R.string.icon_travel),
        "hotel" to IconEntry(Icons.Rounded.Hotel, R.string.icon_hotel),
        "beach" to IconEntry(Icons.Rounded.BeachAccess, R.string.icon_beach),
        "camera" to IconEntry(Icons.Rounded.PhotoCamera, R.string.icon_camera),
        "events" to IconEntry(Icons.Rounded.Celebration, R.string.icon_events),
        "tickets" to IconEntry(Icons.Rounded.ConfirmationNumber, R.string.icon_tickets),
        "hiking" to IconEntry(Icons.Rounded.Hiking, R.string.icon_hiking),
        "charity" to IconEntry(Icons.Rounded.VolunteerActivism, R.string.icon_charity),
        "haircut" to IconEntry(Icons.Rounded.ContentCut, R.string.icon_haircut),
        "laundry" to IconEntry(Icons.Rounded.LocalLaundryService, R.string.icon_laundry),
        "family" to IconEntry(Icons.Rounded.FamilyRestroom, R.string.icon_family),
        "taxes" to IconEntry(Icons.Rounded.RequestQuote, R.string.icon_taxes),
        "insurance" to IconEntry(Icons.Rounded.Shield, R.string.icon_insurance),
        "loan" to IconEntry(Icons.Rounded.CreditScore, R.string.icon_loan),
        "interest" to IconEntry(Icons.Rounded.Percent, R.string.icon_interest),
        "atm" to IconEntry(Icons.Rounded.LocalAtm, R.string.icon_atm),
        "salary" to IconEntry(Icons.Rounded.Payments, R.string.icon_salary),
        "bonus" to IconEntry(Icons.Rounded.EmojiEvents, R.string.icon_bonus),
        "rental_income" to IconEntry(Icons.Rounded.Key, R.string.icon_rental_income),
        "dividends" to IconEntry(Icons.Rounded.PieChart, R.string.icon_dividends),
        "store" to IconEntry(Icons.Rounded.Storefront, R.string.icon_store),
        "favorite" to IconEntry(Icons.Rounded.Favorite, R.string.icon_favorite),
        "flag" to IconEntry(Icons.Rounded.Flag, R.string.icon_flag),
        "pos" to IconEntry(Icons.Rounded.PointOfSale, R.string.icon_pos),
        "deposit" to IconEntry(Icons.Rounded.AddCard, R.string.icon_deposit),
        "euro" to IconEntry(Icons.Rounded.EuroSymbol, R.string.icon_euro),
        "dollar" to IconEntry(Icons.Rounded.AttachMoney, R.string.icon_dollar),
        "brokerage" to IconEntry(Icons.Rounded.CandlestickChart, R.string.icon_brokerage),
        "crypto" to IconEntry(Icons.Rounded.CurrencyBitcoin, R.string.icon_crypto),
        "gold" to IconEntry(Icons.Rounded.Toll, R.string.icon_gold),
        "real_estate" to IconEntry(Icons.Rounded.HomeWork, R.string.icon_real_estate),
        "pension" to IconEntry(Icons.Rounded.Elderly, R.string.icon_pension),
        "gift_card" to IconEntry(Icons.Rounded.Redeem, R.string.icon_gift_card),
    )

    fun get(key: String?): ImageVector = icons.getValue(IconKeys.resolve(key)).vector

    /** Readable name of an icon for search and accessibility. Unknown keys read as the default icon. */
    @StringRes
    fun label(key: String?): Int = icons.getValue(IconKeys.resolve(key)).label

    /** Keys that have a vector. Must equal [IconKeys.all]; checked by a test. */
    val keys: Set<String> get() = icons.keys
}
