package com.robloxhihi.recallkeeper.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.robloxhihi.recallkeeper.R
import com.robloxhihi.recallkeeper.core.RecallDetector
import com.robloxhihi.recallkeeper.data.MessageEntity
import com.robloxhihi.recallkeeper.data.Status
import com.robloxhihi.recallkeeper.data.Store
import kotlinx.coroutines.launch

/**
 * He thong bind service nay va goi callback DUNG LUC co notification.
 * Khong foreground service, khong alarm, khong polling, khong wakelock.
 * => Man hinh tat / Doze van bat duoc; may TAT NGUON thi khong gi chay duoc (khong app nao lam duoc).
 */
class RecallListenerService : NotificationListenerService() {

    private val clickedKeys = LinkedHashSet<String>()

    override fun onListenerConnected() {
        Store.listenerConnectedAt = System.currentTimeMillis()
        if (Store.ping) {
            showPing(
                1,
                "Recall Keeper đang hoạt động",
                "Đã kết nối bộ lắng nghe thông báo. App chỉ chạy khi có tin tới."
            )
        }
    }

    override fun onListenerDisconnected() {
        Store.listenerConnectedAt = 0L
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (Store.paused) return
        if (sbn.packageName == packageName) return
        if (sbn.packageName !in Store.watched()) return
        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        Store.io.launch { runCatching { handlePosted(sbn) } }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int
    ) {
        if (sbn.packageName !in Store.watched()) return

        if (reason == NotificationListenerService.REASON_CLICK) {
            synchronized(clickedKeys) {
                clickedKeys.add(sbn.key)
                while (clickedKeys.size > 200) clickedKeys.remove(clickedKeys.first())
            }
            return
        }

        // Chi dung APP_CANCEL don le. APP_CANCEL_ALL qua mo ho (mo app cung xoa het) -> bo qua.
        if (reason != NotificationListenerService.REASON_APP_CANCEL) return
        val wasClicked = synchronized(clickedKeys) { clickedKeys.remove(sbn.key) }
        if (wasClicked) return
        Store.io.launch { runCatching { markSuspected(sbn.key) } }
    }

    // ---------------- posted ----------------

    @Suppress("DEPRECATION")
    private suspend fun handlePosted(sbn: StatusBarNotification) {
        val dao = Store.db.dao()
        val extras = sbn.notification.extras
        val now = System.currentTimeMillis()
        val pkg = sbn.packageName
        val label = Store.label(pkg)

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val convTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?.toString()?.trim().orEmpty().ifBlank { title }
        val convKey = pkg + "#" + (sbn.notification.shortcutId ?: convKeyFallback(convTitle, title))

        // (1) Duong chac chan cao: notification doi thanh cau "da thu hoi ..."
        val unsend = RecallDetector.unsendCount("$title $body".trim())
        if (unsend != null) {
            val evidence = "Notification đổi thành: \"$body\""
            var targets = dao.latestNormalInConv(convKey, unsend)
            if (targets.isEmpty()) {
                targets = dao.latestNormalGlobal(now - 15 * 60_000L, unsend)
            }
            if (targets.isEmpty()) {
                // Khong bia noi dung. Ghi lai su kien + noi ro khong tim thay tin goc.
                dao.insert(
                    row(
                        pkg, label, convKey, convTitle, title,
                        "[Có tin bị thu hồi nhưng app chưa lưu được tin gốc]",
                        sbn.postTime, now, sbn.key
                    ).copy(status = Status.CONFIRMED, evidence = evidence, statusAt = now)
                )
            } else {
                targets.forEach { dao.setStatus(it.id, Status.CONFIRMED, evidence, now) }
            }
            Store.lastCaptureAt = now
            maybePrune(now)
            return
        }

        // (2) Tin thuong. Uu tien MessagingStyle (dung nguoi gui + gio tung tin).
        val rows = mutableListOf<MessageEntity>()
        val array = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (array != null && array.isNotEmpty()) {
            for (parcel in array) {
                val bundle = parcel as? Bundle ?: continue
                val text = bundle.getCharSequence("text")?.toString()?.trim().orEmpty()
                if (text.isEmpty()) continue
                val sender = bundle.getCharSequence("sender")?.toString()?.trim().orEmpty()
                    .ifBlank { title }
                val time = bundle.getLong("time", sbn.postTime)
                rows.add(row(pkg, label, convKey, convTitle, sender, text, time, now, sbn.key))
            }
        }
        if (rows.isEmpty()) {
            val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            val text = if (!big.isNullOrBlank()) big else body
            if (text.isNotBlank()) {
                rows.add(row(pkg, label, convKey, convTitle, title, text, sbn.postTime, now, sbn.key))
            }
        }
        if (rows.isEmpty()) return

        var inserted = 0
        for (entity in rows) {
            val existing = dao.byHash(entity.contentHash)
            if (existing != null) {
                // Notification duoc post lai (tin moi cung hoi thoai) -> cap nhat key de theo vet.
                dao.rebindNotifKey(existing.id, sbn.key)
                continue
            }
            dao.insert(entity)
            inserted++
        }

        if (inserted > 0) {
            Store.lastCaptureAt = now
            if (Store.ping && !Store.firstPingDone) {
                Store.firstPingDone = true
                showPing(
                    2,
                    "Đã bắt tin nhắn đầu tiên",
                    "$label: app hoạt động bình thường. Vào app để xem danh sách."
                )
            }
        }
        maybePrune(now)
    }

