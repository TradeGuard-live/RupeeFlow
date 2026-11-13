package com.naman.rupeeflow.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.naman.rupeeflow.ui.AppViewModelProvider
import com.naman.rupeeflow.ui.screens.analytics.AnalyticsScreen
import com.naman.rupeeflow.ui.screens.analytics.AnalyticsViewModel
import com.naman.rupeeflow.ui.screens.friends.FriendDetailScreen
import com.naman.rupeeflow.ui.screens.friends.FriendsScreen
import com.naman.rupeeflow.ui.screens.friends.FriendsViewModel
import com.naman.rupeeflow.ui.screens.overview.OverviewScreen
import com.naman.rupeeflow.ui.screens.overview.OverviewViewModel
import com.naman.rupeeflow.ui.screens.pending.PendingTransactionsScreen
import com.naman.rupeeflow.ui.screens.pending.PendingTransactionsViewModel
import com.naman.rupeeflow.ui.screens.settings.SettingsScreen
import com.naman.rupeeflow.ui.screens.settings.SettingsViewModel
import com.naman.rupeeflow.ui.screens.transactions.TransactionsScreen
import com.naman.rupeeflow.ui.screens.transactions.TransactionsViewModel
import com.naman.rupeeflow.ui.theme.AppTheme
import com.naman.rupeeflow.ui.theme.MidnightDark

@Composable
fun RupeeFlowApp(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val overviewViewModel: OverviewViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val overviewState by overviewViewModel.uiState.collectAsState()
    val pendingCount = overviewState.pendingTransactions.size

    val showBottomBar = currentRoute in BottomNavItems.map { it.route }

    Scaffold(
        containerColor = AppTheme.colors.background,
        bottomBar = {
            if (showBottomBar) {
                RupeeFlowBottomBar(
                    currentRoute = currentRoute,
                    pendingCount = pendingCount,
                    onNavigate = { screen ->
                        if (currentRoute != screen.route) {
                            navController.navigate(screen.route) {
                                popUpTo(Screen.Overview.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Overview.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            // 1. Overview
            composable(Screen.Overview.route) {
                OverviewScreen(
                    viewModel = overviewViewModel,
                    onNavigateToTransactions = {
                        navController.navigate(Screen.Transactions.route)
                    },
                    onNavigateToFriends = {
                        navController.navigate(Screen.Friends.route)
                    },
                    onNavigateToFriendDetail = { friendId ->
                        navController.navigate(Screen.FriendDetail.createRoute(friendId))
                    },
                    onNavigateToPending = {
                        navController.navigate(Screen.PendingTransactions.route)
                    }
                )
            }

            // 2. Transactions
            composable(Screen.Transactions.route) {
                val transactionsViewModel: TransactionsViewModel = viewModel(factory = AppViewModelProvider.Factory)
                TransactionsScreen(
                    viewModel = transactionsViewModel,
                    onNavigateToPending = {
                        navController.navigate(Screen.PendingTransactions.route)
                    }
                )
            }

            // 3. Friends
            composable(Screen.Friends.route) {
                val friendsViewModel: FriendsViewModel = viewModel(factory = AppViewModelProvider.Factory)
                FriendsScreen(
                    viewModel = friendsViewModel,
                    onNavigateToFriendDetail = { friendId ->
                        navController.navigate(Screen.FriendDetail.createRoute(friendId))
                    }
                )
            }

            // Friend Detail
            composable(
                route = Screen.FriendDetail.route,
                arguments = listOf(navArgument("friendId") { type = NavType.LongType })
            ) { backStackEntry ->
                val friendId = backStackEntry.arguments?.getLong("friendId") ?: 0L
                val friendDetailViewModel = viewModel(
                    factory = AppViewModelProvider.friendDetailFactory(friendId)
                ) as com.naman.rupeeflow.ui.screens.friends.FriendDetailViewModel

                FriendDetailScreen(
                    viewModel = friendDetailViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 4. Analytics
            composable(Screen.Analytics.route) {
                val analyticsViewModel: AnalyticsViewModel = viewModel(factory = AppViewModelProvider.Factory)
                AnalyticsScreen(viewModel = analyticsViewModel)
            }

            // 5. Settings
            composable(Screen.Settings.route) {
                val settingsViewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateToPending = {
                        navController.navigate(Screen.PendingTransactions.route)
                    },
                    onNavigateToReportGenerator = {
                        navController.navigate(Screen.ReportGenerator.route)
                    },
                    onNavigateToExpenseGroups = {
                        navController.navigate(Screen.ExpenseGroups.route)
                    }
                )
            }

            // Expense Groups / Trips Management
            composable(Screen.ExpenseGroups.route) {
                val expenseGroupViewModel: com.naman.rupeeflow.ui.screens.groups.ExpenseGroupViewModel = viewModel(factory = AppViewModelProvider.Factory)
                com.naman.rupeeflow.ui.screens.groups.ExpenseGroupsScreen(
                    viewModel = expenseGroupViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToGroupDetail = { groupId ->
                        navController.navigate(Screen.ExpenseGroupDetail.createRoute(groupId))
                    }
                )
            }

            // Expense Group Detail
            composable(
                route = Screen.ExpenseGroupDetail.route,
                arguments = listOf(navArgument("groupId") { type = NavType.LongType })
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getLong("groupId") ?: 0L
                val expenseGroupViewModel: com.naman.rupeeflow.ui.screens.groups.ExpenseGroupViewModel = viewModel(factory = AppViewModelProvider.Factory)
                com.naman.rupeeflow.ui.screens.groups.ExpenseGroupDetailScreen(
                    groupId = groupId,
                    viewModel = expenseGroupViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // PDF Expense Report Generator
            composable(Screen.ReportGenerator.route) {
                val reportViewModel: com.naman.rupeeflow.ui.screens.report.ReportGeneratorViewModel = viewModel(factory = AppViewModelProvider.Factory)
                com.naman.rupeeflow.ui.screens.report.ReportGeneratorScreen(
                    viewModel = reportViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Pending Transactions Review
            composable(Screen.PendingTransactions.route) {
                val pendingViewModel: PendingTransactionsViewModel = viewModel(factory = AppViewModelProvider.Factory)
                PendingTransactionsScreen(
                    viewModel = pendingViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
