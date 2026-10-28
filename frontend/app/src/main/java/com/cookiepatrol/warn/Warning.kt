package com.cookiepatrol.warn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cookiepatrol.ui.CookiePatrolTheme

private const val CONCLUSION = "사기(피싱)일 수 있어요"
private const val DEFAULT_ACTION = "누르지 말고, 돈·개인정보·인증번호를 보내지 마세요. 걱정되면 112에 전화하세요."

/** 경고문 = 결론 1줄 + 이유 1줄 + 할 일 1줄. 알림으로 띄우고, 누르면 큰 글씨 화면. */
object Warning {
    private const val CHANNEL = "warning"
    private const val NOTIFICATION_ID = 1001

    fun show(context: Context, reason: String, action: String = DEFAULT_ACTION) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "위험 경고", NotificationManager.IMPORTANCE_HIGH))
        val intent = Intent(context, WarningActivity::class.java)
            .putExtra("reason", reason)
            .putExtra("action", action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            context, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(CONCLUSION)
            .setContentText(reason)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }
}

/** 강제로 막지 않는다: 확인 버튼은 화면만 닫는다. */
class WarningActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val reason = intent.getStringExtra("reason")?.takeIf { it.isNotBlank() }
            ?: "의심스러운 내용이 발견됐어요."
        val action = intent.getStringExtra("action")?.takeIf { it.isNotBlank() } ?: DEFAULT_ACTION
        setContent {
            CookiePatrolTheme {
                Column(
                    Modifier.fillMaxSize().safeDrawingPadding()
                        .verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Text("위험 신호 발견", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error)
                    Text(CONCLUSION, fontSize = 38.sp, lineHeight = 48.sp,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text("발견한 이유", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(reason, fontSize = 26.sp, lineHeight = 36.sp)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("지금 이렇게 해주세요", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text(action, fontSize = 26.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(onClick = ::finish, modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp)) {
                        Text("확인했어요", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("누르면 이 안내 화면이 닫혀요.", fontSize = 22.sp, lineHeight = 30.sp)
                }
            }
        }
    }
}
