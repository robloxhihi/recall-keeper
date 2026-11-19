package com.robloxhihi.recallkeeper.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Ba trang thai hien thi. Khong gop "nghi" vao "xac nhan". */
object Status {
    const val NORMAL = 0
    const val SUSPECTED = 1
    const val CONFIRMED = 2
}

@Entity(
    tableName = "messages",
    indices = [Index("convKey"), Index("notifKey"), Index("capturedAt"), Index("contentHash")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pkg: String,
    val appLabel: String,
    val convKey: String,
    val convTitle: String,
    val sender: String,
    val text: String,
    val postedAt: Long,
    val capturedAt: Long,
    val notifKey: String,
    val contentHash: String,
    val status: Int = Status.NORMAL,
    val evidence: String = "",
    val statusAt: Long = 0
)
