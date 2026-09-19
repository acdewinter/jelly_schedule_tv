package dev.jellyschedule.tv.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.jellyschedule.tv.appGraph
import dev.jellyschedule.tv.ui.connect.ConnectScreen
import dev.jellyschedule.tv.ui.details.ProgrammeDetailsScreen
import dev.jellyschedule.tv.ui.guide.GuideScreen
import dev.jellyschedule.tv.ui.player.PlayerScreen
import dev.jellyschedule.tv.ui.recordings.RecordingsScreen
import dev.jellyschedule.tv.ui.settings.SettingsScreen
import dev.jellyschedule.tv.ui.signin.SignInScreen
import dev.jellyschedule.tv.ui.tunein.TuneInScreen

/** Single-activity navigation. The session flow decides between connect, sign-in and the channel. */
@Composable
fun AppRoot() {
    val graph = LocalContext.current.appGraph
    val session by graph.sessions.session.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val start: Any = remember {
        when {
            session.isSignedIn -> TuneInRoute
            session.hasServer -> SignInRoute
            else -> ConnectRoute
        }
    }

    LaunchedEffect(session.isSignedIn, session.hasServer) {
        val destination = navController.currentBackStackEntry?.destination
        val onAuthScreen = destination?.hasRoute<SignInRoute>() == true || destination?.hasRoute<ConnectRoute>() == true
        if (session.isSignedIn && onAuthScreen) {
            navController.navigate(TuneInRoute) { popUpTo(0) { inclusive = true } }
        } else if (!session.isSignedIn && !onAuthScreen && destination != null) {
            navController.navigate(if (session.hasServer) SignInRoute else ConnectRoute) { popUpTo(0) { inclusive = true } }
        }
    }

    NavHost(navController = navController, startDestination = start) {
        composable<ConnectRoute> {
            ConnectScreen(onConnected = { navController.navigate(SignInRoute) })
        }
        composable<SignInRoute> {
            SignInScreen(onChangeServer = { navController.navigate(ConnectRoute) })
        }
        composable<TuneInRoute> {
            TuneInScreen(
                onPlay = { airing, mode -> navController.openPlayer(airing, mode) },
                onGuide = { navController.navigate(GuideRoute) },
                onRecordings = { navController.navigate(RecordingsRoute) },
                onSettings = { navController.navigate(SettingsRoute) },
                onChangeServer = { navController.navigate(ConnectRoute) },
            )
        }
        composable<PlayerRoute> { entry ->
            val route = entry.toRoute<PlayerRoute>()
            PlayerScreen(
                route = route,
                onClose = { navController.popBackStack() },
                onOpenGuide = {
                    navController.navigate(GuideRoute) { popUpTo<TuneInRoute>() }
                },
            )
        }
        composable<GuideRoute> {
            GuideScreen(
                onOpenDetails = { airing -> navController.openDetails(airing) },
                onPlay = { airing, mode -> navController.openPlayer(airing, mode) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<DetailsRoute> { entry ->
            val route = entry.toRoute<DetailsRoute>()
            ProgrammeDetailsScreen(
                initial = route.airing,
                onPlay = { airing, mode, resume -> navController.openPlayer(airing, mode, resume) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<RecordingsRoute> {
            RecordingsScreen(
                onPlay = { airing, resume -> navController.openPlayer(airing, PlaybackMode.Recording, resume) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() }, onChangeServer = { navController.navigate(ConnectRoute) })
        }
    }
}