    // ---------------- removed ----------------

    private suspend fun markSuspected(notifKey: String) {
        val dao = Store.db.dao()
        val entity = dao.byNotifKey(notifKey) ?: return
        if (entity.status != Status.NORMAL) return
        // Co notification moi hon cung hoi thoai => bi thay the, khong phai thu hoi.
        if (dao.countNewerInConv(entity.convKey, entity.capturedAt) > 0) return
        dao.setStatus(
            entity.id,
            Status.SUSPECTED,
            "Notification bị chính app xoá (REASON_APP_CANCEL), không do người dùng bấm hay gạt tay. " +
                "Cũng có thể chỉ là do bạn đã đọc trong app — vì vậy đây là NGHI, không phải xác nhận.",
            System.currentTimeMillis()
        )
    }

    // ---------------- helpers ----------------

    private fun convKeyFallback(convTitle: String, title: String): String =
        convTitle.ifBlank { title }.ifBlank { "unknown" }

    private fun row(
        pkg: String,
        label: String,
        convKey: String,
        convTitle: String,
        sender: String,
        text: String,
        postedAt: Long,
        capturedAt: Long,
        notifKey: String
    ): MessageEntity {
        val hash = Store.sha256("$convKey|$sender|$text|$postedAt")
        return MessageEntity(
            pkg = pkg,
            appLabel = label,
            convKey = convKey,
            convTitle = convTitle,
            sender = sender.ifBlank { "(không rõ người gửi)" },
            text = text,
            postedAt = postedAt,
            capturedAt = capturedAt,
            notifKey = notifKey,
            contentHash = hash
        )
    }

    private suspend fun maybePrune(now: Long) {
        if (now - Store.lastPruneAt < 60 * 60_000L) return
        Store.lastPruneAt = now
        Store.db.dao().prune(now - Store.retentionDays * 86_400_000L)
    }

    private fun showPing(id: Int, title: String, text: String) {
        runCatching {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "rk_status"
            if (manager.getNotificationChannel(channelId) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(channelId, "Trạng thái app", NotificationManager.IMPORTANCE_LOW)
                )
            }
            val compat = NotificationManagerCompat.from(this)
            if (!compat.areNotificationsEnabled()) return
            compat.notify(
                id,
                NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setAutoCancel(true)
                    .build()
            )
        }
    }
}
