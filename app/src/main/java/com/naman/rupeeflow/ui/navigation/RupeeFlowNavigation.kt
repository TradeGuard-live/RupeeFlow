package com.naman.rupeeflow.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.ui.theme.*

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    object Overview : Screen("overview", "Overview", Icons.Default.Dashboard)
    object Transactions : Screen("transactions", "Transactions", Icons.Default.ReceiptLong)
    object Friends : Screen("friends", "Friends", Icons.Default.Group)
    object Analytics : Screen("analytics", "Analytics", Icons.Default.BarChart)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)

    object FriendDetail : Screen("friend_detail/{friendId}", "Friend Detail") {
        fun createRoute(friendId: Long) = "friend_detail/$friendId"
    }

    object ExpenseGroups : Screen("expense_groups", "Expense Groups")

    object ExpenseGroupDetail : Screen("expense_group_detail/{groupId}", "Group Detail") {
        fun createRoute(groupId: Long) = "expense_group_detail/$groupId"
    }

    object ReportGenerator : Screen("report_generator?groupId={groupId}", "Expense Report") {
        fun createRoute(groupId: Long? = null): String {
            return if (groupId != null && groupId > 0L) {
                "report_generator?groupId=$groupId"
            } else {
                "report_generator"
            }
        }
    }

    object PendingTransactions : Screen("pending_transactions", "Pending Review")

    object AddEditTransaction : Screen(
        "add_edit_transaction?transactionId={transactionId}&pendingId={pendingId}",
        "Transaction Form"
    ) {
        fun createRoute(transactionId: Long? = null, pendingId: Long? = null): String {
            val tParam = transactionId ?: -1L
            val pParam = pendingId ?: -1L
            return "add_edit_transaction?transactionId=$tParam&pendingId=$pParam"
        }
    }
}

val BottomNavItems = listOf(
    Screen.Overview,
    Screen.Transactions,
    Screen.Friends,
    Screen.Analytics,
    Screen.Settings
)

@Composable
fun RupeeFlowBottomBar(
    currentRoute: String?,
    pendingCount: Int = 0,
    onNavigate: (Screen) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppTheme.colors.surface,
        tonalElevation = if (AppTheme.isDark) 8.dp else 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.divider)
    ) {
        NavigationBar(
            containerColor = AppTheme.colors.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.navigationBarsPadding()
        ) {
            BottomNavItems.forEach { screen ->
                val selected = currentRoute == screen.route
                val iconColor = if (selected) AppTheme.colors.primaryAccent else AppTheme.colors.textSecondary
                val textColor = if (selected) AppTheme.colors.primaryAccent else AppTheme.colors.textSecondary

                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(screen) },
                    icon = {
                        Box {
                            Icon(
                                imageVector = screen.icon ?: Icons.Default.Dashboard,
                                contentDescription = screen.title,
                                tint = iconColor,
                                modifier = Modifier.size(24.dp)
                            )
                            // If screen is Overview or Transactions and there are pending items, show small indicator
                            if (screen == Screen.Overview && pendingCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(AmberGold)
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                    },
                    label = {
                        Text(
                            text = screen.title,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = textColor,
                            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = if (AppTheme.isDark) CharcoalCard else AppTheme.colors.primaryAccentContainer,
                        selectedIconColor = AppTheme.colors.primaryAccent,
                        unselectedIconColor = AppTheme.colors.textSecondary
                    )
                )
            }
        }
    }
}
