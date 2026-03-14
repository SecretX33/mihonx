package eu.kanade.presentation.manga

import android.widget.Toast
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditMangaDialog(
    manga: Manga,
    onDismissRequest: () -> Unit,
    onSave: (
        customTitle: String?,
        customAuthor: String?,
        customArtist: String?,
        customDescription: String?,
        customGenre: List<String>?,
        customStatus: Long?,
    ) -> Unit,
) {
    var title by remember { mutableStateOf(manga.customTitle.orEmpty()) }
    var author by remember { mutableStateOf(manga.customAuthor.orEmpty()) }
    var artist by remember { mutableStateOf(manga.customArtist.orEmpty()) }
    var description by remember { mutableStateOf(manga.customDescription.orEmpty()) }
    var status by remember { mutableLongStateOf(manga.customStatus ?: manga.status) }
    val tags = remember { mutableStateListOf(*(manga.customGenre ?: manga.genre ?: emptyList()).toTypedArray()) }
    var hasEditedTags by remember { mutableStateOf(manga.customGenre != null) }
    var newTag by remember { mutableStateOf("") }

    val context = LocalContext.current
    val longPressToFillMessage = stringResource(MR.strings.long_press_to_fill_default)
    val holdTimeoutMs = 1000L

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.edit_manga_info)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
            ) {
                // Title
                val titleFocusRequester = remember { FocusRequester() }
                Box {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(MR.strings.title)) },
                        placeholder = { Text(manga.title.trimIfTooLong()) },
                        modifier = Modifier.fillMaxWidth().focusRequester(titleFocusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        trailingIcon = if (title.isNotBlank()) {
                            {
                                IconButton(onClick = { title = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(MR.strings.action_reset),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    )
                    if (title.isBlank()) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        val holdStart = System.currentTimeMillis()
                                        val up = withTimeoutOrNull(holdTimeoutMs) {
                                            waitForUpOrCancellation()
                                        }
                                        val holdDuration = System.currentTimeMillis() - holdStart
                                        if (up == null && holdDuration >= holdTimeoutMs - 100L) {
                                            title = manga.title
                                            titleFocusRequester.requestFocus()
                                            Toast.makeText(context, longPressToFillMessage, Toast.LENGTH_SHORT).show()
                                        } else if (up != null) {
                                            titleFocusRequester.requestFocus()
                                        }
                                    }
                                },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Author
                val authorFocusRequester = remember { FocusRequester() }
                Box {
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text(stringResource(MR.strings.author)) },
                        placeholder = { Text((manga.author.orEmpty()).trimIfTooLong()) },
                        modifier = Modifier.fillMaxWidth().focusRequester(authorFocusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        trailingIcon = if (author.isNotBlank()) {
                            {
                                IconButton(onClick = { author = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(MR.strings.action_reset),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    )
                    if (author.isBlank()) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        val holdStart = System.currentTimeMillis()
                                        val up = withTimeoutOrNull(holdTimeoutMs) {
                                            waitForUpOrCancellation()
                                        }
                                        val holdDuration = System.currentTimeMillis() - holdStart
                                        if (up == null && holdDuration >= holdTimeoutMs - 100L) {
                                            author = manga.author.orEmpty()
                                            authorFocusRequester.requestFocus()
                                            Toast.makeText(context, longPressToFillMessage, Toast.LENGTH_SHORT).show()
                                        } else if (up != null) {
                                            authorFocusRequester.requestFocus()
                                        }
                                    }
                                },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Artist
                val artistFocusRequester = remember { FocusRequester() }
                Box {
                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        label = { Text(stringResource(MR.strings.artist)) },
                        placeholder = { Text(manga.artist.orEmpty().trimIfTooLong()) },
                        modifier = Modifier.fillMaxWidth().focusRequester(artistFocusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        trailingIcon = if (artist.isNotBlank()) {
                            {
                                IconButton(onClick = { artist = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(MR.strings.action_reset),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    )
                    if (artist.isBlank()) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        val holdStart = System.currentTimeMillis()
                                        val up = withTimeoutOrNull(holdTimeoutMs) {
                                            waitForUpOrCancellation()
                                        }
                                        val holdDuration = System.currentTimeMillis() - holdStart
                                        if (up == null && holdDuration >= holdTimeoutMs - 100L) {
                                            artist = manga.artist.orEmpty()
                                            artistFocusRequester.requestFocus()
                                            Toast.makeText(context, longPressToFillMessage, Toast.LENGTH_SHORT).show()
                                        } else if (up != null) {
                                            artistFocusRequester.requestFocus()
                                        }
                                    }
                                },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Description
                val descriptionFocusRequester = remember { FocusRequester() }
                Box {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(MR.strings.description)) },
                        placeholder = { Text((manga.description.orEmpty()).trimIfTooLong()) },
                        modifier = Modifier.fillMaxWidth().focusRequester(descriptionFocusRequester),
                        minLines = 3,
                        maxLines = 5,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        trailingIcon = if (description.isNotBlank()) {
                            {
                                IconButton(onClick = { description = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(MR.strings.action_reset),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    )
                    if (description.isBlank()) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        val holdStart = System.currentTimeMillis()
                                        val up = withTimeoutOrNull(holdTimeoutMs) {
                                            waitForUpOrCancellation()
                                        }
                                        val holdDuration = System.currentTimeMillis() - holdStart
                                        if (up == null && holdDuration >= holdTimeoutMs - 100L) {
                                            description = manga.description.orEmpty()
                                            descriptionFocusRequester.requestFocus()
                                            Toast.makeText(context, longPressToFillMessage, Toast.LENGTH_SHORT).show()
                                        } else if (up != null) {
                                            descriptionFocusRequester.requestFocus()
                                        }
                                    }
                                },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Status dropdown
                var statusExpanded by remember { mutableStateOf(false) }
                val statusOptions = listOf(
                    SManga.ONGOING.toLong() to MR.strings.ongoing,
                    SManga.COMPLETED.toLong() to MR.strings.completed,
                    SManga.LICENSED.toLong() to MR.strings.licensed,
                    SManga.PUBLISHING_FINISHED.toLong() to MR.strings.publishing_finished,
                    SManga.CANCELLED.toLong() to MR.strings.cancelled,
                    SManga.ON_HIATUS.toLong() to MR.strings.on_hiatus,
                    0L to MR.strings.unknown,
                )
                ExposedDropdownMenuBox(
                    expanded = statusExpanded,
                    onExpandedChange = { statusExpanded = it },
                ) {
                    OutlinedTextField(
                        value = stringResource(
                            statusOptions.firstOrNull { it.first == status }?.second ?: MR.strings.unknown,
                        ),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(MR.strings.status)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false },
                    ) {
                        statusOptions.forEach { (value, labelRes) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(labelRes)) },
                                onClick = {
                                    status = value
                                    statusExpanded = false
                                },
                                trailingIcon = if (value == manga.status) {
                                    {
                                        Icon(
                                            imageVector = Icons.Outlined.Public,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tags/Genre chips
                Text(
                    text = stringResource(MR.strings.genre),
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = {
                                tags.remove(tag)
                                hasEditedTags = true
                            },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newTag,
                        onValueChange = { newTag = it },
                        label = { Text(stringResource(MR.strings.add_tag)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        trailingIcon = if (newTag.isNotBlank()) {
                            {
                                IconButton(onClick = { newTag = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(MR.strings.action_reset),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            if (newTag.isNotBlank()) {
                                tags.add(newTag.trim())
                                newTag = ""
                                hasEditedTags = true
                            }
                        },
                    ) {
                        Text(stringResource(MR.strings.action_add))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                TextButton(
                    onClick = {
                        tags.clear()
                        tags.addAll(manga.genre ?: emptyList())
                        hasEditedTags = false
                    },
                    enabled = hasEditedTags,
                ) {
                    Text(stringResource(MR.strings.reset_to_default))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        title.ifBlank { null },
                        author.ifBlank { null },
                        artist.ifBlank { null },
                        description.ifBlank { null },
                        if (hasEditedTags) tags.toList().ifEmpty { null } else null,
                        status.takeIf { it != manga.status },
                    )
                    onDismissRequest()
                },
            ) {
                Text(stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

private fun String.trimIfTooLong(): String {
    val maxLength = 50
    return if (length > maxLength) take(maxLength).trim() + "…" else this
}
