package com.example.aicodebuilder

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ChatMessage(val text: String, val isUser: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Color(0xFF131314),
                surface = Color(0xFF1E1F20),
                primary = Color(0xFF8AB4F8),
                onBackground = Color(0xFFE3E3E3),
                onSurface = Color(0xFFE3E3E3)
            )) {
                AICodeBuilderApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AICodeBuilderApp() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE) }

    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var showKeyDialog by remember { mutableStateOf(apiKey.isBlank()) }
    var tempKey by remember { mutableStateOf(apiKey) }

    val modes = listOf("Android APK", "Web App", "Error Fixer")
    var selectedMode by remember { mutableStateOf(modes[0]) }

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    if (showKeyDialog) {
        AlertDialog(
            onDismissRequest = { if (apiKey.isNotBlank()) showKeyDialog = false },
            title = { Text("Gemini API Key") },
            text = {
                Column {
                    Text("Google AI Studio se apni API key yahan paste karein:", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    apiKey = tempKey.trim()
                    prefs.edit().putString("api_key", apiKey).apply()
                    showKeyDialog = false
                    Toast.makeText(context, "API Key Saved!", Toast.LENGTH_SHORT).show()
                }) { Text("Save") }
            }
        )
    }

    Scaffold(
        containerColor = Color(0xFF131314),
        topBar = {
            Column(modifier = Modifier.background(Color(0xFF131314))) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF131314)),
                    title = {
                        Text("AI Code Builder", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                    },
                    actions = {
                        TextButton(onClick = {
                            tempKey = apiKey
                            showKeyDialog = true
                        }) {
                            Text("API Key", color = Color(0xFF8AB4F8))
                        }
                    }
                )
                // Mode Switcher Chips
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    modes.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode, fontSize = 12.sp) }
                        )
                    }
                }
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131314))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ask to build any $selectedMode...", color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E1F20),
                        unfocusedContainerColor = Color(0xFF1E1F20),
                        focusedBorderColor = Color(0xFF8AB4F8),
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val prompt = inputText.trim()
                        if (prompt.isEmpty() || isLoading) return@Button
                        if (apiKey.isBlank()) {
                            showKeyDialog = true
                            return@Button
                        }
                        messages.add(ChatMessage(prompt, true))
                        inputText = ""
                        isLoading = true

                        scope.launch {
                            listState.animateScrollToItem(messages.size - 1)
                            val reply = callGeminiApi(apiKey, selectedMode, prompt)
                            messages.add(ChatMessage(reply, false))
                            isLoading = false
                            listState.animateScrollToItem(messages.size - 1)
                        }
                    },
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(50.dp)
                ) {
                    Text("➤", fontSize = 18.sp)
                }
            }
        }
    ) { padding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("Hello Developer!", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8AB4F8))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Aaj kaunsa Android ya Web App banana hai?", color = Color.Gray, fontSize = 15.sp)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages) { msg ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (msg.isUser) Color(0xFF282A2C) else Color(0xFF1E1F20),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(14.dp)
                        ) {
                            Column {
                                Text(
                                    text = msg.text,
                                    color = Color(0xFFE3E3E3),
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                                if (!msg.isUser) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(msg.text))
                                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text("Copy Response", fontSize = 12.sp, color = Color(0xFF8AB4F8))
                                    }
                                }
                            }
                        }
                    }
                }
                if (isLoading) {
                    item {
                        Text("Generating code...", color = Color(0xFF8AB4F8), fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
    }
}

suspend fun callGeminiApi(apiKey: String, mode: String, userPrompt: String): String = withContext(Dispatchers.IO) {
    try {
        val systemInstruction = when (mode) {
            "Android APK" -> "You are an expert Android Kotlin Jetpack Compose developer. Always give complete, error-free, step-by-step code with exact file paths so the user can build an APK on GitHub Actions. Reply in simple Hinglish."
            "Web App" -> "You are an expert Full-Stack Web Developer. Always give complete, working HTML, CSS, and JavaScript/React code step by step. Reply in simple Hinglish."
            else -> "You are a senior debugging expert. Find the exact bug in the user's code or error log and provide the direct fix. Reply in simple Hinglish."
        }

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val body = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
            })
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
            }))
        }

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val response = BufferedReader(InputStreamReader(stream)).use { it.readText() }

        if (conn.responseCode in 200..299) {
            val json = JSONObject(response)
            json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        } else {
            "API Error (${conn.responseCode}): $response"
        }
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}
