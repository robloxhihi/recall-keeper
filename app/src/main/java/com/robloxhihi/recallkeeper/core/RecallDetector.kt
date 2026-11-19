package com.robloxhihi.recallkeeper.core

/**
 * Phat hien "tin da bi thu hoi" tu NOI DUNG notification.
 *
 * Day la duong duy nhat cho do chac chan cao: Messenger thay noi dung notification
 * thanh mot cau thong bao ("X da thu hoi mot tin nhan" / "X unsent a message").
 * Khi thay cau do, ta danh dau tin goc da luu truoc do.
 *
 * Luu y that: day la so khop chuoi, phu thuoc ngon ngu Messenger dang dung.
 * Neu Meta doi wording, can them mau moi vao PATTERNS.
 */
object RecallDetector {

    private val PATTERNS = listOf(
        // vi
        Regex("""thu\s+hồi\s+(?:một|1|\d+)?\s*tin\s+nhắn""", RegexOption.IGNORE_CASE),
        Regex("""tin\s+nhắn\s+đã\s+(?:bị\s+)?(?:thu\s+hồi|gỡ|xoá)""", RegexOption.IGNORE_CASE),
        Regex("""đã\s+gỡ\s+(?:một|1|\d+)?\s*tin\s+nhắn""", RegexOption.IGNORE_CASE),
        // en
        Regex("""unsent\s+(?:a|an|\d+)?\s*messages?""", RegexOption.IGNORE_CASE),
        Regex("""unsend\s+(?:a|an|\d+)?\s*messages?""", RegexOption.IGNORE_CASE),
        Regex("""messages?\s+(?:was|were)\s+unsent""", RegexOption.IGNORE_CASE),
        Regex("""removed\s+(?:a|an|\d+)?\s*messages?""", RegexOption.IGNORE_CASE)
    )

    private val NUMBER = Regex("""\b(\d{1,3})\b""")

    /**
     * @return so tin bi thu hoi neu notification nay la thong bao thu hoi, null neu khong phai.
     *
     * Chong nham: nguoi ta chat "may thu hoi tin nhan di" cung khop chuoi. Nen chi coi la
     * thong bao thu hoi khi cau ngan (<= 140 ky tu), mot dong, va cum khop nam ngay dau cau
     * (ngay sau ten nguoi gui). Tin chat that su thuong dai hon hoac khong o vi tri do.
     */
    fun unsendCount(text: String): Int? {
        val clean = text.trim()
        if (clean.isBlank() || clean.length > 140 || clean.contains('\n')) return null
        val hit = PATTERNS.firstOrNull { it.containsMatchIn(clean) } ?: return null
        val found = hit.find(clean) ?: return null
        if (found.range.first > 45) return null
        val n = NUMBER.find(found.value)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        return n.coerceIn(1, 20)
    }

    fun isUnsendNotice(text: String): Boolean = unsendCount(text) != null
}
