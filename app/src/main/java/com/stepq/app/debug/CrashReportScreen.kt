package com.stepq.app.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stepq.app.ui.components.StepqButton
import com.stepq.app.ui.components.StepqButtonStyle
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqType

/** Debug builds only: shows the crash from the last run so a tester can send it to us. */
@Composable
fun CrashReportScreen(report: String, onContinue: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(StepqColors.White).safeDrawingPadding().padding(24.dp),
    ) {
        Text("StepQ crashed last time", style = StepqType.Title, color = StepqColors.Ink)
        Text(
            "Please share this report with the team, then continue.",
            style = StepqType.Body,
            color = StepqColors.Muted,
        )
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.weight(1f).fillMaxWidth().background(StepqColors.Mint).padding(12.dp)
                .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
        ) {
            Text(report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = StepqColors.Ink)
        }
        Spacer(Modifier.height(16.dp))
        StepqButton("Share report", {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "StepQ crash report")
                .putExtra(Intent.EXTRA_TEXT, report)
            context.startActivity(Intent.createChooser(send, "Share crash report"))
        }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        StepqButton("Copy", {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("StepQ crash report", report))
        }, style = StepqButtonStyle.Outline, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        StepqButton("Continue", onContinue, style = StepqButtonStyle.Outline, modifier = Modifier.fillMaxWidth())
    }
}
