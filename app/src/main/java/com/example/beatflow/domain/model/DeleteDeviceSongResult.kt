package com.example.beatflow.domain.model

/**
 * Result representation for local audio file deletion via MediaStore.
 * - [Success]: File successfully removed from storage and MediaStore.
 * - [RequiresConfirmation]: Android Scoped Storage requires user consent via system dialog.
 *   Contains the IntentSender (as Any to decouple domain from Android framework).
 * - [Failure]: File deletion failed with an explanatory message.
 */
sealed interface DeleteDeviceSongResult {
    data object Success : DeleteDeviceSongResult
    data class RequiresConfirmation(val intentSender: Any) : DeleteDeviceSongResult
    data class Failure(val message: String) : DeleteDeviceSongResult
}
