package com.robloxhihi.recallkeeper.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robloxhihi.recallkeeper.data.MessageEntity
import com.robloxhihi.recallkeeper.data.Status
import com.robloxhihi.recallkeeper.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun listenerEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)

private fun clock(millis: Long): String =
    if (millis <= 0L) "—" else SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(millis))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { Store.db.dao() }
    val all by remember { dao.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())

    var enabled by remember { mutableStateOf(listenerEnabled(context)) }
    var paused by remember { mutableStateOf(Store.paused) }
    var ping by remember { mutableStateOf(Store.ping) }
    var retention by remember { mutableStateOf(Store.retentionDays) }
    var filter by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var confirmWipe by remember { mutableStateOf(false) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = listenerEnabled(context)
                paused = Store.paused
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val array = JSONArray()
                    dao.all().forEach { m ->
                        array.put(
                            JSONObject()
                                .put("app", m.appLabel)
                                .put("conversation", m.convTitle)
                                .put("sender", m.sender)
                                .put("text", m.text)
                                .put("postedAt", m.postedAt)
                                .put("capturedAt", m.capturedAt)
                                .put("status", statusName(m.status))
                                .put("evidence", m.evidence)
                        )
                    }
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(array.toString(2).toByteArray(Charsets.UTF_8))
                    }
                }
            }
        }
    }

    val shown = all.filter { m ->
        val byFilter = when (filter) {
            1 -> m.status == Status.CONFIRMED
            2 -> m.status == Status.SUSPECTED
            3 -> m.status == Status.NORMAL
            else -> true
        }
        val byQuery = query.isBlank() ||
            m.text.contains(query, true) ||
            m.sender.contains(query, true) ||
            m.convTitle.contains(query, true)
        byFilter && byQuery
    }

    val confirmed = all.count { it.status == Status.CONFIRMED }
    val suspected = all.count { it.status == Status.SUSPECTED }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Recall Keeper", fontWeight = FontWeight.SemiBold) }) }
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (enabled) "Đang hoạt động" else "Chưa cấp quyền",
                        fontWeight = FontWeight.SemiBold,
                        color = if (enabled) MaterialTheme.colorScheme.primary else ConfirmFg
                    )
                    Text(
                        "Đã lưu ${all.size} tin · xác nhận thu hồi $confirmed · nghi $suspected",
                        fontSize = 13.sp
                    )
                    Text("Lần bắt gần nhất: ${clock(Store.lastCaptureAt)}", fontSize = 13.sp)
                    if (!enabled) {
                        Button(onClick = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                        }) { Text("Cấp quyền truy cập thông báo") }
                    }
                }
            }

            Text(
                "App chỉ đọc thông báo đã tới máy bạn và lưu tại máy. Không có quyền INTERNET nên không thể gửi đi đâu. " +
                    "Không xem được tin chưa gửi, và không cứu được tin nếu máy tắt nguồn hoặc hội thoại đang tắt thông báo.",
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    paused = !paused
                    Store.paused = paused
                }) { Text(if (paused) "Tiếp tục ghi" else "Tạm dừng") }
                OutlinedButton(onClick = {
                    ping = !ping
                    Store.ping = ping
                }) { Text(if (ping) "Tắt báo trạng thái" else "Bật báo trạng thái") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    exportLauncher.launch("recall-keeper-${System.currentTimeMillis()}.json")
                }) { Text("Xuất JSON") }
                OutlinedButton(onClick = { confirmWipe = true }) { Text("Xoá hết") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Giữ tin:", fontSize = 13.sp)
                listOf(7, 30, 90).forEach { days ->
                    FilterChip(
                        selected = retention == days,
                        onClick = {
                            retention = days
                            Store.retentionDays = days
                        },
                        label = { Text("$days ngày", fontSize = 12.sp) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Tất cả", "Đã thu hồi", "Nghi", "Thường").forEachIndexed { index, label ->
                    FilterChip(
                        selected = filter == index,
                        onClick = { filter = index },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Tìm người gửi hoặc nội dung") }
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shown, key = { it.id }) { m ->
                    MessageCard(
                        m = m,
                        onDelete = { scope.launch(Dispatchers.IO) { dao.delete(m.id) } },
                        onRelabel = { target ->
                            scope.launch(Dispatchers.IO) {
                                dao.setStatus(
                                    m.id, target,
                                    "Người dùng tự đánh dấu lại.",
                                    System.currentTimeMillis()
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Xoá toàn bộ tin đã lưu?") },
            text = { Text("Không hoàn tác được. Tin đã thu hồi cũng mất.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    scope.launch(Dispatchers.IO) { dao.deleteAll() }
                }) { Text("Xoá hết") }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("Thôi") }
            }
        )
    }
}

private fun statusName(status: Int): String = when (status) {
    Status.CONFIRMED -> "confirmed_recalled"
    Status.SUSPECTED -> "suspected_recalled"
    else -> "normal"
}

@Composable
private fun StatusBadge(status: Int) {
    val label: String
    val bg: androidx.compose.ui.graphics.Color
    val fg: androidx.compose.ui.graphics.Color
    when (status) {
        Status.CONFIRMED -> { label = "ĐÃ THU HỒI"; bg = ConfirmBg; fg = ConfirmFg }
        Status.SUSPECTED -> { label = "NGHI THU HỒI"; bg = SuspectBg; fg = SuspectFg }
        else -> { label = "TIN THƯỜNG"; bg = NormalBg; fg = NormalFg }
    }
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Text(
            label,
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MessageCard(m: MessageEntity, onDelete: () -> Unit, onRelabel: (Int) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(m.sender, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                StatusBadge(m.status)
            }
            Text(m.text, fontSize = 14.sp)
            Text(
                "${m.appLabel} · ${m.convTitle} · ${clock(m.postedAt)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (m.status != Status.NORMAL && m.evidence.isNotBlank()) {
                Text(
                    m.evidence,
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (m.status == Status.NORMAL) {
                    TextButton(onClick = { onRelabel(Status.CONFIRMED) }) {
                        Text("Đánh dấu thu hồi", fontSize = 12.sp)
                    }
                } else {
                    TextButton(onClick = { onRelabel(Status.NORMAL) }) {
                        Text("Đây là tin thường", fontSize = 12.sp)
                    }
                }
                TextButton(onClick = onDelete) { Text("Xoá", fontSize = 12.sp) }
            }
        }
    }
}
