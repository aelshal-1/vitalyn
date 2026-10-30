package com.elshal.vitalyn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.elshal.vitalyn.LocalApp
import com.elshal.vitalyn.OpenTarget
import com.elshal.vitalyn.features.coach.CoachScreen
import com.elshal.vitalyn.features.food.FoodScreen
import com.elshal.vitalyn.features.food.FoodTab
import com.elshal.vitalyn.features.profile.OnboardingScreen
import com.elshal.vitalyn.features.profile.ProfileScreen
import com.elshal.vitalyn.features.progress.ProgressHubScreen
import com.elshal.vitalyn.features.today.TodayScreen

enum class AppTab(val ar: String, val en: String, val icon: ImageVector) {
    TODAY("اليوم", "Today", Icons.Rounded.Home),
    FOOD("الأكل", "Food", Icons.Rounded.Restaurant),
    COACH("المدرب", "Coach", Icons.Rounded.AutoAwesome),
    PROGRESS("التقدم", "Progress", Icons.Rounded.BarChart),
    ME("أنا", "Me", Icons.Rounded.Person),
}

/** Phones get the bilingual bottom bar; tablets get a side rail. First launch shows onboarding. */
@Composable
fun RootScreen() {
    val app = LocalApp.current
    val state by app.store.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(AppTab.TODAY) }
    val navigators = remember { AppTab.entries.associateWith { Navigator() } }

    LaunchedEffect(app.openTarget) {
        when (app.openTarget) {
            OpenTarget.PLAN -> {
                app.foodTab = FoodTab.PLAN
                navigators.getValue(AppTab.FOOD).popToRoot()
                tab = AppTab.FOOD
            }
            OpenTarget.COACH -> {
                navigators.getValue(AppTab.COACH).popToRoot()
                tab = AppTab.COACH
            }
            null -> return@LaunchedEffect
        }
        app.openTarget = null
    }

    Box(Modifier.fillMaxSize().background(VColor.ground)) {
        if (state.profile == null) {
            OnboardingScreen(onDone = { tab = AppTab.TODAY })
            return@Box
        }
        val holder = rememberSaveableStateHolder()
        val screen = @Composable {
            holder.SaveableStateProvider(tab.name) {
                NavStack(navigators.getValue(tab)) {
                    when (tab) {
                        AppTab.TODAY -> TodayScreen(
                            openProfile = { tab = AppTab.ME },
                            openPlan = {
                                app.foodTab = FoodTab.PLAN
                                tab = AppTab.FOOD
                            },
                        )
                        AppTab.FOOD -> FoodScreen()
                        AppTab.COACH -> CoachScreen()
                        AppTab.PROGRESS -> ProgressHubScreen()
                        AppTab.ME -> ProfileScreen()
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= 720.dp) {
                Row(Modifier.fillMaxSize()) {
                    SideRail(tab) { tab = it }
                    Box(Modifier.weight(1f).fillMaxHeight()) { screen() }
                }
            } else {
                // Stacked (not overlaid) so scrolling content ends above the bar.
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { screen() }
                    BilingualTabBar(tab) { tab = it }
                }
            }
        }
    }
}

@Composable
private fun BilingualTabBar(selection: AppTab, onSelect: (AppTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(VColor.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(VColor.line))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 6.dp)
                .padding(top = 6.dp),
        ) {
            AppTab.entries.forEach { item ->
                val selected = item == selection
                val color = if (selected) VColor.primary else VColor.muted
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .clickable(role = Role.Tab) { onSelect(item) }
                        .semantics {
                            contentDescription = "${item.ar} — ${item.en}"
                            this.selected = selected
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(item.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                    Text(item.ar, style = VFont.style(11f, 500, color))
                    Text(item.en, style = VFont.style(9f, 400, color))
                }
            }
        }
    }
}

@Composable
private fun SideRail(selection: AppTab, onSelect: (AppTab) -> Unit) {
    Column(
        Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(VColor.surface)
            .statusBarsPadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Vitalyn", style = VFont.style(24f, 700, VColor.ink), modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp))
        AppTab.entries.forEach { item ->
            val selected = item == selection
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (selected) VColor.primarySoft else VColor.surface, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Tab) { onSelect(item) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(item.icon, contentDescription = null, tint = if (selected) VColor.primaryDark else VColor.ink)
                Spacer(Modifier.width(12.dp))
                BiText(item.ar, item.en, size = 15f, weight = if (selected) 600 else 400, color = if (selected) VColor.primaryDark else VColor.ink)
            }
        }
    }
}
