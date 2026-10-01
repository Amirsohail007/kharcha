package com.amir.expense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amir.expense.data.AppDatabase
import com.amir.expense.data.Category

/**
 * Category identity colors: the 8 validated categorical slots, each pinned to one seeded
 * top-level category (by id, see [SEED_NAME]) so a color never moves when categories are added,
 * removed or renamed.
 * Categories you create yourself get neutral gray plus their own initial.
 */
private enum class Hue(val light: Long, val dark: Long) {
    Blue(0xFF2A78D6, 0xFF3987E5), Orange(0xFFEB6834, 0xFFD95926), Aqua(0xFF1BAF7A, 0xFF199E70),
    Yellow(0xFFEDA100, 0xFFC98500), Magenta(0xFFE87BA4, 0xFFD55181), Green(0xFF008300, 0xFF008300),
    Violet(0xFF4A3AA7, 0xFF9085E9), Red(0xFFE34948, 0xFFE66767),
}

private val PARENT_HUE = mapOf(
    "food" to Hue.Orange, "travel" to Hue.Blue, "bills" to Hue.Violet, "shopping" to Hue.Magenta,
    "health" to Hue.Aqua, "fun" to Hue.Yellow, "personal" to Hue.Red, "other" to Hue.Green,
)

private val ICONS: Map<String, ImageVector> = mapOf(
    "food" to Icons.Rounded.Restaurant, "delivery" to Icons.Rounded.DeliveryDining,
    "restaurant" to Icons.Rounded.Restaurant, "groceries" to Icons.Rounded.LocalGroceryStore,
    "travel" to Icons.Rounded.DirectionsCar, "office" to Icons.Rounded.Work,
    "explore" to Icons.Rounded.Explore, "fuel" to Icons.Rounded.LocalGasStation,
    "bills" to Icons.AutoMirrored.Rounded.ReceiptLong, "rent" to Icons.Rounded.Home, "utilities" to Icons.Rounded.Bolt,
    "mobile & internet" to Icons.Rounded.Smartphone, "subscriptions" to Icons.Rounded.Subscriptions,
    "shopping" to Icons.Rounded.ShoppingBag, "clothes" to Icons.Rounded.Checkroom,
    "electronics" to Icons.Rounded.Devices, "household" to Icons.Rounded.Chair,
    "health" to Icons.Rounded.Favorite, "medicine" to Icons.Rounded.Medication,
    "doctor" to Icons.Rounded.MedicalServices, "fun" to Icons.Rounded.TheaterComedy,
    "movies" to Icons.Rounded.Movie, "outings" to Icons.Rounded.Celebration,
    "personal" to Icons.Rounded.Person, "grooming" to Icons.Rounded.ContentCut,
    "gifts" to Icons.Rounded.CardGiftcard, "other" to Icons.Rounded.Category,
)

/** Seeded category id -> its original name, so a renamed "Food" keeps Food's color and icon. */
private val SEED_NAME: Map<Long, String> =
    AppDatabase.SEED.flatMap { (parent, kids) -> listOf(parent) + kids }
        .mapIndexed { i, name -> (i + 1L) to name.lowercase() }.toMap()

/** Seeded categories by original name; ones you create by their own name. */
private fun Category.styleName(): String = SEED_NAME[id] ?: name.lowercase()

/** How a category is drawn: [icon] (null = show [initial]) on a [container] tint, in [color]. */
data class CategoryLook(val icon: ImageVector?, val initial: String, val color: Color, val container: Color)

@Composable
fun lookOf(categoryId: Long?, categories: List<Category>): CategoryLook {
    val c = categories.firstOrNull { it.id == categoryId }
    val parent = c?.parentId?.let { pid -> categories.firstOrNull { it.id == pid } } ?: c
    val dark = isSystemInDarkTheme()
    val hue = parent?.let { PARENT_HUE[it.styleName()] }
    val color = hue?.let { Color(if (dark) it.dark else it.light) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    val icon = c?.let { ICONS[it.styleName()] ?: parent?.let { p -> ICONS[p.styleName()] } }
        ?: if (c == null) Icons.Rounded.Sell else null
    val container = color.copy(alpha = if (dark) 0.24f else 0.13f).compositeOver(MaterialTheme.colorScheme.surfaceContainerLowest)
    return CategoryLook(icon, c?.name?.take(1)?.uppercase() ?: "?", color, container)
}

/** Rounded-square badge with the category icon (or initial). */
@Composable
fun CategoryBadge(look: CategoryLook, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).background(look.container, RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        if (look.icon != null) {
            Icon(look.icon, contentDescription = null, tint = look.color, modifier = Modifier.size(size * 0.52f))
        } else {
            Text(look.initial, color = look.color, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp)
        }
    }
}

/** Round avatar with the merchant's first letter, for payments not filed yet. */
@Composable
fun LetterAvatar(name: String, size: Dp = 40.dp, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(
        Modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "₹",
            color = color, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp,
        )
    }
}
