package sky.universidad.UnApp.presentation.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import kotlin.math.absoluteValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sky.universidad.UnApp.core.network.UnapEndpoints
import sky.universidad.UnApp.core.update.AppUpdateInfo
import sky.universidad.UnApp.core.update.AppUpdateManager
import sky.universidad.UnApp.domain.model.Course
import sky.universidad.UnApp.domain.model.InAppAcademicAlert
import sky.universidad.UnApp.presentation.MainUiState
import sky.universidad.UnApp.presentation.MainViewModel
import sky.universidad.UnApp.presentation.bus.BusScreen
import sky.universidad.UnApp.presentation.components.AppleBottomSheet
import sky.universidad.UnApp.presentation.components.ApplePillBadge
import sky.universidad.UnApp.presentation.components.AppleSubtleDivider
import sky.universidad.UnApp.presentation.components.DocumentOpenRequest
import sky.universidad.UnApp.presentation.components.DocumentViewerDialog
import sky.universidad.UnApp.presentation.components.LocalModalBlurController
import sky.universidad.UnApp.presentation.components.ModalBlurController
import sky.universidad.UnApp.presentation.components.UnAppIcons
import sky.universidad.UnApp.presentation.components.applePressable
import sky.universidad.UnApp.presentation.components.appleSpringDefault
import sky.universidad.UnApp.presentation.components.isUnapInternalUrl
import sky.universidad.UnApp.presentation.components.liveModalBlur
import sky.universidad.UnApp.presentation.components.normalizeResourceUrl
import sky.universidad.UnApp.presentation.components.openExternalUri
import sky.universidad.UnApp.presentation.components.rememberReducedMotionEnabled
import sky.universidad.UnApp.presentation.courses.CourseDetailScreen
import sky.universidad.UnApp.presentation.courses.CoursesScreen
import sky.universidad.UnApp.presentation.forums.ForumsAndNoticesScreen
import sky.universidad.UnApp.presentation.home.HomeScreen
import sky.universidad.UnApp.presentation.login.LoginScreen
import sky.universidad.UnApp.presentation.more.IntranetModuleScreen
import sky.universidad.UnApp.presentation.more.MoreDestination
import sky.universidad.UnApp.presentation.more.MoreHubScreen
import sky.universidad.UnApp.presentation.more.StudentProfileScreen
import sky.universidad.UnApp.presentation.notes.NotesScreen
import sky.universidad.UnApp.presentation.portal.WebPortalScreen
import sky.universidad.UnApp.presentation.schedule.ScheduleScreen
import sky.universidad.UnApp.presentation.settings.SettingsScreen
import sky.universidad.UnApp.presentation.tasks.TasksScreen
import sky.universidad.UnApp.ui.theme.LocalUnAppColors

enum class MainTab(
    val label: String,
    val icon: ImageVector
) {
    HOME("Inicio", UnAppIcons.Home),
    COURSES("Cursos", UnAppIcons.Book),
    TASKS("Tareas", UnAppIcons.CheckSquare),
    SCHEDULE("Horario", UnAppIcons.Calendar),
    MORE("Más", UnAppIcons.Grid)
}

