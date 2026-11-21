package com.cookiepatrol.warn

import android.os.Bundle
import androidx.core.os.BundleCompat
import com.cookiepatrol.detect.SignalCategory

internal val SignalCategory.warningLabel: String
    get() = when (this) {
        SignalCategory.LINK -> "링크"
        SignalCategory.PHONE -> "전화번호"
        SignalCategory.BANK_ACCOUNT -> "계좌번호"
        SignalCategory.MESSAGE -> "그 외 문구"
    }

internal data class WarningFinding(
    val category: SignalCategory,
    val title: String,
    val reason: String,
    val points: Int? = null,
) {
    val label: String get() = points?.let { "$title +${it}점" } ?: title
}

internal data class WarningAssessment(val score: Int, val findings: List<WarningFinding>) {
    fun toBundle() = Bundle().apply {
        putInt("score", score)
        putParcelableArrayList("findings", ArrayList(findings.map { finding ->
            Bundle().apply {
                putString("category", finding.category.name)
                putString("title", finding.title)
                putString("reason", finding.reason)
                finding.points?.let { putInt("points", it) }
            }
        }))
    }

    companion object {
        fun fromBundle(bundle: Bundle?): WarningAssessment? {
            if (bundle == null) return null
            val findings = BundleCompat.getParcelableArrayList(bundle, "findings", Bundle::class.java)
                .orEmpty().mapNotNull { finding ->
                    val category = SignalCategory.entries.firstOrNull { it.name == finding.getString("category") }
                        ?: return@mapNotNull null
                    val title = finding.getString("title") ?: return@mapNotNull null
                    val reason = finding.getString("reason") ?: return@mapNotNull null
                    WarningFinding(category, title, reason, finding.getInt("points").takeIf { finding.containsKey("points") })
                }
            return WarningAssessment(bundle.getInt("score"), findings)
        }
    }
}
