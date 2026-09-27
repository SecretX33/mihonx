package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
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
    val scanlators = remember(availableScanlators, hasUnlabeledChapters, rules) {
        (availableScanlators + rules.keys + (if (hasUnlabeledChapters) setOf("") else emptySet()))
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }
    val drafts = remember(scanlators, rules) {
        mutableStateMapOf<String, Pair<String, String>>().apply {
            scanlators.forEach { scanlator ->
                val counts = rules[scanlator] ?: ScanlatorFillerPages()
                put(scanlator, counts.beginning.toString() to counts.end.toString())
            }
        }
    }
    val valid = drafts.values.all { (beginning, end) ->
        beginning.toIntOrNull() != null && end.toIntOrNull() != null
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(MR.strings.skip_filler_pages)) },
        text = {
            if (scanlators.isEmpty()) {
                Text(stringResource(MR.strings.no_scanlators_found))
            } else {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stringResource(MR.strings.filler_pages_zero_hint))
                    scanlators.forEach { scanlator ->
                        val draft = drafts.getValue(scanlator)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(scanlator.ifEmpty { stringResource(MR.strings.unknown_scanlator) })
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
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(drafts.mapNotNull { (scanlator, draft) ->
                        val counts = ScanlatorFillerPages(draft.first.toInt(), draft.second.toInt())
                        if (counts.beginning == 0 && counts.end == 0) null else scanlator to counts
                    }.toMap())
                    onDismissRequest()
                },
                enabled = valid && scanlators.isNotEmpty(),
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
