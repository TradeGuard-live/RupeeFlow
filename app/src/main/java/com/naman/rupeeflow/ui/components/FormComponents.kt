package com.naman.rupeeflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.domain.model.Category
import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.SplitMethod
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
            // Allow only digits and at most one dot with up to 2 decimals
            val filtered = input.filter { it.isDigit() || it == '.' }
            val parts = filtered.split(".")
            if (parts.size <= 2 && (parts.size < 2 || parts[1].length <= 2)) {
                onValueChange(filtered)
            }
        },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, color = TextSecondary) },
        placeholder = { Text(placeholder, color = TextMuted) },
        leadingIcon = {
            Text(
                text = "₹",
                style = MaterialTheme.typography.titleLarge,
                color = ElectricBlue,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp)
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineMedium.copy(color = TextPrimary),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ElectricBlue,
            unfocusedBorderColor = GlassBorder,
            focusedContainerColor = CharcoalCard,
            unfocusedContainerColor = CharcoalCard
        ),
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
                color = if (isSelected) catColor.copy(alpha = 0.25f) else CharcoalCard,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) catColor else GlassBorder
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
                        tint = if (isSelected) catColor else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = cat.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) TextPrimary else TextSecondary
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
                color = if (isSelected) ElectricBlue.copy(alpha = 0.2f) else CharcoalCard,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) ElectricBlue else GlassBorder
                ),
                modifier = Modifier.clickable { onSelect(method) }
            ) {
                Text(
                    text = method.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) ElectricBlueLight else TextSecondary,
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
    val types = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CharcoalCard)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        types.forEach { type ->
            val isSelected = selectedType == type
            val activeColor = when (type) {
                TransactionType.EXPENSE -> CoralRed
                TransactionType.INCOME -> EmeraldMint
                TransactionType.TRANSFER -> ElectricBlue
                else -> ElectricBlue
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) activeColor.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { onSelect(type) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = type.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) activeColor else TextSecondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
