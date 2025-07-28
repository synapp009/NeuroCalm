package nodomain.synapp.neurocalm

import android.media.MediaPlayer
import android.os.*
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import com.google.accompanist.flowlayout.FlowRow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

    override fun onDestroy() {
        super.onDestroy()
    }
}

@Composable
fun VagusStimUI(vibrator: Vibrator, showIntro: Boolean, onDismissIntro: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val dynamicTopPadding = screenHeight * 0.05f

    val mediaPlayer: MediaPlayer? = remember {
        MediaPlayer.create(context, R.raw.result)?.apply { isLooping = true }
    }
    DisposableEffect(Unit) {
        onDispose { mediaPlayer?.release() }
    }

    var minutes by remember { mutableStateOf(5f) }
    var isVibrating by remember { mutableStateOf(false) }
    var threadHandle by remember { mutableStateOf<Thread?>(null) }
    var showDialog by remember { mutableStateOf(showIntro) }
    var playSound by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.title)) },
            text = {
                Column {
                    Text(stringResource(R.string.intro) + "\n")
                    Text(buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(stringResource(R.string.side_effects_title))
                        }
                    })

                    Text(
                        text = stringResource(R.string.side_effects)
                            .split("-")
                            .filter { it.isNotBlank() }
                            .joinToString("\n") { "-${it.trim()}" }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.heat_warning), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.disclaimer))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onDismissIntro()
                }) {
                    Text(stringResource(R.string.got_it))
                }
            }
        )
    }

    Scaffold { innerPadding ->
        BoxWithConstraints {
            val isSmallScreen = maxWidth < 360.dp
            Column(
                Modifier
                    .padding(innerPadding)
                    .padding(top = dynamicTopPadding, start = 16.dp, end = 16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.duration_label) + " ${minutes.toInt()} " + stringResource(R.string.minutes))
                Slider(
                    value = minutes,
                    onValueChange = { minutes = it },
                    valueRange = 0f..30f,
                    enabled = !isVibrating,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                )


                Spacer(Modifier.height(12.dp))

                FlowRow(
                    mainAxisSpacing = 8.dp,
                    crossAxisSpacing = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(1f, 3f, 5f, 10f).forEach { preset ->
                        Button(
                            onClick = { minutes = preset },
                            enabled = !isVibrating,
                            shape = RoundedCornerShape(4.dp), // minimale Rundung
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp) // weniger Abstand
                        ) {
                            Text("${preset.toInt()} min")
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = playSound, onCheckedChange = { playSound = it }, enabled = !isVibrating)
                    Text(stringResource(R.string.play_sound))
                }

                Spacer(Modifier.height(24.dp))

                if (!isVibrating) {
                    Text(stringResource(R.string.choose_mode))

                    listOf(
                        stringResource(R.string.standard) to ::startStandardVibration,
                        stringResource(R.string.continuous) to ::startContinuousVibration,
                        stringResource(R.string.burst) to ::startBurstVibration,
                        stringResource(R.string.am) to ::startAmplitudeModulatedVibration
                    ).forEach { (label, function) ->
                        Button(
                            onClick = {
                                if (playSound) mediaPlayer?.start()
                                isVibrating = true
                                threadHandle = function(vibrator, minutes) {
                                    isVibrating = false
                                    mediaPlayer?.pause()
                                    mediaPlayer?.seekTo(0)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(label)
                        }
                    }
                }

                if (isVibrating) {
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = {
                        isVibrating = false
                        vibrator.cancel()
                        threadHandle?.interrupt()
                        threadHandle = null
                        mediaPlayer?.pause()
                        mediaPlayer?.seekTo(0)
                    }, modifier = Modifier.fillMaxWidth(),shape = RoundedCornerShape(4.dp)) {
                        Text(stringResource(R.string.stop))
                    }
                }


                // State für das Dialogfenster
                var showInstructions by remember { mutableStateOf(false) }
                val instructionLabel = stringResource(R.string.instructions).substringBefore(":")


                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = { showInstructions = true }) {
                        Text(instructionLabel)
                    }
                }

// Der Dialog mit den Anleitungen
                if (showInstructions) {
                    AlertDialog(
                        onDismissRequest = { showInstructions = false },
                        title = { Text(instructionLabel) },
                        text = {
                            Column {
                                Image(
                                    painter = painterResource(R.drawable.ear_diagram),
                                    contentDescription = stringResource(R.string.image_desc),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.instructions)
                                        .substringAfter(":")
                                        .replace("1.", "\n1.")
                                        .replace("2.", "\n2.")
                                        .replace("3.", "\n3.")
                                        .trim(),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showInstructions = false }) {
                                Text("OK")
                            }
                        }
                    )
                }
            }
        }
    }
}

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
        } catch (_: InterruptedException) { }
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
        } catch (_: InterruptedException) { }
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
        } catch (_: InterruptedException) { }
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
        } catch (_: InterruptedException) { }
        onStop()
    }
    thread.start()
    return thread
}
