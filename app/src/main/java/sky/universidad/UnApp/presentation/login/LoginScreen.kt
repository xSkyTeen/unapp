package sky.universidad.UnApp.presentation.login

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sky.universidad.UnApp.R
import sky.universidad.UnApp.core.session.SessionSnapshot
import sky.universidad.UnApp.core.session.SessionStatus
import sky.universidad.UnApp.presentation.components.AppleBottomSheet
import sky.universidad.UnApp.presentation.components.ApplePillBadge
import sky.universidad.UnApp.presentation.components.AppleSubtleDivider
import sky.universidad.UnApp.presentation.components.UnAppIcons
import sky.universidad.UnApp.presentation.components.applePressable
import sky.universidad.UnApp.ui.theme.CalibriFontFamily
import sky.universidad.UnApp.ui.theme.LocalUnAppColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LoginScreen(
    session: SessionSnapshot,
    isSyncing: Boolean,
    errorMessage: String?,
    onLogin: (identifier: String, password: String) -> Unit,
    onClearError: () -> Unit
) {
    var identifier by remember { mutableStateOf(session.userIdentifier.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var acceptedTerms by remember { mutableStateOf(false) }
    var showTermsSheet by remember { mutableStateOf(false) }
    var localValidationError by remember { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val view = LocalView.current
    val extended = LocalUnAppColors.current
    val idBringIntoView = remember { BringIntoViewRequester() }
    val passBringIntoView = remember { BringIntoViewRequester() }
    val isAuthenticating = session.status == SessionStatus.AUTHENTICATING || isSyncing

    // Light icons on dark aerial hero; restore active theme on dispose
    DisposableEffect(extended.isDark) {
        val window = (view.context as? Activity)?.window
        val controller = if (window != null) WindowCompat.getInsetsController(window, view) else null
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = !extended.isDark
        }
    }

    LaunchedEffect(session.status) {
        if (session.status == SessionStatus.AUTHENTICATED) {
            password = ""
        }
    }

    val displayedError = localValidationError ?: errorMessage ?: session.errorMessage

    fun attemptLogin() {
        focusManager.clearFocus()
        when {
            !acceptedTerms -> {
                localValidationError = "Debes aceptar los Términos y Condiciones para ingresar."
            }
            identifier.isBlank() || password.isBlank() -> {
                localValidationError = "Completa tu DNI/código y contraseña."
            }
            else -> onLogin(identifier, password)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1428))
    ) {
        val heroHeight = (maxHeight * 0.88f).coerceAtLeast(520.dp)
        val sheetMinHeight = (maxHeight * 0.90f).coerceAtLeast(520.dp)
        val heroHeightPx = with(density) { heroHeight.toPx() }.coerceAtLeast(1f)
        val scrollProgress = (scrollState.value / (heroHeightPx * 0.72f)).coerceIn(0f, 1f)

        // Aerial campus photo with smooth upward parallax and progressive blur when scrolling sheet up
        Image(
            painter = painterResource(id = R.drawable.ciudad_universitaria_vista_aerea),
            contentDescription = "Vista aérea Ciudad Universitaria UNAP Puno",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(
                    radius = (scrollProgress * 16f).dp,
                    edgeTreatment = BlurredEdgeTreatment.Rectangle
                )
                .graphicsLayer {
                    val s = 1.08f - (scrollProgress * 0.05f)
                    scaleX = s
                    scaleY = s
                    translationY = -(scrollState.value * 0.18f)
                }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF071226).copy(alpha = 0.36f + scrollProgress * 0.28f),
                            Color(0xFF0A1B3D).copy(alpha = 0.50f + scrollProgress * 0.24f),
                            Color(0xFF060E1E).copy(alpha = 0.86f)
                        )
                    )
                )
        )

        // Gradual blurred fade across the phone notification/status bar on the hero cover
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.00f to Color.Black,
                            0.45f to Color.Black.copy(alpha = 0.72f),
                            1.00f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            Image(
                painter = painterResource(id = R.drawable.ciudad_universitaria_vista_aerea),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 20.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle)
                    .graphicsLayer {
                        val s = 1.08f - (scrollProgress * 0.05f)
                        scaleX = s
                        scaleY = s
                    }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF050C1B).copy(alpha = 0.56f),
                                Color(0xFF071226).copy(alpha = 0.22f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
        ) {
            val cueAlpha = (1f - (scrollProgress * 3.6f)).coerceIn(0f, 1f)

            // Initial full-screen aerial hero: large transparent UNAP shield + uppercase institution name
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 28.dp)
                    .graphicsLayer {
                        alpha = (1f - scrollProgress * 0.65f).coerceIn(0.20f, 1f)
                        translationY = scrollState.value * 0.14f
                    }
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(176.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF060E1E).copy(alpha = 0.42f),
                                        Color.Transparent
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_unap),
                            contentDescription = "Escudo UNAP",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(148.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "UNIVERSIDAD NACIONAL DEL\nALTIPLANO DE PUNO",
                        fontFamily = CalibriFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        letterSpacing = 0.8.sp,
                        lineHeight = 30.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "UnApp",
                        fontFamily = CalibriFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.8.sp,
                        color = Color.White.copy(alpha = 0.82f),
                        textAlign = TextAlign.Center
                    )
                }

                if (cueAlpha > 0.01f) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.16f),
                        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.36f)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .graphicsLayer {
                                alpha = cueAlpha
                                translationY = -(scrollProgress * 36f)
                            }
                            .applePressable {
                                coroutineScope.launch {
                                    scrollState.animateScrollTo((heroHeightPx * 0.85f).toInt())
                                }
                            }
                    ) {
                        Text(
                            text = "Desliza hacia arriba para acceder",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                        )
                    }
                }
            }

            // Sliding Upward Access Sheet with transparent top gradient & soft white mist under logo_psg
            val sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            val sheetSurface = MaterialTheme.colorScheme.surface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = sheetMinHeight)
                    .clip(sheetShape)
                    .background(
                        Brush.verticalGradient(
                            0.00f to Color.Transparent,
                            0.14f to sheetSurface.copy(alpha = 0.34f),
                            0.28f to sheetSurface.copy(alpha = 0.78f),
                            0.45f to sheetSurface.copy(alpha = 0.94f),
                            1.00f to sheetSurface.copy(alpha = 0.98f)
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.48f))
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Transparent logo_psg with no white background or blotch
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_psg),
                            contentDescription = "Universidad Nacional del Altiplano - Puno",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth(0.84f)
                                .height(82.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    OutlinedTextField(
                        value = identifier,
                        onValueChange = { raw ->
                            identifier = raw.filter { it.isDigit() }.take(15)
                            localValidationError = null
                            if (errorMessage != null || session.errorMessage != null) onClearError()
                        },
                        label = { Text("DNI o código de estudiante") },
                        placeholder = { Text("Ej. 74128950 o 220145") },
                        singleLine = true,
                        enabled = !isAuthenticating,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(idBringIntoView)
                            .onFocusEvent { event ->
                                if (event.isFocused) {
                                    coroutineScope.launch {
                                        delay(180)
                                        idBringIntoView.bringIntoView()
                                    }
                                }
                            }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            localValidationError = null
                            if (errorMessage != null || session.errorMessage != null) onClearError()
                        },
                        label = { Text("Contraseña") },
                        singleLine = true,
                        enabled = !isAuthenticating,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) UnAppIcons.EyeOff else UnAppIcons.Eye,
                                    contentDescription = if (showPassword) "Ocultar contraseña" else "Mostrar contraseña",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { attemptLogin() }
                        ),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(passBringIntoView)
                            .onFocusEvent { event ->
                                if (event.isFocused) {
                                    coroutineScope.launch {
                                        delay(180)
                                        passBringIntoView.bringIntoView()
                                    }
                                }
                            }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Legal Terms and Data Usage Consent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                            .border(
                                0.8.dp,
                                if (acceptedTerms) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable(enabled = !isAuthenticating) {
                                acceptedTerms = !acceptedTerms
                                localValidationError = null
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = acceptedTerms,
                            onCheckedChange = { checked ->
                                acceptedTerms = checked
                                localValidationError = null
                            },
                            enabled = !isAuthenticating,
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Acepto los Términos y Condiciones y la Política de Privacidad y Uso de Datos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Leer términos y condiciones legales",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier
                                    .clickable { showTermsSheet = true }
                                    .padding(top = 2.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = !displayedError.isNullOrBlank()) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.error,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = UnAppIcons.AlertCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = displayedError.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { attemptLogin() },
                        enabled = !isAuthenticating,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (isAuthenticating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                text = "Ingresar",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = UnAppIcons.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Sesión cifrada localmente con Android Keystore",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

    if (showTermsSheet) {
        TermsAndConditionsSheet(
            onAccept = {
                acceptedTerms = true
                localValidationError = null
                showTermsSheet = false
            },
            onDismiss = { showTermsSheet = false }
        )
    }
}

@Composable
private fun TermsAndConditionsSheet(
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    AppleBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 540.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ApplePillBadge(text = "Marco legal y privacidad")
            Text(
                text = "Términos, Condiciones y Tratamiento de Datos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            AppleSubtleDivider()

            LegalClauseBlock(
                title = "1. Naturaleza de la aplicación",
                body = "UnApp es un11 cliente móvil independiente de consulta académica de solo lectura para estudiantes de la Universidad Nacional del Altiplano (UNAP - Puno). No reemplaza ni constituye un portal oficial administrado por la Oficina de Tecnologías de Información de la UNAP."
            )
            LegalClauseBlock(
                title = "2. Alcance y restricciones de datos",
                body = "Tus credenciales institucionales se envían única y directamente al servidor oficial de autenticación universitaria (campus.unap.edu.pe, aulavirtual2.unap.edu.pe e intranet.unap.edu.pe). UnApp no almacena tu contraseña en texto claro ni la transmite a servidores externos o de terceros."
            )
            LegalClauseBlock(
                title = "3. Cifrado y almacenamiento local (Ley N.° 29733)",
                body = "Conforme a la Ley N.° 29733 (Ley de Protección de Datos Personales del Perú), las cookies de sesión y la caché académica se guardan exclusivamente en tu dispositivo mediante almacenamiento cifrado respaldado por Android Keystore (AES-256-GCM / EncryptedSharedPreferences). Los datos sensibles como código y DNI se restringen a tu vista de perfil."
            )
            LegalClauseBlock(
                title = "4. Modo solo lectura y responsabilidad",
                body = "La aplicación se limita a consultar horarios, tareas, foros, notas, asistencia, historial y apuntes personales locales. Las entregas de tareas, exámenes y participaciones oficiales deben verificarse siempre en el Aula Virtual e Intranet web oficial. Al cerrar sesión o desinstalar UnApp puedes eliminar toda la caché almacenada."
            )

            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onAccept,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Aceptar términos y continuar", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LegalClauseBlock(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