@Composable
fun UnAppRoot(
    state: MainUiState,
    viewModel: MainViewModel
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val modalBlurController = remember { ModalBlurController() }

    CompositionLocalProvider(LocalModalBlurController provides modalBlurController) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .liveModalBlur(modalBlurController.blurFraction)
        ) {
            AnimatedContent(
                targetState = state.session.hasActiveAccess,
                transitionSpec = {
                    fadeIn(appleSpringDefault(reducedMotion)) togetherWith
                        fadeOut(appleSpringDefault(reducedMotion))
                },
                label = "authRoot"
            ) { hasAccess ->
                if (!hasAccess) {
                    LoginScreen(
                        session = state.session,
                        isSyncing = state.isSyncing,
                        errorMessage = state.syncError,
                        onLogin = viewModel::login,
                        onClearError = viewModel::clearErrors
                    )
                } else {
                    AuthenticatedShell(
                        state = state,
                        viewModel = viewModel
                    )
                }
            }

            state.availableUpdate?.let { update ->
                AppUpdatePromptSheet(
                    update = update,
                    onDismiss = viewModel::dismissUpdatePrompt
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedShell(
    state: MainUiState,
    viewModel: MainViewModel
) {
    var currentTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var moreDestination by rememberSaveable { mutableStateOf(MoreDestination.HUB) }
    var presetCourseForNote by remember { mutableStateOf<Pair<String, String>?>(null) }
    var activeCourse by remember { mutableStateOf<Course?>(null) }
    var portalOverlayUrl by remember { mutableStateOf<String?>(null) }
    var activeDocument by remember { mutableStateOf<DocumentOpenRequest?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val reducedMotion = rememberReducedMotionEnabled()
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = currentTab.ordinal,
        pageCount = { MainTab.entries.size }
    )

    LaunchedEffect(state.statusBannerMessage) {
        val msg = state.statusBannerMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearErrors()
    }

    LaunchedEffect(state.inAppAlert) {
        if (state.inAppAlert != null) {
            delay(6500)
            viewModel.dismissInAppAlert()
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val target = MainTab.entries[page]
            if (currentTab != target) {
                currentTab = target
                if (target != MainTab.MORE) {
                    moreDestination = MoreDestination.HUB
                }
            }
        }
    }

    val navigateToTab: (MainTab) -> Unit = { target ->
        activeCourse = null
        portalOverlayUrl = null
        if (target == MainTab.MORE && currentTab == MainTab.MORE) {
            moreDestination = MoreDestination.HUB
        } else {
            if (target != MainTab.MORE) {
                moreDestination = MoreDestination.HUB
            }
            currentTab = target
            coroutineScope.launch {
                pagerState.animateScrollToPage(
                    page = target.ordinal,
                    animationSpec = appleSpringDefault(reducedMotion)
                )
            }
        }
    }

    BackHandler(
        enabled = portalOverlayUrl != null ||
            activeCourse != null ||
            (currentTab == MainTab.MORE && moreDestination != MoreDestination.HUB)
    ) {
        when {
            portalOverlayUrl != null -> portalOverlayUrl = null
            activeCourse != null -> activeCourse = null
            currentTab == MainTab.MORE && moreDestination != MoreDestination.HUB -> {
                moreDestination = MoreDestination.HUB
            }
        }
    }

    val openCourseNote: (String, String) -> Unit = { courseId, courseName ->
        activeCourse = null
        portalOverlayUrl = null
        presetCourseForNote = courseId to courseName
        moreDestination = MoreDestination.NOTES
        currentTab = MainTab.MORE
        coroutineScope.launch {
            pagerState.animateScrollToPage(
                page = MainTab.MORE.ordinal,
                animationSpec = appleSpringDefault(reducedMotion)
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 84.dp)
            )
        }
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainer,
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                    )
                )
        ) {
            val bottomNavPadding = PaddingValues(bottom = 88.dp)
            val isOverlayVisible = activeCourse != null || portalOverlayUrl != null

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isOverlayVisible &&
                    !(currentTab == MainTab.MORE && moreDestination == MoreDestination.PORTAL),
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                    .absoluteValue
                    .coerceIn(0f, 1f)
                val blurRadius = if (reducedMotion || pageOffset < 0.01f) 0.dp else (pageOffset * 14f).dp
                val pageAlpha = (1f - pageOffset * 0.28f).coerceIn(0.65f, 1f)
                val pageScale = if (reducedMotion) 1f else 1f - pageOffset * 0.032f

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(radius = blurRadius, edgeTreatment = BlurredEdgeTreatment.Rectangle)
                        .graphicsLayer {
                            alpha = pageAlpha
                            scaleX = pageScale
                            scaleY = pageScale
                        }
                ) {
                    when (MainTab.entries[page]) {
                    MainTab.HOME -> HomeScreen(
                        state = state,
                        bottomContentPadding = bottomNavPadding,
                        onSync = { viewModel.syncNow(notifyNewTasks = true) },
                        onDismissError = viewModel::clearErrors,
                        onToggleTask = viewModel::toggleTaskCompleted,
                        onOpenTasksTab = { navigateToTab(MainTab.TASKS) },
                        onOpenCoursesTab = { navigateToTab(MainTab.COURSES) },
                        onOpenScheduleTab = { navigateToTab(MainTab.SCHEDULE) },
                        onCreateNoteForCourse = openCourseNote,
                        onOpenCourse = { course -> activeCourse = course },
                        onLoadHomeworkDetail = viewModel::loadHomeworkDetail,
                        onLoadForumDiscussion = viewModel::loadForumDiscussion,
                        onOpenUrl = { url ->
                            val normalized = normalizeResourceUrl(url)
                            if (isUnapInternalUrl(normalized)) {
                                activeDocument = DocumentOpenRequest(
                                    title = "Documento adjunto",
                                    url = normalized
                                )
                            } else {
                                openExternalUri(context, normalized)
                            }
                        }
                    )

                    MainTab.COURSES -> CoursesScreen(
                        state = state,
                        bottomContentPadding = bottomNavPadding,
                        onSync = { viewModel.syncNow(notifyNewTasks = true) },
                        onEnsureLoaded = { viewModel.ensureCoursesLoaded(false) },
                        onToggleTask = viewModel::toggleTaskCompleted,
                        onCreateNoteForCourse = openCourseNote,
                        onOpenCourse = { course -> activeCourse = course }
                    )

                    MainTab.TASKS -> TasksScreen(
                        state = state,
                        bottomContentPadding = bottomNavPadding,
                        onSync = { viewModel.syncNow(notifyNewTasks = true) },
                        onEnsureForumsLoaded = { viewModel.ensureForumsLoaded(false) },
                        onToggleTask = viewModel::toggleTaskCompleted,
                        onMarkSeen = viewModel::markTaskSeen,
                        onCreateNoteForCourse = openCourseNote,
                        onLoadHomeworkDetail = viewModel::loadHomeworkDetail,
                        onLoadForumDiscussion = viewModel::loadForumDiscussion,
                        onOpenUrl = { url ->
                            val normalized = normalizeResourceUrl(url)
                            if (isUnapInternalUrl(normalized)) {
                                activeDocument = DocumentOpenRequest(
                                    title = "Documento de tarea",
                                    url = normalized
                                )
                            } else {
                                openExternalUri(context, normalized)
                            }
                        }
                    )

                    MainTab.SCHEDULE -> ScheduleScreen(
                        state = state,
                        bottomContentPadding = bottomNavPadding,
                        onSync = { viewModel.syncNow(notifyNewTasks = false) },
                        onAddManualSlot = viewModel::saveManualScheduleSlot,
                        onDeleteSlot = viewModel::deleteScheduleSlot,
                        onOpenCourse = { course -> activeCourse = course }
                    )

                    MainTab.MORE -> {
                        AnimatedContent(
                            targetState = moreDestination,
                            transitionSpec = {
                                moreTransitionSpec(
                                    initial = initialState,
                                    target = targetState,
                                    reducedMotion = reducedMotion
                                )
                            },
                            label = "moreSubNav"
                        ) { dest ->
                            when (dest) {
                                MoreDestination.HUB -> MoreHubScreen(
                                    state = state,
                                    bottomContentPadding = bottomNavPadding,
                                    onNavigate = { moreDestination = it }
                                )

                                MoreDestination.PROFILE -> StudentProfileScreen(
                                    state = state,
                                    bottomContentPadding = bottomNavPadding,
                                    onEnsureProfileLoaded = viewModel::ensureProfileLoaded,
                                    onBack = { moreDestination = MoreDestination.HUB }
                                )

                                MoreDestination.INTRANET_GRADES,
                                MoreDestination.CURRICULUM,
                                MoreDestination.ACADEMIC_HISTORY,
                                MoreDestination.ATTENDANCE,
                                MoreDestination.ADMIN_PROCEDURES -> IntranetModuleScreen(
                                    destination = dest,
                                    state = state,
                                    bottomContentPadding = bottomNavPadding,
                                    onLoadIntranet = viewModel::loadIntranetOverview,
                                    onLoadIntranetSection = viewModel::loadIntranetSection,
                                    onEnsureGradesLoaded = viewModel::ensureGradesLoaded,
                                    onBack = { moreDestination = MoreDestination.HUB }
                                )

                                MoreDestination.NOTES -> NotesScreen(
                                    state = state,
                                    bottomContentPadding = bottomNavPadding,
                                    initialCoursePreset = presetCourseForNote,
                                    onClearCoursePreset = { presetCourseForNote = null },
                                    onBack = { moreDestination = MoreDestination.HUB },
                                    onSaveNote = viewModel::saveNote,
                                    onDeleteNote = viewModel::deleteNote,
                                    onTogglePin = viewModel::toggleNotePinned
                                )

                                MoreDestination.FORUMS -> ForumsAndNoticesScreen(
                                    state = state,
                                    bottomContentPadding = bottomNavPadding,
                                    initialTab = 0,
                                    onEnsureForumsLoaded = viewModel::ensureForumsLoaded,
                                    onLoadForumDiscussion = viewModel::loadForumDiscussion,
                                    onBack = { moreDestination = MoreDestination.HUB }
                                )

                                MoreDestination.ANNOUNCEMENTS -> ForumsAndNoticesScreen(
                                    state = state,
                                    bottomContentPadding = bottomNavPadding,
                                    initialTab = 1,
                                    onEnsureForumsLoaded = viewModel::ensureForumsLoaded,
                                    onLoadForumDiscussion = viewModel::loadForumDiscussion,
                                    onBack = { moreDestination = MoreDestination.HUB }
                                )

                                MoreDestination.BUS -> BusScreen(
                                    state = state,
                                    bottomContentPadding = bottomNavPadding,
                                    onBack = { moreDestination = MoreDestination.HUB },
                                    onToggleFavorite = viewModel::toggleBusFavorite
                                )

                                MoreDestination.PORTAL -> WebPortalScreen(
                                    bottomContentPadding = bottomNavPadding,
                                    onPreparePortalCookies = { urls ->
                                        viewModel.syncCookiesToWebView(urls)
                                    },
                                    onSyncCookiesFromWebView = viewModel::importPortalCookies,
                                    onBack = { moreDestination = MoreDestination.HUB }
                                )

                                MoreDestination.SETTINGS -> SettingsScreen(
                                    state = state,
                                    bottomContentPadding = bottomNavPadding,
                                    onBack = { moreDestination = MoreDestination.HUB },
                                    onSyncNow = { viewModel.syncNow(notifyNewTasks = true) },
                                    onThemeModeChange = viewModel::setThemeMode,
                                    onAutoSyncChange = { enabled ->
                                        viewModel.setAutoSyncEnabled(enabled) {}
                                    },
                                    onNotificationsChange = viewModel::setNotificationsEnabled,
                                    onCheckForUpdates = { viewModel.checkForUpdates(manual = true) },
                                    onLogout = viewModel::logout
                                )
                            }
                        }
                    }
                }
                }
            }

            // Full-screen Course Detail overlay (when opening a course from any tab)
            AnimatedVisibility(
                visible = activeCourse != null && portalOverlayUrl == null,
                enter = slideInHorizontally(appleSpringDefault(reducedMotion)) { it / 3 } +
                    fadeIn(appleSpringDefault(reducedMotion)),
                exit = slideOutHorizontally(appleSpringDefault(reducedMotion)) { it / 3 } +
                    fadeOut(appleSpringDefault(reducedMotion))
            ) {
                activeCourse?.let { course ->
                    CourseDetailScreen(
                        course = course,
                        state = state,
                        bottomContentPadding = bottomNavPadding,
                        onLoadDetail = viewModel::loadCourseFullDetail,
                        onLoadHomeworkDetail = viewModel::loadHomeworkDetail,
                        onLoadForumDiscussion = viewModel::loadForumDiscussion,
                        onCreateNoteForCourse = openCourseNote,
                        onOpenUrlInPortal = { url -> portalOverlayUrl = url },
                        onOpenDocument = { req -> activeDocument = req },
                        onBack = { activeCourse = null }
                    )
                }
            }

            activeDocument?.let { docReq ->
                DocumentViewerDialog(
                    request = docReq,
                    onDownloadFile = viewModel::downloadAuthenticatedFile,
                    onDismiss = { activeDocument = null }
                )
            }

            // Direct in-app viewer overlay (Syllabus, PDFs, Chat, Conferences)
            AnimatedVisibility(
                visible = portalOverlayUrl != null,
                enter = fadeIn(appleSpringDefault(reducedMotion)),
                exit = fadeOut(appleSpringDefault(reducedMotion))
            ) {
                portalOverlayUrl?.let { url ->
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        WebPortalScreen(
                            bottomContentPadding = bottomNavPadding,
                            initialUrl = url,
                            onPreparePortalCookies = { urls ->
                                viewModel.syncCookiesToWebView(urls)
                            },
                            onSyncCookiesFromWebView = viewModel::importPortalCookies,
                            onBack = { portalOverlayUrl = null }
                        )
                    }
                }
            }

            // Floating Top In-App Academic Notification Banner (iOS style)
            AnimatedVisibility(
                visible = state.inAppAlert != null,
                enter = slideInVertically(appleSpringDefault(reducedMotion)) { -it } +
                    fadeIn(appleSpringDefault(reducedMotion)),
                exit = slideOutVertically(appleSpringDefault(reducedMotion)) { -it } +
                    fadeOut(appleSpringDefault(reducedMotion)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                state.inAppAlert?.let { alert ->
                    TopAcademicAlertBanner(
                        alert = alert,
                        onTap = {
                            viewModel.dismissInAppAlert()
                            navigateToTab(MainTab.TASKS)
                        },
                        onDismiss = viewModel::dismissInAppAlert
                    )
                }
            }

            // Controlled frosted bottom blur veil around floating navigation capsule
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.56f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.88f)
                            )
                        )
                    )
            ) {
                FloatingBottomNavBar(
                    selectedTab = currentTab,
                    pendingTaskCount = state.pendingTaskCount,
                    onSelectTab = navigateToTab,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun FloatingBottomNavBar(
    selectedTab: MainTab,
    pendingTaskCount: Int,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val extended = LocalUnAppColors.current
    val reducedMotion = rememberReducedMotionEnabled()

    val navShape = RoundedCornerShape(28.dp)
    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth(),
        shape = navShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, extended.glassBorder.copy(alpha = 0.92f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
                        )
                    )
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                val pillAlpha by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = appleSpringDefault(reducedMotion),
                    label = "navPillAlpha"
                )
                val pillShape = RoundedCornerShape(22.dp)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(pillShape)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = pillAlpha * 0.85f)
                        )
                        .applePressable(pressedScale = 0.94f) { onSelectTab(tab) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer {
                                        val s = if (selected) 1.06f else 1f
                                        scaleX = s
                                        scaleY = s
                                    }
                            )
                            if (tab == MainTab.TASKS && pendingTaskCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(7.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopAcademicAlertBanner(
    alert: InAppAcademicAlert,
    onTap: () -> Unit,
    onDismiss: () -> Unit
) {
    val extended = LocalUnAppColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .applePressable(onClick = onTap),
        shape = RoundedCornerShape(22.dp),
        color = extended.glassSurface,
        shadowElevation = 6.dp,
        border = BorderStroke(0.8.dp, extended.glassBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = UnAppIcons.Bell,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = alert.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    alert.courseName?.takeIf { it.isNotBlank() }?.let { cName ->
                        ApplePillBadge(text = cName)
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = UnAppIcons.Close,
                    contentDescription = "Cerrar notificación",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun AnimatedContentTransitionScope<MoreDestination>.moreTransitionSpec(
    initial: MoreDestination,
    target: MoreDestination,
    reducedMotion: Boolean
): ContentTransform {
    if (reducedMotion) {
        return fadeIn(appleSpringDefault(true)) togetherWith fadeOut(appleSpringDefault(true))
    }
    // Symmetric spatial axis: enter from right when drilling into detail, exit to right when backing to hub
    val forward = initial == MoreDestination.HUB && target != MoreDestination.HUB
    val enterOffset: (Int) -> Int = { w -> if (forward) w / 3 else -w / 4 }
    val exitOffset: (Int) -> Int = { w -> if (forward) -w / 4 else w / 3 }
    return (slideInHorizontally(appleSpringDefault(), initialOffsetX = enterOffset) +
        fadeIn(appleSpringDefault())) togetherWith
        (slideOutHorizontally(appleSpringDefault(), targetOffsetX = exitOffset) +
            fadeOut(appleSpringDefault()))
}

@Composable
private fun AppUpdatePromptSheet(
    update: AppUpdateInfo,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var isDownloading by remember(update.apkUrl) { mutableStateOf(false) }
    var progress by remember(update.apkUrl) { mutableFloatStateOf(0f) }
    var downloadError by remember(update.apkUrl) { mutableStateOf<String?>(null) }

    AppleBottomSheet(onDismiss = { if (!isDownloading) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ApplePillBadge(text = "Actualización disponible")
            Text(
                text = "Nueva versión v${update.versionName}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = update.releaseNotes.ifBlank {
                    "Hay una nueva versión lista para instalar. Tus apuntes y sesión se conservarán."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isDownloading) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { if (progress > 0f) progress else 0.05f },
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (progress > 0f) {
                            "Descargando actualización (${(progress * 100).toInt()}%)..."
                        } else {
                            "Descargando paquete de actualización..."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            downloadError?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            AppleSubtleDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isDownloading,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Más tarde")
                }
                Button(
                    onClick = {
                        if (!AppUpdateManager.canInstallPackages(context)) {
                            Toast.makeText(
                                context,
                                "Activa 'Permitir desde esta fuente' para instalar la actualización.",
                                Toast.LENGTH_LONG
                            ).show()
                            AppUpdateManager.openUnknownSourcesSettings(context)
                            return@Button
                        }
                        isDownloading = true
                        downloadError = null
                        progress = 0f
                        scope.launch {
                            val result = AppUpdateManager.downloadApk(context, update.apkUrl) { p ->
                                progress = p
                            }
                            isDownloading = false
                            result.onSuccess { file ->
                                val launched = AppUpdateManager.launchApkInstaller(context, file)
                                if (launched) {
                                    onDismiss()
                                } else {
                                    downloadError = "No se pudo abrir el instalador del sistema."
                                }
                            }.onFailure { t ->
                                downloadError = t.message ?: "Error al descargar la actualización."
                            }
                        }
                    },
                    enabled = !isDownloading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text(if (isDownloading) "Descargando..." else "Instalar ahora")
                }
            }
        }
    }
}
