/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Describes the remote model files declared in the application manifest.
 *
 * @param models files that the application can download and verify
 */
@Serializable
data class Manifest(
    val models: List<RemoteFileSpec> = emptyList()
)

/**
 * Describes a remote file and its local download destination.
 *
 * @param destination path of the downloaded file relative to the application download directory
 * @param repoId Hugging Face repository identifier
 * @param filename name of the file in the remote repository
 * @param revision repository revision containing the file
 * @param sha256 expected SHA-256 checksum, or `null` when checksum verification is not required
 */
@Serializable
data class RemoteFileSpec(
    val destination: String = "",
    @SerialName("repo_id") val repoId: String = "",
    val filename: String = "",
    val revision: String = "main",
    @SerialName("sha256sum") val sha256: String? = null
)
