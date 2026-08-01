package com.earshot.diag

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.aware.WifiAwareManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val Pass = Color(0xFF1B5E20)
private val PassBg = Color(0xFFC8E6C9)
private val Warn = Color(0xFFE65100)
private val WarnBg = Color(0xFFFFE0B2)
private val Fail = Color(0xFFB71C1C)
private val FailBg = Color(0xFFFFCDD2)

private enum class Status { PASS, WARN, FAIL }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DiagnosticScreen()
                }
            }
        }
    }
}

@Composable
private fun DiagnosticScreen() {
    val context = LocalContext.current
    val hasFeature = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)
    }
    val awareManager = remember {
        context.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
    }

    var isAvailable by remember { mutableStateOf(awareManager?.isAvailable == true) }

    DisposableEffect(awareManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                isAvailable = awareManager?.isAvailable == true
            }
        }
        val filter = IntentFilter(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    val overallStatus = when {
        !hasFeature -> Status.FAIL
        !isAvailable -> Status.WARN
        else -> Status.PASS
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Header(overallStatus)

        StatusCard(
            title = "Hardware support",
            status = if (hasFeature) Status.PASS else Status.FAIL,
            value = if (hasFeature) "Supported" else "Not supported",
            explanation = if (hasFeature) {
                "PackageManager reports FEATURE_WIFI_AWARE. The device advertises Wi-Fi Aware capability."
            } else {
                "PackageManager does NOT report FEATURE_WIFI_AWARE. Either the hardware lacks it or the OEM has stripped it from this build."
            }
        )

        StatusCard(
            title = "Currently available",
            status = when {
                !hasFeature -> Status.FAIL
                isAvailable -> Status.PASS
                else -> Status.WARN
            },
            value = if (isAvailable) "Available" else "Unavailable",
            explanation = if (isAvailable) {
                "WifiAwareManager.isAvailable is true — Wi-Fi Aware can be used right now."
            } else {
                "WifiAwareManager.isAvailable is false. Toggle Wi-Fi on, disable airplane mode, and check Wi-Fi Aware isn't blocked by power-saving. This card updates live."
            }
        )

        DeviceInfoCard()
    }
}

@Composable
private fun Header(status: Status) {
    val (bg, fg, label) = when (status) {
        Status.PASS -> Triple(PassBg, Pass, "READY")
        Status.WARN -> Triple(WarnBg, Warn, "NOT READY")
        Status.FAIL -> Triple(FailBg, Fail, "UNSUPPORTED")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(20.dp)
    ) {
        Text(
            text = "Wi-Fi Aware Diagnostic",
            color = fg,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(text = label, color = fg, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusCard(
    title: String,
    status: Status,
    value: String,
    explanation: String
) {
    val (bg, fg) = when (status) {
        Status.PASS -> PassBg to Pass
        Status.WARN -> WarnBg to Warn
        Status.FAIL -> FailBg to Fail
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(16.dp)
    ) {
        Text(title, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(value, color = fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(explanation, color = fg, fontSize = 13.sp)
    }
}

@Composable
private fun DeviceInfoCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFECEFF1))
            .padding(16.dp)
    ) {
        Text("Device", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        InfoRow("Model", "${Build.MANUFACTURER} ${Build.MODEL}")
        InfoRow("Android", Build.VERSION.RELEASE)
        InfoRow("API level", Build.VERSION.SDK_INT.toString())
    }
}

@Composable
private fun InfoRow(key: String, value: String) {
    Text("$key: $value", fontSize = 13.sp)
}
