package com.naman.rupeeflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.ui.theme.AppTheme

data class CategoryVisual(
    val icon: ImageVector,
    val tintColor: Color,
    val backgroundColor: Color,
    val displayName: String
)

object CategoryIconHelper {
    // 16 Consistent Category Visuals with Cute Peachy / Pastel Palettes
    val FoodVisual = CategoryVisual(
        icon = Icons.Default.Restaurant,
        tintColor = Color(0xFFF58B72),      // Deep peach
        backgroundColor = Color(0xFFFFF0E6), // Peach cream
        displayName = "Food & Drinks"
    )

    val ShoppingVisual = CategoryVisual(
        icon = Icons.Default.ShoppingBag,
        tintColor = Color(0xFFF5A6B8),      // Accent pink
        backgroundColor = Color(0xFFFDF2F4),
        displayName = "Shopping"
    )

    val TransportVisual = CategoryVisual(
        icon = Icons.Default.DirectionsCar,
        tintColor = Color(0xFF268B65),      // Emerald mint
        backgroundColor = Color(0xFFEDFAF3),
        displayName = "Transport"
    )

    val HousingVisual = CategoryVisual(
        icon = Icons.Default.Home,
        tintColor = Color(0xFF8B5CF6),      // Soft purple
        backgroundColor = Color(0xFFF7F3FE),
        displayName = "Housing"
    )

    val EntertainmentVisual = CategoryVisual(
        icon = Icons.Default.Movie,
        tintColor = Color(0xFFD97706),      // Warm amber
        backgroundColor = Color(0xFFFFF9EB),
        displayName = "Entertainment"
    )

    val HealthcareVisual = CategoryVisual(
        icon = Icons.Default.Favorite,
        tintColor = Color(0xFFEF4444),      // Coral red
        backgroundColor = Color(0xFFFEE2E2),
        displayName = "Healthcare"
    )

    val EducationVisual = CategoryVisual(
        icon = Icons.Default.School,
        tintColor = Color(0xFF3B82F6),      // Blue
        backgroundColor = Color(0xFFEFF6FF),
        displayName = "Education"
    )

    val BillsVisual = CategoryVisual(
        icon = Icons.Default.ReceiptLong,
        tintColor = Color(0xFFD97706),      // Warm amber
        backgroundColor = Color(0xFFFEF3C7),
        displayName = "Bills"
    )

    val TravelVisual = CategoryVisual(
        icon = Icons.Default.Flight,
        tintColor = Color(0xFF0284C7),      // Sky blue
        backgroundColor = Color(0xFFE0F2FE),
        displayName = "Travel"
    )

    val GroceriesVisual = CategoryVisual(
        icon = Icons.Default.ShoppingCart,
        tintColor = Color(0xFF10B981),      // Mint green
        backgroundColor = Color(0xFFECFDF5),
        displayName = "Groceries"
    )

    val PersonalCareVisual = CategoryVisual(
        icon = Icons.Default.Person,
        tintColor = Color(0xFFC026D3),      // Orchid
        backgroundColor = Color(0xFFFDF4FF),
        displayName = "Personal Care"
    )

    val GiftsVisual = CategoryVisual(
        icon = Icons.Default.CardGiftcard,
        tintColor = Color(0xFFF43F5E),      // Rose
        backgroundColor = Color(0xFFFFF1F2),
        displayName = "Gifts"
    )

    val IncomeVisual = CategoryVisual(
        icon = Icons.Default.TrendingUp,
        tintColor = Color(0xFF16A34A),      // Forest green
        backgroundColor = Color(0xFFDCFCE7),
        displayName = "Income"
    )

    val RefundsVisual = CategoryVisual(
        icon = Icons.Default.Undo,
        tintColor = Color(0xFF0D9488),      // Teal
        backgroundColor = Color(0xFFF0FDFA),
        displayName = "Refunds"
    )

    val TransfersVisual = CategoryVisual(
        icon = Icons.Default.SwapHoriz,
        tintColor = Color(0xFF6366F1),      // Indigo
        backgroundColor = Color(0xFFEEF2FF),
        displayName = "Transfers"
    )

    val OtherVisual = CategoryVisual(
        icon = Icons.Default.Category,
        tintColor = Color(0xFF7C3AED),      // Violet
        backgroundColor = Color(0xFFF5F3FF),
        displayName = "Other"
    )

    fun getVisual(categoryName: String?): CategoryVisual {
        val key = categoryName?.lowercase()?.trim() ?: ""
        return when {
            key.contains("food") || key.contains("drink") || key.contains("restaurant") || key.contains("dining") -> FoodVisual
            key.contains("grocery") || key.contains("groceries") || key.contains("supermarket") -> GroceriesVisual
            key.contains("transport") || key.contains("commute") || key.contains("car") || key.contains("cab") || key.contains("fuel") -> TransportVisual
            key.contains("shopping") || key.contains("cloth") -> ShoppingVisual
            key.contains("house") || key.contains("housing") || key.contains("rent") || key.contains("home") -> HousingVisual
            key.contains("entertain") || key.contains("movie") || key.contains("subscription") || key.contains("game") -> EntertainmentVisual
            key.contains("health") || key.contains("medical") || key.contains("doctor") || key.contains("pharmacy") -> HealthcareVisual
            key.contains("school") || key.contains("education") || key.contains("college") || key.contains("book") -> EducationVisual
            key.contains("bill") || key.contains("utilit") || key.contains("electric") || key.contains("wifi") || key.contains("water") -> BillsVisual
            key.contains("travel") || key.contains("flight") || key.contains("trip") || key.contains("vacation") -> TravelVisual
            key.contains("personal") || key.contains("salon") || key.contains("spa") || key.contains("groom") -> PersonalCareVisual
            key.contains("gift") || key.contains("donation") || key.contains("charity") -> GiftsVisual
            key.contains("income") || key.contains("salary") || key.contains("freelance") || key.contains("interest") -> IncomeVisual
            key.contains("refund") || key.contains("cashback") || key.contains("reimburse") -> RefundsVisual
            key.contains("transfer") || key.contains("self") || key.contains("swap") -> TransfersVisual
            else -> OtherVisual
        }
    }

    fun getIcon(iconName: String?): ImageVector {
        return getVisual(iconName).icon
    }

    fun parseColor(colorHex: String?, defaultColor: Color = Color(0xFF3B82F6)): Color {
        if (colorHex.isNullOrBlank()) return defaultColor
        return try {
            val cleanHex = if (colorHex.startsWith("#")) colorHex else "#$colorHex"
            Color(android.graphics.Color.parseColor(cleanHex))
        } catch (_: Exception) {
            defaultColor
        }
    }
}

/**
 * Cute, rounded category badge adhering to Peachy Cozy design system.
 * Renders 100% offline using vector icons with soft pastel background.
 */
@Composable
fun CategoryBadge(
    categoryName: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    showLabel: Boolean = false,
    labelColor: Color = AppTheme.colors.textPrimary
) {
    val visual = CategoryIconHelper.getVisual(categoryName)
    val iconSize = size * 0.55f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (AppTheme.isDark) {
                        visual.tintColor.copy(alpha = 0.2f)
                    } else {
                        visual.backgroundColor
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = visual.icon,
                contentDescription = visual.displayName,
                tint = visual.tintColor,
                modifier = Modifier.size(iconSize)
            )
        }

        if (showLabel) {
            Text(
                text = visual.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = labelColor,
                fontSize = 13.sp
            )
        }
    }
}
