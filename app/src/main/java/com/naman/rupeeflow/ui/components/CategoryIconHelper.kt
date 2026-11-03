package com.naman.rupeeflow.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

object CategoryIconHelper {
    fun getIcon(iconName: String?): ImageVector {
        return when (iconName?.lowercase()) {
            "restaurant", "food" -> Icons.Default.Restaurant
            "shopping_cart", "groceries" -> Icons.Default.ShoppingCart
            "directions_car", "transportation", "commute" -> Icons.Default.DirectionsCar
            "shopping_bag", "shopping" -> Icons.Default.ShoppingBag
            "school", "education" -> Icons.Default.School
            "movie", "entertainment" -> Icons.Default.Movie
            "favorite", "health", "medical" -> Icons.Default.Favorite
            "home", "rent" -> Icons.Default.Home
            "bolt", "utilities" -> Icons.Default.Bolt
            "flight", "travel" -> Icons.Default.Flight
            "receipt_long", "bills" -> Icons.Default.ReceiptLong
            "subscriptions" -> Icons.Default.Subscriptions
            "person", "personal" -> Icons.Default.Person
            "group", "friends" -> Icons.Default.Group
            "payments", "upi", "cash" -> Icons.Default.Payments
            else -> Icons.Default.Category
        }
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
