/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.screens

import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
 * @param modifier Optional modifier for layout customization
 * @param viewModel [MainViewModel] providing chat state and actions
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
        Text(
            text = "Download model",
            style = MaterialTheme.typography.headlineSmall
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { viewModel.startModelDownload() },
                enabled = ui.canStart
            ) {
                Text(
                    when {
                        ui.isRunning -> "Downloading..."
                        ui.finishedOk -> "Finished"
                        else -> "Start download"
                    }
                )
            }
            OutlinedButton(
                onClick = { viewModel.cancelModelDownload() },
                enabled = ui.canCancel
            ) {
                Text("Cancel")
            }
        }

        if (ui.isRunning || ui.finishedOk) {
            val progress = when {
                ui.fileProgress in 0..100 -> ui.fileProgress / 100f
                ui.total > 0 -> (ui.done.toFloat() / ui.total.toFloat()).coerceIn(0f, 1f)
                else -> 0f
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (ui.fileProgress in 0..100) {
                Text(text = "Current file progress: ${ui.fileProgress}%")
            }
            if (ui.finishedOk) {
                Text(text = "Download finished.")
            }
        }
    }
}
