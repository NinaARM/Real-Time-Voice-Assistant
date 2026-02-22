/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.viewmodels

import com.arm.voiceassistant.utils.Constants.ContentStates
import com.arm.voiceassistant.utils.Constants.INITIAL_METRICS_VALUE

/**
 * Main screen UI state.
 *
 * @property contentState State used to determine UI content to display.
 * @property error Error information.
 * @property userText Transcribed input from user.
 * @property responseText Text response from voice assistant.
 * @property imagePath Path to the selected image.
 * @property recTime Formatted duration of current recording.
 * @property recTimeMs Duration of current recording in ms.
 * @property playingAudio Whether speech response is playing.
 * @property displayPerformance Whether performance metrics should be displayed.
 * @property sttTime Whisper model time taken.
 * @property llmEncodeTPS LLM model encode tokens/second.
 * @property llmDecodeTPS LLM model decode tokens/second.
 * @property isTTSEnabled Whether TTS is enabled.
 * @property ttsWarningMessage Optional TTS warning message.
 */
data class MainUiState(
    val contentState: ContentStates = ContentStates.Idle,
    val error: Error = Error(),
    val userText: String = "",
    val responseText: String = "",
    val imagePath: String = "",
    val recTime: String = "00:00",
    val recTimeMs: Long = 0,
    val playingAudio: Boolean = false,
    val displayPerformance: Boolean = false,
    val sttTime: String = INITIAL_METRICS_VALUE,
    val llmEncodeTPS: String = INITIAL_METRICS_VALUE,
    val llmDecodeTPS: String = INITIAL_METRICS_VALUE,
    val isTTSEnabled: Boolean = false,
    val ttsWarningMessage: String? = null
)

/**
 * Download UI state.
 *
 * @property done Bytes downloaded so far (clamped to Int range).
 * @property total Total bytes expected for the download (clamped to Int range).
 * @property fileProgress Percentage 0..100, or -1 when unknown.
 * @property canStart Whether the UI should allow starting a download.
 * @property canCancel Whether the UI should allow canceling an in-progress download.
 * @property isRunning Whether a download is currently in progress.
 * @property finishedOk Whether the download completed successfully.
 */
data class DownloadUiState(
    val done: Int = 0,
    val total: Int = 0,
    val fileProgress: Int = -1,              // 0..100 or -1 unknown
    val canStart: Boolean = true,
    val canCancel: Boolean = false,
    val isRunning: Boolean = false,
    val finishedOk: Boolean = false
)

/**
 * Class to hold error information.
 *
 * @property state True if there is an error.
 * @property contextCapacity True if the error is due to LLM context capacity.
 * @property message Error message to display.
 */
data class Error(
    val state: Boolean = false,
    val contextCapacity: Boolean = false,
    val message: String = ""
)
