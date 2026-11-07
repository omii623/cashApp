package com.example.cashapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cashapp.domain.model.CategoryIcon

/**
 * A választható ikonok. A kulcs kerül az adatbázisba – **meglévő kulcsot ne nevezz át**,
 * újat bármikor hozzá lehet adni. A sorrend = a választóban megjelenő sorrend.
 */
object CategoryIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        // Számlákhoz
        "cash" to Icons.Filled.LocalAtm,
        "wallet" to Icons.Filled.AccountBalanceWallet,
        "card" to Icons.Filled.CreditCard,
        // Kategóriákhoz
        "work" to Icons.Filled.Work,
        "side" to Icons.Filled.Laptop,
        "money" to Icons.Filled.Payments,
        "trending" to Icons.AutoMirrored.Filled.TrendingUp,
        "gift" to Icons.Filled.CardGiftcard,
        "home" to Icons.Filled.Home,
        "bills" to Icons.AutoMirrored.Filled.ReceiptLong,
        "electricity" to Icons.Filled.Bolt,
        "water" to Icons.Filled.WaterDrop,
        "wifi" to Icons.Filled.Wifi,
        "phone" to Icons.Filled.PhoneAndroid,
        "food" to Icons.Filled.LocalGroceryStore,
        "restaurant" to Icons.Filled.Restaurant,
        "coffee" to Icons.Filled.LocalCafe,
        "bar" to Icons.Filled.LocalBar,
        "transport" to Icons.Filled.DirectionsCar,
        "bus" to Icons.Filled.DirectionsBus,
        "fuel" to Icons.Filled.LocalGasStation,
        "parking" to Icons.Filled.LocalParking,
        "flight" to Icons.Filled.Flight,
        "insurance" to Icons.Filled.Shield,
        "health" to Icons.Filled.LocalHospital,
        "medication" to Icons.Filled.Medication,
        "sport" to Icons.Filled.FitnessCenter,
        "fun" to Icons.Filled.TheaterComedy,
        "movie" to Icons.Filled.Movie,
        "music" to Icons.Filled.MusicNote,
        "games" to Icons.Filled.SportsEsports,
        "clothes" to Icons.Filled.Checkroom,
        "shopping" to Icons.Filled.ShoppingBag,
        "savings" to Icons.Filled.Savings,
        "loan" to Icons.Filled.CreditCard,
        "bank" to Icons.Filled.AccountBalance,
        "vacation" to Icons.Filled.BeachAccess,
        "school" to Icons.Filled.School,
        "kids" to Icons.Filled.ChildCare,
        "pets" to Icons.Filled.Pets,
        "repair" to Icons.Filled.Build,
        "other" to Icons.Filled.Category,
    )

    private val byKey = all.toMap()

    fun vector(key: String): ImageVector? = byKey[key]
}

/**
 * A váltás rögzített jele. Nem kategória, ezért nem szerkeszthető – csak itt, kódban változtatható.
 * Minden helyen (főoldali gomb, tételsor, Váltás oldal) ezt kell használni.
 */
object ExchangeIcon {
    val vector: ImageVector = Icons.AutoMirrored.Filled.CompareArrows
}

/** Kör a váltás rögzített ikonjával (a tételsorokban a kategória-jelvény helyén). */
@Composable
fun ExchangeBadge(color: Color, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(
        modifier.size(size).background(color.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(ExchangeIcon.vector, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.58f))
    }
}

/**
 * Színes kör a kategória ikonjával vagy betűjével.
 * Ha az ikon ismeretlen / nincs megadva, a név első betűje jelenik meg.
 */
@Composable
fun CategoryBadge(
    icon: String?,
    color: Color,
    name: String?,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    Box(
        modifier.size(size).background(color.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        CategorySymbol(icon, color, name, size)
    }
}

/** Csak a jel (ikon vagy betű) háttér nélkül – pl. kijelölt, színes csempén. */
@Composable
fun CategorySymbol(icon: String?, tint: Color, name: String?, size: Dp = 32.dp) {
    val parsed = CategoryIcon.parse(icon)
    val vector = (parsed as? CategoryIcon.Vector)?.let { CategoryIcons.vector(it.key) }
    if (vector != null) {
        Icon(vector, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.58f))
    } else {
        val text = (parsed as? CategoryIcon.Letter)?.text ?: name?.take(1)?.uppercase() ?: "?"
        Text(
            text,
            color = tint,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * if (text.length > 1) 0.36f else 0.45f).sp,
            maxLines = 1,
        )
    }
}
