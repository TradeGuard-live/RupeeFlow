package com.naman.rupeeflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.naman.rupeeflow.domain.model.Category
import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.TransactionType
import com.naman.rupeeflow.ui.theme.*

@Composable
fun AmountInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Amount",
    placeholder: String = "0.00"
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val filtered = input.filter { it.isDigit() || it == '.' }
            val parts = filtered.split(".")
            if (parts.size <= 2 && (parts.size < 2 || parts[1].length <= 2)) {
                onValueChange(filtered)
            }
        },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, color = AppTheme.colors.textSecondary) },
        placeholder = { Text(placeholder, color = AppTheme.colors.textTertiary) },
        leadingIcon = {
            Text(
                text = "₹",
                style = MaterialTheme.typography.titleLarge,
                color = AppTheme.colors.primaryAccent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp)
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineMedium.copy(color = AppTheme.colors.textPrimary),
        colors = rupeeFlowTextFieldColors(),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun CategorySelectorRow(
    categories: List<Category>,
    selectedCategory: Category?,
    onSelect: (Category) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = AppTheme.isDark
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { cat ->
            val isSelected = selectedCategory?.id == cat.id
            val catColor = CategoryIconHelper.parseColor(cat.colorHex)
            val catIcon = CategoryIconHelper.getIcon(cat.iconName)

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) {
                    if (isDark) catColor.copy(alpha = 0.25f) else catColor.copy(alpha = 0.15f)
                } else {
                    if (isDark) CharcoalCard else AppTheme.colors.surfaceSecondary
                },
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) catColor else (if (isDark) GlassBorder else AppTheme.colors.cardBorder)
                ),
                modifier = Modifier.clickable { onSelect(cat) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = catIcon,
                        contentDescription = cat.name,
                        tint = if (isSelected) catColor else AppTheme.colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = cat.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) (if (isDark) TextPrimary else catColor) else AppTheme.colors.textSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun PaymentMethodSelectorRow(
    selectedMethod: PaymentMethod,
    onSelect: (PaymentMethod) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = AppTheme.isDark
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PaymentMethod.values().forEach { method ->
            val isSelected = selectedMethod == method
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) {
                    if (isDark) ElectricBlue.copy(alpha = 0.2f) else AppTheme.colors.primaryAccentContainer
                } else {
                    if (isDark) CharcoalCard else AppTheme.colors.surfaceSecondary
                },
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) AppTheme.colors.primaryAccent else (if (isDark) GlassBorder else AppTheme.colors.cardBorder)
                ),
                modifier = Modifier.clickable { onSelect(method) }
            ) {
                Text(
                    text = method.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) {
                        if (isDark) ElectricBlueLight else Color(0xFF1D4ED8)
                    } else {
                        AppTheme.colors.textSecondary
                    },
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun TransactionTypeSelectorRow(
    selectedType: TransactionType,
    onSelect: (TransactionType) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = AppTheme.isDark
    val types = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDark) CharcoalCard else AppTheme.colors.surfaceSecondary)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        types.forEach { type ->
            val isSelected = selectedType == type
            val activeColor = when (type) {
                TransactionType.EXPENSE -> if (isDark) CoralRed else Color(0xFFDC2626)
                TransactionType.INCOME -> if (isDark) EmeraldMint else Color(0xFF059669)
                TransactionType.TRANSFER -> if (isDark) ElectricBlue else Color(0xFF2563EB)
                else -> AppTheme.colors.primaryAccent
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) (if (isDark) activeColor.copy(alpha = 0.2f) else Color.White) else Color.Transparent)
                    .clickable { onSelect(type) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = type.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) activeColor else AppTheme.colors.textSecondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
