package nodomain.synapp.neurocalm

import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import nodomain.synapp.neurocalm.R


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vibrator = getSystemService<Vibrator>() ?: return

        val prefs = getSharedPreferences("vagus_prefs", MODE_PRIVATE)
        val firstLaunch = prefs.getBoolean("firstLaunch", true)

        setContent {
            MaterialTheme {
                VagusStimUI(vibrator, firstLaunch) {
                    prefs.edit().putBoolean("firstLaunch", false).apply()
                }
            }
        }
    }
}


@Composable
fun VagusStimUI(vibrator: Vibrator, showIntro: Boolean, onDismissIntro: () -> Unit) {
    var minutes by remember { mutableStateOf(5f) }
    var isVibrating by remember { mutableStateOf(false) }
    var threadHandle by remember { mutableStateOf<Thread?>(null) }
    var showDialog by remember { mutableStateOf(showIntro) }


    if (showDialog) {


                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Vagus Nerve Stimulation") },
                    text = {
                        Column {
                            Text("This app stimulates the auricular branch of the vagus nerve to activate the parasympathetic nervous system.\n")
                            Text("⚠️ Possible side effects:\n")
                            Text(
                                """
                - Tingling or numbness (common)
                - Skin irritation (occasional)
                - Mild dizziness (occasional)
                - Drowsiness (occasional)
                - Headache (rare)
                - Nausea (rare)
                """.trimIndent()
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "⚠️ Prolonged continuous vibration may cause your device to heat up. Avoid using Continuous mode for extended periods.",
                                color = Color.Red
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Use at your own risk. No liability for use.")
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showDialog = false
                            onDismissIntro()
                        }) {
                            Text("Got it")
                        }
                    }
                )
    }


    Column(Modifier.padding(top = 48.dp, start = 16.dp, end = 16.dp)) {
        Text("Stimulation duration: ${minutes.toInt()} minutes")
        Slider(
            value = minutes,
            onValueChange = { minutes = it },
            valueRange = 0f..30f,
            enabled = !isVibrating
        )

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(1f, 3f, 5f, 10f).forEach { preset ->
                Button(
                    onClick = { minutes = preset },
                    enabled = !isVibrating
                ) {
                    Text("${preset.toInt()} min")
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        if (!isVibrating) {
            Text("Choose a stimulation mode:")

            Button(onClick = {
                isVibrating = true
                threadHandle = startStandardVibration(vibrator, minutes) { isVibrating = false }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Standard (500ms ON / 1500ms OFF)")
            }

            Button(onClick = {
                isVibrating = true
                threadHandle = startContinuousVibration(vibrator, minutes) { isVibrating = false }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Continuous")
            }

            Button(onClick = {
                isVibrating = true
                threadHandle = startBurstVibration(vibrator, minutes) { isVibrating = false }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Burst")
            }

            Button(onClick = {
                isVibrating = true
                threadHandle = startAmplitudeModulatedVibration(vibrator, minutes) { isVibrating = false }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Amplitude Modulated")
            }
        }

        if (isVibrating) {
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    isVibrating = false
                    vibrator.cancel()
                    threadHandle?.interrupt()
                    threadHandle = null
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop")
            }
        }

        Spacer(Modifier.height(32.dp))

        Image(
            painter = painterResource(id = R.drawable.ear_diagram),
            contentDescription = "Ear stimulation guide",
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentScale = ContentScale.Fit,
        )


        Spacer(Modifier.height(16.dp))

        Text(
            text = buildAnnotatedString {
                append("Instructions:\n")
                append("1. Tap a vibration mode to start.\n")
                append("2. Place the bottom-left corner of your phone directly on the ")

                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("marked point inside your left ear")
                }

                append(" (cymba conchae). Hold gently for the duration of the stimulation.")
            },
            style = MaterialTheme.typography.bodyMedium
        )

    }
}


// === Threaded vibration functions ===

fun startStandardVibration(vibrator: Vibrator, minutes: Float, onStop: () -> Unit): Thread {
    val duration = (minutes * 60 * 1000).toLong()
    val thread = Thread {
        val startTime = System.currentTimeMillis()
        try {
            while (!Thread.interrupted() && (System.currentTimeMillis() - startTime < duration || minutes == 0f)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(500, 255))
                } else vibrator.vibrate(500)
                Thread.sleep(1500)
            }
        } catch (_: InterruptedException) {}
        onStop()
    }
    thread.start()
    return thread
}

fun startContinuousVibration(vibrator: Vibrator, minutes: Float, onStop: () -> Unit): Thread {
    val duration = (minutes * 60 * 1000).toLong()
    val thread = Thread {
        val startTime = System.currentTimeMillis()
        try {
            while (!Thread.interrupted() && (System.currentTimeMillis() - startTime < duration || minutes == 0f)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(10000, 255))
                } else vibrator.vibrate(10000)
                Thread.sleep(10000)
            }
        } catch (_: InterruptedException) {}
        onStop()
    }
    thread.start()
    return thread
}

fun startBurstVibration(vibrator: Vibrator, minutes: Float, onStop: () -> Unit): Thread {
    val duration = (minutes * 60 * 1000).toLong()
    val thread = Thread {
        val startTime = System.currentTimeMillis()
        try {
            while (!Thread.interrupted() && (System.currentTimeMillis() - startTime < duration || minutes == 0f)) {
                repeat(3) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(100, 255))
                    } else vibrator.vibrate(100)
                    Thread.sleep(200)
                }
                Thread.sleep(1500)
            }
        } catch (_: InterruptedException) {}
        onStop()
    }
    thread.start()
    return thread
}

fun startAmplitudeModulatedVibration(vibrator: Vibrator, minutes: Float, onStop: () -> Unit): Thread {
    val duration = (minutes * 60 * 1000).toLong()
    val thread = Thread {
        val startTime = System.currentTimeMillis()
        try {
            while (!Thread.interrupted() && (System.currentTimeMillis() - startTime < duration || minutes == 0f)) {
                for (amp in 50..255 step 20) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(100, amp))
                    } else vibrator.vibrate(100)
                    Thread.sleep(150)
                }
                for (amp in 255 downTo 50 step 20) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(100, amp))
                    } else vibrator.vibrate(100)
                    Thread.sleep(150)
                }
                Thread.sleep(1000)
            }
        } catch (_: InterruptedException) {}
        onStop()
    }
    thread.start()
    return thread
}
