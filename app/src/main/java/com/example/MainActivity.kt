package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold"),
                    contentWindowInsets = WindowInsets.systemBars
                ) { innerPadding ->
                    MainScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Form inputs and status states
    var unicodeText by remember { mutableStateOf("") }
    var krutiText by remember { mutableStateOf("") }
    var isOperating by remember { mutableStateOf(false) }
    var scanStatusMessage by remember { mutableStateOf("रेडी है! कृपया फोटो अपलोड करें।") }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showExplanationDialog by remember { mutableStateOf(false) }
    
    // Warn user about missing API key if it's the placeholder or empty
    val apiKey = BuildConfig.GEMINI_API_KEY
    val isApiKeyConfigured = apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY"

    // Launcher for picking image from gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                isOperating = true
                scanStatusMessage = "⏳ फोटो को लोड किया जा रहा है..."
                val bitmap = loadUriToBitmap(context, uri)
                if (bitmap != null) {
                    selectedBitmap = bitmap
                    scanStatusMessage = "⏳ टेक्स्ट स्कैन हो रहा है (Gemini API)..."
                    coroutineScope.launch {
                        runOcrAndConvert(bitmap, apiKey, isApiKeyConfigured) { resultText, isSuccess ->
                            isOperating = false
                            if (isSuccess) {
                                unicodeText = resultText
                                krutiText = KrutiDevConverter.convertUnicodeToKrutiDev(resultText)
                                scanStatusMessage = "✅ सफलतापूर्वक पूरा हुआ! छोटी-मोटी गलतियों को बॉक्स 1 में सुधार लें।"
                            } else {
                                scanStatusMessage = "❌ त्रुटि! फोटो धुंधली हो सकती है या API की समस्या है।"
                                Toast.makeText(context, resultText, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } else {
                    isOperating = false
                    scanStatusMessage = "❌ फोटो लोड नहीं हो पाई।"
                }
            } catch (e: Exception) {
                isOperating = false
                scanStatusMessage = "❌ फोटो लोड करने में समस्या: ${e.localizedMessage}"
            }
        }
    }

    // Launcher for taking photo with Camera
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            selectedBitmap = bitmap
            isOperating = true
            scanStatusMessage = "⏳ टेक्स्ट स्कैन हो रहा है (Gemini OCR)..."
            coroutineScope.launch {
                runOcrAndConvert(bitmap, apiKey, isApiKeyConfigured) { resultText, isSuccess ->
                    isOperating = false
                    if (isSuccess) {
                        unicodeText = resultText
                        krutiText = KrutiDevConverter.convertUnicodeToKrutiDev(resultText)
                        scanStatusMessage = "✅ सफलतापूर्वक पूरा हुआ! छोटी-मोटी गलतियों को बॉक्स 1 में सुधार लें।"
                    } else {
                        scanStatusMessage = "❌ त्रुटि! फोटो धुंधली हो सकती है या API की समस्या है।"
                        Toast.makeText(context, resultText, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Custom Stylish Header ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "📸 Photo to PageMaker",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Hindi Unicode to KrutiDev 010",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            IconButton(
                onClick = { showExplanationDialog = true },
                modifier = Modifier.testTag("info_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Help Guide",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // --- Graceful Warning Notice (No API Key Configured) ---
        if (!isApiKeyConfigured) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "🔒 API Key config required",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "To run Hindi OCR scanning from photos, please enter your GEMINI_API_KEY in the AI Studio Secrets panel.\n\nYou can still type or paste Hindi text directly in Box 1 for instant conversion offline!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                    )
                }
            }
        }

        // --- Upload/Capture Box Card ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("upload_card_trigger")
                .clickable {
                    if (isOperating) return@clickable
                    galleryLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectedBitmap != null) {
                    // Show a Thumbnail preview of loaded picture
                    Box(modifier = Modifier.size(140.dp)) {
                        Image(
                            bitmap = selectedBitmap!!.asImageBitmap(),
                            contentDescription = "Selected Picture",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { selectedBitmap = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                .size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_gallery),
                        contentDescription = "Gallery Slot",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Text(
                    text = "📁 यहाँ क्लिक करके अपनी Handwriting या डॉक्यूमेंट की फोटो सेलेक्ट करें",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "या",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Button(
                        onClick = {
                            cameraLauncher.launch()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("camera_button")
                    ) {
                        Text("📸 कैमरा शुरू करें", fontSize = 12.sp)
                    }
                }
            }
        }

        // --- Live Status Output Bar ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isOperating) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 6.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Text(
                        text = scanStatusMessage,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // --- Dual conversion layout ---
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Text Box 1: Unicode input
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "1. फोटो से निकला टेक्स्ट (बदलाव करने के लिए यहाँ टाइप करें):",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                OutlinedTextField(
                    value = unicodeText,
                    onValueChange = {
                        unicodeText = it
                        krutiText = KrutiDevConverter.convertUnicodeToKrutiDev(it)
                    },
                    placeholder = {
                        Text(
                            text = "फोटो अपलोड होते ही यहाँ हिंदी टेक्स्ट आ जाएगा या खुद हिंदी टाइप करें...",
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .testTag("unicode_input_box"),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }

            // Text Box 2: Kruti Dev Output Box
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "2. कनवर्टेड कोड (PageMaker के लिए):",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                OutlinedTextField(
                    value = krutiText,
                    onValueChange = {},
                    placeholder = {
                        Text(
                            text = "यहाँ क्रुतिदेव कोड दिखेगा जिसे कॉपी करना है...",
                            fontSize = 14.sp
                        )
                    },
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .testTag("kruti_output_box"),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.secondary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                )

                Button(
                    onClick = {
                        if (krutiText.isEmpty()) {
                            Toast.makeText(context, "कॉपी करने के लिए कोई कोड नहीं है!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("KrutiDev Code", krutiText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(
                            context,
                            "PageMaker कोड कॉपी हो गया! अब PageMaker में Paste करके 'Kruti Dev 010' फॉन्ट लागू करें।",
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .testTag("copy_code_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Copy",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "📋 कोड कॉपी करें / Copy Code",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // --- Instructional pop-up dialog ---
    if (showExplanationDialog) {
        Dialog(onDismissRequest = { showExplanationDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "ℹ️ PageMaker में कैसे उपयोग करें?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider()
                    Text(
                        text = "1. **कोड कॉपी करें**: नीचे दिए गए 'कोड कॉपी करें' बटन पर क्लिक करके KrutiDev टेक्स्ट कॉपी करें।",
                        fontSize = 14.sp
                    )
                    Text(
                        text = "2. **सॉफ्टवेयर खोलें**: Adobe PageMaker / InDesign / QuarkXPress को ओपन करें।",
                        fontSize = 14.sp
                    )
                    Text(
                        text = "3. **पेस्ट करें**: कॉपी किए गए टेक्स्ट को सामान्य रूप से (Ctrl + V) पेस्ट करें। प्रारंभ में यह इंग्लिश अक्षरों की तरह दिखेगा।",
                        fontSize = 14.sp
                    )
                    Text(
                        text = "4. **फॉन्ट बदलें**: पेस्ट किए गए उस टेक्स्ट को सेलेक्ट करें और फॉन्ट बदलना (Font Family) 'Kruti Dev 010' चुनें।",
                        fontSize = 14.sp
                    )
                    Text(
                        text = "5. **तैयार!**: फॉन्ट लागू होते ही कोडेड इंग्लिश शब्द स्वच्छ और शुद्ध क्रुतिदेव 010 हिंदी स्वरूप में प्रस्तुत हो जाएंगे।",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Button(
                        onClick = { showExplanationDialog = false },
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("ठीक है (Got it)")
                    }
                }
            }
        }
    }
}

// --- URI Image Loader Utility ---
private fun loadUriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// --- Multi-modal OCR Call Runner ---
private suspend fun runOcrAndConvert(
    bitmap: Bitmap,
    apiKey: String,
    isApiKeyConfigured: Boolean,
    onFinished: (String, Boolean) -> Unit
) {
    if (!isApiKeyConfigured) {
        onFinished("API key has not been entered. Read instructions in the red box.", false)
        return
    }

    withContext(Dispatchers.IO) {
        try {
            // Compress the image to JPEG with 80% quality to keeping high legibility and reducing package size
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            val requestPrompt = "Scan this photo of Hindi writing (could be handwritten or printed). Extract all the Hindi words. Do not translate them. Provide ONLY the extracted Unicode Hindi text. No notes, no explanations, no markdown formatting. Maintain any line breaks and paragraphs if possible."

            val request = GenerateContentRequest(
                contents = listOf(
                    Content(
                        parts = listOf(
                            Part(text = requestPrompt),
                            Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                        )
                    )
                ),
                systemInstruction = Content(
                    parts = listOf(
                        Part(text = "You are a professional Hindi OCR utility. Your goal is to scan image records and output pristine, clean Unicode Hindi text maintaining absolute typographical fidelity.")
                    )
                ),
                generationConfig = GenerationConfig()
            )

            val response = GeminiClient.apiService.generateContent(apiKey, request)
            val extractedText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

            if (!extractedText.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    onFinished(extractedText.trim(), true)
                }
            } else {
                withContext(Dispatchers.Main) {
                    onFinished("Could not find any readable Hindi text inside this photo.", false)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                onFinished("OCR scanner connection failed: ${e.localizedMessage}", false)
            }
        }
    }
}
