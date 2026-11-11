package com.naman.rupeeflow.ui.components

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naman.rupeeflow.domain.model.LocationSource
import com.naman.rupeeflow.domain.model.TransactionLocation
import com.naman.rupeeflow.location.LocationIntentHelper
import com.naman.rupeeflow.location.LocationParser
import com.naman.rupeeflow.location.OfflineLocationManager
import com.naman.rupeeflow.ui.theme.AmberGold
import com.naman.rupeeflow.ui.theme.AppTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun TransactionLocationPicker(
    location: TransactionLocation?,
    onLocationChanged: (TransactionLocation?) -> Unit,
    merchantSuggestion: TransactionLocation? = null,
    merchantName: String? = null,
    onApplyMerchantSuggestion: () -> Unit = {},
    onDismissMerchantSuggestion: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isLocating by remember { mutableStateOf(false) }
    var showCoordinatesDialog by remember { mutableStateOf(false) }
    var showMapsLinkDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            coroutineScope.launch {
                isLocating = true
                val freshLoc = OfflineLocationManager.requestFreshLocation(context)
                isLocating = false
                if (freshLoc != null) {
                    onLocationChanged(freshLoc)
                } else {
                    Toast.makeText(context, "Could not acquire current GPS fix", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.cardBackground)
            .border(1.dp, AppTheme.colors.cardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = AppTheme.colors.primaryAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Payment Location (Optional)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.textPrimary
                )
            }

            if (location != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppTheme.colors.accentContainer
                ) {
                    Text(
                        text = "Attached",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.primaryAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Merchant Memory Suggestion Banner
        if (location == null && merchantSuggestion != null && !merchantName.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AmberGold.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Found saved location for \"$merchantName\"",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        IconButton(
                            onClick = onDismissMerchantSuggestion,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = AppTheme.colors.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.4f, %.4f", merchantSuggestion.latitude, merchantSuggestion.longitude)} • Previously used",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onApplyMerchantSuggestion,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Use Saved Location",
                                color = Color.Black,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        TextButton(
                            onClick = onDismissMerchantSuggestion,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "Skip",
                                color = AppTheme.colors.textSecondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        // Active Attached Location Details
        if (location != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AppTheme.colors.surfaceSecondary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = String.format(java.util.Locale.US, "%.5f, %.5f", location.latitude, location.longitude),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val accuracyStr = if (location.accuracyMeters != null) " (±${location.accuracyMeters.roundToInt()}m)" else ""
                            val sourceLabel = when (location.locationSource) {
                                LocationSource.DEVICE_CURRENT -> "Current GPS"
                                LocationSource.DEVICE_AT_DETECTION -> "At Detection"
                                LocationSource.MANUAL_COORDINATES -> "Coordinates"
                                LocationSource.MANUAL_MAPS_LINK -> "Maps Link"
                                LocationSource.HISTORICAL_LOCATION_SUGGESTION -> "Saved Merchant"
                                LocationSource.MANUAL_MAP_SELECTION -> "Map Pin"
                            }
                            Text(
                                text = "Source: $sourceLabel$accuracyStr",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.colors.textSecondary,
                                fontSize = 11.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    LocationIntentHelper.openLocation(
                                        context = context,
                                        latitude = location.latitude,
                                        longitude = location.longitude,
                                        label = location.locationLabel
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
                            ) {
                                Icon(Icons.Default.Map, contentDescription = "Map", modifier = Modifier.size(14.dp), tint = AppTheme.colors.primaryAccent)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Map", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.primaryAccent)
                            }

                            IconButton(
                                onClick = { onLocationChanged(null) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Remove location",
                                    tint = AppTheme.colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Action Buttons to Add Location
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Current Location Button
                OutlinedButton(
                    onClick = {
                        if (OfflineLocationManager.hasPermission(context)) {
                            coroutineScope.launch {
                                isLocating = true
                                val loc = OfflineLocationManager.requestFreshLocation(context)
                                isLocating = false
                                if (loc != null) {
                                    onLocationChanged(loc)
                                } else {
                                    Toast.makeText(context, "GPS location unavailable offline", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                    enabled = !isLocating
                ) {
                    if (isLocating) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AppTheme.colors.primaryAccent)
                    } else {
                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(14.dp), tint = AppTheme.colors.primaryAccent)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Current GPS", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textPrimary, fontSize = 11.sp)
                    }
                }

                // Coordinates Button
                OutlinedButton(
                    onClick = { showCoordinatesDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
                ) {
                    Icon(Icons.Default.PinDrop, contentDescription = null, modifier = Modifier.size(14.dp), tint = AppTheme.colors.primaryAccent)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Coordinates", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textPrimary, fontSize = 11.sp)
                }

                // Paste Maps Link Button
                OutlinedButton(
                    onClick = { showMapsLinkDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, AppTheme.colors.cardBorder)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp), tint = AppTheme.colors.primaryAccent)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Maps Link", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textPrimary, fontSize = 11.sp)
                }
            }
        }
    }

    // Coordinates Input Dialog
    if (showCoordinatesDialog) {
        var inputLat by remember { mutableStateOf("") }
        var inputLon by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showCoordinatesDialog = false },
            title = { Text("Enter Coordinates", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter valid latitude (-90 to 90) and longitude (-180 to 180):",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary
                    )
                    OutlinedTextField(
                        value = inputLat,
                        onValueChange = { inputLat = it; errorMsg = null },
                        label = { Text("Latitude (e.g. 28.6139)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = rupeeFlowTextFieldColors(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = inputLon,
                        onValueChange = { inputLon = it; errorMsg = null },
                        label = { Text("Longitude (e.g. 77.2090)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = rupeeFlowTextFieldColors(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    if (errorMsg != null) {
                        Text(errorMsg!!, color = Color(0xFFEF4444), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = LocationParser.parseCoordinates("$inputLat, $inputLon")
                        if (parsed != null) {
                            onLocationChanged(
                                TransactionLocation(
                                    latitude = parsed.first,
                                    longitude = parsed.second,
                                    locationCapturedAt = System.currentTimeMillis(),
                                    locationSource = LocationSource.MANUAL_COORDINATES,
                                    googleMapsUrl = LocationIntentHelper.buildGoogleMapsWebUrl(parsed.first, parsed.second)
                                )
                            )
                            showCoordinatesDialog = false
                        } else {
                            errorMsg = "Invalid coordinates. Please verify range."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primaryAccent)
                ) {
                    Text("Attach Location")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCoordinatesDialog = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Maps Link Input Dialog
    if (showMapsLinkDialog) {
        var inputUrl by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showMapsLinkDialog = false },
            title = { Text("Paste Google Maps Link", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste a Google Maps URL containing coordinates (@lat,lng or q=lat,lng):",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary
                    )
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it; errorMsg = null },
                        label = { Text("https://maps.google.com/...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = rupeeFlowTextFieldColors(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    if (errorMsg != null) {
                        Text(errorMsg!!, color = Color(0xFFEF4444), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = LocationParser.parseGoogleMapsUrl(inputUrl.trim())
                        if (parsed != null) {
                            onLocationChanged(
                                TransactionLocation(
                                    latitude = parsed.first,
                                    longitude = parsed.second,
                                    locationCapturedAt = System.currentTimeMillis(),
                                    locationSource = LocationSource.MANUAL_MAPS_LINK,
                                    googleMapsUrl = inputUrl.trim()
                                )
                            )
                            showMapsLinkDialog = false
                        } else {
                            errorMsg = "Could not parse coordinates from this URL."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primaryAccent)
                ) {
                    Text("Attach Location")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMapsLinkDialog = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

