package com.attendance.rollcheck.ui.components.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.attendance.rollcheck.ui.theme.AppBackground
import com.attendance.rollcheck.ui.theme.TextPrimary
import com.attendance.rollcheck.ui.theme.TextSecondary

/**
 * Standard top app bar used across all screens.
 * Pass onBack = null to hide the back arrow (e.g. on HomeScreen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RollCheckTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text       = title,
                fontWeight = FontWeight.Bold,
                fontSize   = 18.sp,
                color      = TextPrimary
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
                }
            }
        },
        actions = { actions() },
        colors  = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
    )
}