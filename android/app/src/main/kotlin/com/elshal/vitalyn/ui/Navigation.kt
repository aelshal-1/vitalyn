package com.elshal.vitalyn.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.UUID

/**
 * A push/pop stack of screens for one tab, the Compose stand-in for SwiftUI's NavigationStack:
 * `navigator.push { ActivityScreen() }` is `NavigationLink { ActivityView() }`.
 */
class Navigator {
    class Entry(val id: String, val content: @Composable () -> Unit)

    val stack = mutableStateListOf<Entry>()

    fun push(content: @Composable () -> Unit) {
        stack.add(Entry(UUID.randomUUID().toString(), content))
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    fun popToRoot() = stack.clear()
}

val LocalNavigator = compositionLocalOf { Navigator() }

/** Shows the top of `navigator`'s stack, or `root` when it's empty. System back pops. */
@Composable
fun NavStack(navigator: Navigator, root: @Composable () -> Unit) {
    val holder = rememberSaveableStateHolder()
    BackHandler(enabled = navigator.stack.isNotEmpty()) { navigator.pop() }
    val top = navigator.stack.lastOrNull()
    androidx.compose.runtime.CompositionLocalProvider(LocalNavigator provides navigator) {
        if (top == null) holder.SaveableStateProvider("root") { root() } else holder.SaveableStateProvider(top.id) { top.content() }
    }
}

/** Header for a pushed screen: back button and bilingual title. */
@Composable
fun ScreenHeader(ar: String, en: String, onBack: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع — Back", onBack, background = VColor.surface, tint = VColor.ink)
            Spacer(Modifier.width(10.dp))
        } else {
            Spacer(Modifier.width(12.dp))
        }
        BiText(ar, en, size = 20f, weight = 700, modifier = Modifier.weight(1f))
        trailing()
    }
}

/**
 * A pushed screen: header with back, then scrolling content on the ground color.
 * Use it for everything reached with `navigator.push`.
 */
@Composable
fun PushedScreen(
    ar: String,
    en: String,
    trailing: @Composable () -> Unit = {},
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val navigator = LocalNavigator.current
    Column(
        Modifier
            .fillMaxSize()
            .background(VColor.ground)
            .statusBarsPadding(),
    ) {
        ScreenHeader(ar, en, onBack = { navigator.pop() }, trailing = trailing)
        val body = Modifier
            .fillMaxWidth()
            .weight(1f)
            .let { if (scroll) it.verticalScroll(rememberScrollState()) else it }
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
        Column(body, verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/**
 * A full-screen sheet over the current screen (SwiftUI `.sheet`). Back or the close button calls `onDismiss`.
 * Content gets the ground background, safe-area and keyboard padding; scroll it yourself if needed,
 * or use [SheetScaffold] inside for a header + scrolling column.
 */
@Composable
fun FullSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(VColor.ground)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            content()
        }
    }
}

/** Header (title + close) and a scrolling column, for use inside [FullSheet]. */
@Composable
fun SheetScaffold(
    ar: String,
    en: String,
    onClose: () -> Unit,
    trailing: @Composable () -> Unit = {},
    scroll: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BiText(ar, en, size = 18f, weight = 700, modifier = Modifier.weight(1f))
            trailing()
            Spacer(Modifier.width(8.dp))
            CircleIconButton(Icons.Rounded.Close, "اقفل — Close", onClose, background = VColor.surface, tint = VColor.ink, size = 40.dp)
        }
        val body = Modifier
            .fillMaxWidth()
            .weight(1f)
            .let { if (scroll) it.verticalScroll(rememberScrollState()) else it }
            .padding(contentPadding)
        Column(body, verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/** Bottom padding helper for screens that end above the tab bar / gesture area. */
fun Modifier.screenBottomPadding(): Modifier = this.navigationBarsPadding()
