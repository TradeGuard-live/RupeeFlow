package com.naman.rupeeflow.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.naman.rupeeflow.data.local.ImageStorageManager
import com.naman.rupeeflow.domain.model.Friend
import com.naman.rupeeflow.domain.model.FriendGroup
import com.naman.rupeeflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Loads an ImageBitmap from a local file path with IO dispatcher caching.
 */
@Composable
fun rememberBitmapFromPath(path: String?): ImageBitmap? {
    if (path.isNullOrBlank()) return null
    return produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (file.exists() && file.length() > 0) {
                    val opts = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }.value
}

/**
 * Friend Avatar displaying profile picture if present, or initials fallback.
 */
@Composable
fun FriendAvatar(
    friend: Friend,
    modifier: Modifier = Modifier.size(44.dp),
    fallbackColor: Color = ElectricBlue
) {
    val bitmap = rememberBitmapFromPath(friend.profileImagePath)

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = friend.name,
            modifier = modifier
                .clip(CircleShape)
                .border(1.dp, fallbackColor.copy(alpha = 0.4f), CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        val initials = friend.name.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase()

        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(fallbackColor.copy(alpha = 0.2f))
                .border(1.dp, fallbackColor.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials.ifBlank { "F" },
                style = MaterialTheme.typography.titleSmall,
                color = fallbackColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Group Avatar displaying group image if present, or group icon / initials.
 */
@Composable
fun GroupAvatar(
    group: FriendGroup,
    modifier: Modifier = Modifier.size(44.dp),
    fallbackColor: Color = VibrantCyan
) {
    val bitmap = rememberBitmapFromPath(group.groupImagePath)

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = group.name,
            modifier = modifier
                .clip(CircleShape)
                .border(1.dp, fallbackColor.copy(alpha = 0.4f), CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        val initials = group.name.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase()

        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(fallbackColor.copy(alpha = 0.2f))
                .border(1.dp, fallbackColor.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials.ifBlank { "G" },
                style = MaterialTheme.typography.titleSmall,
                color = fallbackColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Row of mini avatars representing group members.
 */
@Composable
fun GroupMemberAvatarRow(
    memberIds: List<Long>,
    allFriends: List<Friend>,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 22.dp,
    maxAvatars: Int = 3
) {
    val members = remember(memberIds, allFriends) {
        memberIds.mapNotNull { mId -> allFriends.firstOrNull { it.id == mId } }
    }
    if (members.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy((-6).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        members.take(maxAvatars).forEach { member ->
            FriendAvatar(
                friend = member,
                modifier = Modifier
                    .size(avatarSize)
                    .border(1.dp, CharcoalCard, CircleShape)
            )
        }
        if (members.size > maxAvatars) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(CharcoalCardHover)
                    .border(1.dp, GlassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+${members.size - maxAvatars}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Reusable Bill / Receipt attachment component with camera & gallery launchers.
 */
@Composable
fun BillAttachmentSection(
    billImagePath: String?,
    onImageSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showFullscreenReceipt by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val path = ImageStorageManager.saveBillImage(context, uri)
                if (path != null) {
                    onImageSelected(path)
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            scope.launch {
                val path = ImageStorageManager.saveBillBitmap(context, bitmap)
                if (path != null) {
                    onImageSelected(path)
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ReceiptLong,
                    contentDescription = null,
                    tint = VibrantCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Receipt / Bill Photo",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            if (billImagePath != null) {
                TextButton(
                    onClick = {
                        ImageStorageManager.deleteImage(billImagePath)
                        onImageSelected(null)
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Remove", color = CoralRed, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (billImagePath != null) {
            val bitmap = rememberBitmapFromPath(billImagePath)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CharcoalCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showFullscreenReceipt = true }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Receipt Preview",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CharcoalSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = TextMuted)
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bill receipt attached",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tap to view full receipt",
                            style = MaterialTheme.typography.labelSmall,
                            color = VibrantCyan
                        )
                    }

                    // Action to replace
                    IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Replace", tint = TextSecondary)
                    }
                }
            }

            if (showFullscreenReceipt) {
                FullScreenReceiptDialog(
                    imagePath = billImagePath,
                    title = "Bill Receipt",
                    onDismiss = { showFullscreenReceipt = false }
                )
            }
        } else {
            // Pick options (Camera or Gallery)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { cameraLauncher.launch(null) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextPrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = VibrantCyan)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Take Photo", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextPrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElectricBlueLight)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload Bill", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * Fullscreen dialog with interactive zoom and pan for receipts.
 */
@Composable
fun FullScreenReceiptDialog(
    imagePath: String,
    title: String = "Bill Receipt",
    onDismiss: () -> Unit
) {
    val bitmap = rememberBitmapFromPath(imagePath)
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.95f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Zoomable image container
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Full Receipt",
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                ),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        CircularProgressIndicator(color = VibrantCyan)
                    }
                }

                // Reset zoom pill
                if (scale > 1f) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CharcoalCard.copy(alpha = 0.8f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp)
                            .clickable {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }
                    ) {
                        Text(
                            text = "Reset Zoom",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
