package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import tachiyomi.domain.manga.model.ScanlatorFillerPages
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ScanlatorFillerPagesDialog(
    availableScanlators: Set<String>,
    hasUnlabeledChapters: Boolean,
    rules: Map<String, ScanlatorFillerPages>,
    onDismissRequest: () -> Unit,
    onConfirm: (Map<String, ScanlatorFillerPages>) -> Unit,
) {
    val available = remember(availableScanlators, hasUnlabeledChapters) {
        (availableScanlators + (if (hasUnlabeledChapters) setOf("") else emptySet()))
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }
    val drafts = remember(rules) {
        mutableStateMapOf<String, Pair<String, String>>().apply {
            rules.forEach { (scanlator, counts) ->
                put(scanlator, counts.beginning.toString() to counts.end.toString())
            }
        }
    }
    var addExpanded by remember { mutableStateOf(false) }
    val remaining = available.filterNot(drafts::containsKey)
    val valid = drafts.values.all { (beginning, end) ->
        beginning.toIntOrNull() != null && end.toIntOrNull() != null
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(MR.strings.skip_filler_pages)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box {
                    OutlinedButton(onClick = { addExpanded = true }, enabled = remaining.isNotEmpty()) {
                        Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(MR.strings.filler_pages_add_scanlator))
                    }
                    DropdownMenu(expanded = addExpanded, onDismissRequest = { addExpanded = false }) {
                        remaining.forEach { scanlator ->
                            DropdownMenuItem(
                                text = { Text(scanlator.ifEmpty { stringResource(MR.strings.unknown_scanlator) }) },
                                onClick = {
                                    drafts[scanlator] = "0" to "0"
                                    addExpanded = false
                                },
                            )
                        }
                    }
                }
                if (drafts.isEmpty()) {
                    val message = if (available.isEmpty()) {
                        MR.strings.no_scanlators_found
                    } else {
                        MR.strings.filler_pages_none_configured
                    }
                    Text(stringResource(message))
                } else {
                    Text(stringResource(MR.strings.filler_pages_zero_hint))
                    drafts.keys.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it }).forEach { scanlator ->
                        val draft = drafts.getValue(scanlator)
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = scanlator.ifEmpty { stringResource(MR.strings.unknown_scanlator) },
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { drafts.remove(scanlator) }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = stringResource(MR.strings.action_remove),
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = draft.first,
                                        onValueChange = { value ->
                                            if (value.all(Char::isDigit)) drafts[scanlator] = value to draft.second
                                        },
                                        label = { Text(stringResource(MR.strings.filler_pages_beginning)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedTextField(
                                        value = draft.second,
                                        onValueChange = { value ->
                                            if (value.all(Char::isDigit)) drafts[scanlator] = draft.first to value
                                        },
                                        label = { Text(stringResource(MR.strings.filler_pages_end)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        drafts.mapNotNull { (scanlator, draft) ->
                            val counts = ScanlatorFillerPages(draft.first.toInt(), draft.second.toInt())
                            if (counts.beginning == 0 && counts.end == 0) null else scanlator to counts
                        }.toMap(),
                    )
                    onDismissRequest()
                },
                enabled = valid,
            ) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}
