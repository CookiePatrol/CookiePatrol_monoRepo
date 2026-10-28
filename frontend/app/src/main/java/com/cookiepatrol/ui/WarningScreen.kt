package com.cookiepatrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WarningScreen(reason: String, action: String, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("COOKIE PATROL", Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            fontSize = 20.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold,
            color = colors.onSurfaceVariant)
        PatrolCard {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                PatrolIcon(PatrolSymbol.WARNING, colors.error)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("잠깐, 확인해주세요", color = colors.error,
                        fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("사기(피싱)일 수\n있어요", fontSize = 36.sp, lineHeight = 46.sp,
                        letterSpacing = (-1).sp, fontWeight = FontWeight.Bold)
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("발견한 이유", fontSize = 22.sp, color = colors.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold)
                    Text(reason, fontSize = 26.sp, lineHeight = 38.sp)
                }
                Column(
                    Modifier.fillMaxWidth().background(colors.errorContainer, RoundedCornerShape(20.dp)).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("지금 할 일", fontSize = 22.sp, color = colors.onErrorContainer,
                        fontWeight = FontWeight.SemiBold)
                    Text(action, fontSize = 26.sp, lineHeight = 38.sp, color = colors.onErrorContainer,
                        fontWeight = FontWeight.SemiBold)
                }
                PatrolButton("확인했어요", onDismiss)
            }
        }
        Text("버튼을 누르면 안내 화면이 닫혀요.\n계속 진행할지는 직접 결정할 수 있어요.",
            Modifier.padding(horizontal = 4.dp), fontSize = 20.sp,
            lineHeight = 30.sp, color = colors.onSurfaceVariant)
    }
}
