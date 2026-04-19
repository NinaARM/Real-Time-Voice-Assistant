/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.screens

import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arm.voiceassistant.viewmodels.MainViewModel

/**
 * Download screen for the voice assistant.
 *
 * Displays a simple download UI currently used to test models download
 *
 * @param viewModel [MainViewModel] providing chat state and actions
 * @param modifier Optional modifier for layout customization
 */
@Composable
fun DownloadScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val ui by viewModel.downloadUiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { viewModel.downloadAppModels() },
            enabled = !ui.isRunning
        ) {
            Text("Download app models")
        }
    }
}
