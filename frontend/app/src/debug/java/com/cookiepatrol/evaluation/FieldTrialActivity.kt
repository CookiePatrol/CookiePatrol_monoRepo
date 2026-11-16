package com.cookiepatrol.evaluation

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cookiepatrol.BuildConfig
import com.cookiepatrol.detect.RiskAnalyzer
import com.cookiepatrol.monitor.NotificationMonitor
import com.cookiepatrol.monitor.ScreenMonitor
import com.cookiepatrol.monitor.MonitorStatus
import com.cookiepatrol.monitor.fingerprint
import com.cookiepatrol.detect.PrivacySettings
import com.cookiepatrol.warn.Warning
import com.cookiepatrol.ui.CookiePatrolTheme
import com.cookiepatrol.ui.PatrolButton
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import androidx.core.content.edit

/** Debug-only, manually observed field evidence. Never calls Detector or a remote provider. */
class FieldTrialActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("field-trial-v1", MODE_PRIVATE) }
    private val fixtureText by lazy { assets.open("holdout-cases.txt").bufferedReader().use { it.readText() } }
    private val cases by lazy { EvaluationCases.parse(fixtureText) }
    private var evidence = TrialEvidence(JSONObject(), JSONObject())
    private val observations get() = evidence.observations
    private val fixtureHash by lazy { fingerprint(fixtureText) }
    private var sessionId = ""
    private var startedAt = ""
    private var channel = "SMS"
    private var network = "OFFLINE"

    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = report().toString(2).toByteArray(Charsets.UTF_8)
                requireNotNull(contentResolver.openOutputStream(uri, "wt")).use { it.write(bytes) }
            }.onSuccess { toast("관찰 기록을 저장했어요.") }
                .onFailure { toast("저장하지 못했어요. 저장 위치를 다시 선택하세요.") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionId = prefs.getString("session", null) ?: UUID.randomUUID().toString()
        startedAt = prefs.getString("started", null) ?: Instant.now().toString()
        channel = prefs.getString("channel", "SMS") ?: "SMS"
        network = prefs.getString("network", "OFFLINE") ?: "OFFLINE"
        evidence = TrialEvidence(
            runCatching { JSONObject(prefs.getString("session_environment", "{}") ?: "{}") }.getOrDefault(JSONObject()),
            runCatching { JSONObject(prefs.getString("observations", "{}") ?: "{}") }.getOrDefault(JSONObject()),
        )
        persist()
        setContent {
            CookiePatrolTheme {
                var index by rememberSaveable { mutableIntStateOf(0) }
                var revision by remember { mutableIntStateOf(0) }
                var reset by remember { mutableStateOf(false) }
                val current = cases[index.coerceIn(cases.indices)]
                val field = remember(revision) { evidence.matrix(cases, currentEnvironment()) }
                val local = remember { ConfusionMatrix.local(cases) }
                Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("실험 기록 · 디버그 전용", fontSize = 30.sp)
                    Text("로컬 합성 문장 평가 ${cases.size}건", fontSize = 24.sp)
                    Text(matrixText(local), fontSize = 22.sp)
                    Text("위 수치는 합성 문장의 규칙 점검 결과예요. 개발용과 비슷한 교육 문구가 있어 독립된 정확도 검증으로 발표하면 안 돼요. 실제 문자 수신·서버 AI·피해 감소도 별도로 확인해야 해요.", fontSize = 22.sp)
                    Text("실기기 수동 관찰", fontSize = 26.sp)
                    Text("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\n채널 $channel · 네트워크 $network", fontSize = 22.sp)
                    Text(field?.let { matrixText(it) } ?: "다른 버전이나 설정에서 기록한 결과예요. 먼저 내보낸 뒤 새 세션을 시작해주세요. 이전 결과를 현재 규칙으로 다시 집계하지 않아요.", fontSize = 22.sp)
                    Text("미수신·판단 불가·미기록은 제외해요. 기록이 있으면 채널과 네트워크는 새 세션에서 바꿔주세요.", fontSize = 22.sp)
                    if (observations.length() == 0) {
                        Row {
                            listOf("SMS", "KAKAO", "OTHER").forEach { value ->
                                TextButton(onClick = { channel = value; persist(); revision++ }) { Text(value, fontSize = 20.sp) }
                            }
                        }
                        Row {
                            listOf("OFFLINE", "ONLINE").forEach { value ->
                                TextButton(onClick = { network = value; persist(); revision++ }) { Text(value, fontSize = 20.sp) }
                            }
                        }
                    }
                    Text("${index + 1} / ${cases.size} · ${current.id} · ${current.category}\n정답: ${if (current.phishing) "교육용 사칭 문장" else "정상 문장"}", fontSize = 24.sp)
                    Text(current.text, fontSize = 22.sp)
                    PatrolButton("문장 복사") {
                        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("교육용 시험 문장", current.text))
                        toast("다른 기기에서 이 문장을 문자나 카톡으로 보내세요.")
                    }
                    Text("이 화면은 경고를 직접 생성하지 않아요. 실제 수신 앱으로 문장을 받은 뒤 경고 알림을 확인하세요. 오프라인 검사는 5초, 온라인 검사는 다른 요청이 없는 상태에서 30초 이상 기다린 뒤 기록하세요. 추가 검사를 마치지 못했다는 안내는 ‘판단 불가’로 기록하세요. 수신하지 못했다면 미수신으로 표시하세요.", fontSize = 22.sp)
                    Text("현재 기록: ${outcomeLabel(observations.optJSONObject(current.id)?.optString("outcome"))}",
                        fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                    if (field != null) TrialOutcome.entries.forEach { outcome ->
                        PatrolButton(outcomeLabel(outcome.name)) {
                            if (!evidence.record(currentEnvironment(), current.id, outcome, Instant.now().toString(), permissions())) {
                                toast("실험 조건이 바뀌었어요. 기존 결과를 내보낸 뒤 새 세션을 시작하세요.")
                            }
                            persist(); revision++
                        }
                    }
                    Row {
                        TextButton(onClick = { index = (index - 1).coerceAtLeast(0) }) { Text("이전", fontSize = 22.sp) }
                        TextButton(onClick = { index = (index + 1).coerceAtMost(cases.lastIndex) }) { Text("다음", fontSize = 22.sp) }
                    }
                    PatrolButton("JSON 기록 내보내기") { export.launch("cookiepatrol-$sessionId.json") }
                    PatrolButton("새 세션 시작") { reset = true }
                    PatrolButton("홈으로") { finish() }
                    Text("저장 내용: 사례 ID·관찰 결과·시각·기종·권한 상태. 개인 문자 원문·전화번호·기기 식별번호는 저장하지 않아요. 내보낸 파일은 직접 관리하고 삭제하세요.", fontSize = 22.sp)
                }
                if (reset) AlertDialog(
                    onDismissRequest = { reset = false },
                    title = { Text("새 실험을 시작할까요?") },
                    text = { Text("현재 기록은 기기에서 지워집니다. 먼저 JSON으로 내보내세요.") },
                    confirmButton = { TextButton(onClick = {
                        evidence = TrialEvidence(currentEnvironment(), JSONObject()); sessionId = UUID.randomUUID().toString()
                        startedAt = Instant.now().toString(); persist(); index = 0; revision++; reset = false
                    }) { Text("기록 지우고 시작") } },
                    dismissButton = { TextButton(onClick = { reset = false }) { Text("취소") } },
                )
            }
        }
    }

    private fun persist() {
        prefs.edit {
            putString("session", sessionId); putString("started", startedAt)
            putString("channel", channel); putString("network", network)
            putString("observations", observations.toString())
            putString("session_environment", evidence.environment.toString())
        }
    }

    private fun permissions(): JSONObject {
        val nm = getSystemService(NotificationManager::class.java)
        val screen = ComponentName(this, ScreenMonitor::class.java)
        return JSONObject().put("warning_notifications", nm.areNotificationsEnabled())
            .put("warning_channel_enabled", Warning.canNotify(this))
            .put("notification_access", nm.isNotificationListenerAccessGranted(ComponentName(this, NotificationMonitor::class.java)))
            .put("all_files_access", Environment.isExternalStorageManager())
            .put("notification_service_connected", MonitorStatus.connected)
            .put("install_monitoring_ready", MonitorStatus.installMonitoringReady)
            .put("download_monitoring_ready", MonitorStatus.downloadMonitoringReady)
            .put("remote_url_checks_consent", PrivacySettings.remoteEnabled(this))
            .put("screen_access", Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')?.any { ComponentName.unflattenFromString(it) == screen } == true)
    }

    private fun matrixJson(matrix: ConfusionMatrix) = JSONObject()
        .put("tp", matrix.tp).put("fp", matrix.fp).put("tn", matrix.tn).put("fn", matrix.fn)
        .put("excluded", matrix.excluded).put("evaluated", matrix.evaluated)
        .put("precision", matrix.precision ?: JSONObject.NULL).put("recall", matrix.recall ?: JSONObject.NULL)
        .put("f1", matrix.f1 ?: JSONObject.NULL)
        .put("false_positive_rate", matrix.falsePositiveRate ?: JSONObject.NULL)
        .put("false_negative_rate", matrix.falseNegativeRate ?: JSONObject.NULL)

    private fun currentEnvironment(): JSONObject {
        val notificationApp = when (channel) {
            "SMS" -> runCatching { Telephony.Sms.getDefaultSmsPackage(this) }.getOrNull()
            "KAKAO" -> "com.kakao.talk"
            else -> null
        }
        @Suppress("DEPRECATION")
        val notificationAppVersion = notificationApp?.let {
            runCatching { packageManager.getPackageInfo(it, 0).versionName }.getOrNull()
        }
        val environment = JSONObject().put("dataset_version", EvaluationCases.VERSION).put("dataset_sha256", fixtureHash)
            .put("rule_version", RiskAnalyzer.RULE_VERSION)
            .put("app_version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            .put("api_endpoint_sha256", fingerprint(BuildConfig.API_BASE_URL))
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("android_version", Build.VERSION.RELEASE).put("android_api", Build.VERSION.SDK_INT)
            .put("channel", channel).put("network_declared_by_tester", network)
            .put("notification_app_package", notificationApp ?: JSONObject.NULL)
            .put("notification_app_version", notificationAppVersion ?: JSONObject.NULL)
        val keys = environment.keys().asSequence().toList().sorted()
        return environment.put("signature", fingerprint(keys.joinToString("\u0000") { "$it=${environment.get(it)}" }))
    }

    private fun report(): JSONObject {
        val environment = currentEnvironment()
        val envelope = evidence.envelope(environment).put("session_id", sessionId)
            .put("started_at", startedAt).put("exported_at", Instant.now().toString())
            .put("observation_method", "manual_notification_observation")
        val matrix = evidence.matrix(cases, environment)
        if (matrix == null) return envelope.put("status", "incompatible_session_unaggregated")
        val rows = JSONArray()
        cases.forEach { case ->
            val local = RiskAnalyzer.analyze(case.text)
            rows.put(JSONObject().put("case_id", case.id).put("expected_phishing", case.phishing)
                .put("local_score", local.score).put("local_warned", local.shouldWarn)
                .put("local_signals", JSONArray(local.hits.map { it.id }))
                .put("observation", observations.optJSONObject(case.id) ?: JSONObject.NULL))
        }
        return envelope.put("session_id", sessionId)
            .put("started_at", startedAt).put("exported_at", Instant.now().toString())
            .put("dataset_version", EvaluationCases.VERSION).put("rule_version", RiskAnalyzer.RULE_VERSION)
            .put("dataset_sha256", fixtureHash)
            .put("observation_method", "manual_notification_observation")
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("android_version", Build.VERSION.RELEASE).put("android_api", Build.VERSION.SDK_INT)
            .put("app_version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            .put("channel", channel).put("network_declared_by_tester", network)
            .put("notification_app_package", environment.get("notification_app_package"))
            .put("notification_app_version", environment.get("notification_app_version"))
            .put("local_synthetic_metrics", matrixJson(ConfusionMatrix.local(cases)))
            .put("field_observation_metrics", matrixJson(matrix)).put("cases", rows)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun outcomeLabel(value: String?) = when (value) {
        "WARNING" -> "수신 확인 · 경고 있음"
        "NO_WARNING" -> "수신 확인 · 경고 없음"
        "NOT_RECEIVED" -> "문자·알림 미수신 (집계 제외)"
        "INCONCLUSIVE" -> "판단 불가 (집계 제외)"
        else -> "미기록"
    }

    private fun matrixText(matrix: ConfusionMatrix): String {
        fun percent(value: Double?) = value?.let { "%.1f%%".format(it * 100) } ?: "계산 불가"
        return "평가 ${matrix.evaluated}건 · 제외 ${matrix.excluded}건\n" +
            "위험 경고 ${matrix.tp} · 정상 오탐 ${matrix.fp}\n정상 무경고 ${matrix.tn} · 위험 미탐 ${matrix.fn}\n" +
            "오탐률 ${percent(matrix.falsePositiveRate)} · 미탐률 ${percent(matrix.falseNegativeRate)}"
    }
}
