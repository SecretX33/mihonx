package eu.kanade.presentation.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.history.model.History
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@Composable
fun ChapterInfoDialog(
    chapter: Chapter,
    history: History?,
    onDismissRequest: () -> Unit,
) {
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.chapter_info)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (chapter.isRecognizedNumber) {
                    InfoRow(
                        label = stringResource(MR.strings.chapter_info_number),
                        value = chapter.chapterNumber.let {
                            if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
                        },
                    )
                }
                if (!chapter.scanlator.isNullOrBlank()) {
                    InfoRow(
                        label = stringResource(MR.strings.scanlator),
                        value = chapter.scanlator!!,
                    )
                }
                InfoRow(
                    label = stringResource(MR.strings.chapter_info_date_uploaded),
                    value = if (chapter.dateUpload > 0) {
                        dateFormat.format(Date(chapter.dateUpload))
                    } else {
                        "-"
                    },
                )
                InfoRow(
                    label = stringResource(MR.strings.chapter_info_date_fetched),
                    value = if (chapter.dateFetch > 0) {
                        dateFormat.format(Date(chapter.dateFetch))
                    } else {
                        "-"
                    },
                )
                InfoRow(
                    label = stringResource(MR.strings.chapter_info_last_read),
                    value = history?.readAt?.let { dateFormat.format(it) } ?: "-",
                )
                InfoRow(
                    label = stringResource(MR.strings.chapter_info_read_duration),
                    value = history?.readDuration
                        ?.takeIf { it >= 0 }
                        ?.let { durationMs ->
                            val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
                            val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
                            "%d:%02d".format(minutes, seconds)
                        }
                        ?: "-",
                )
                if (!chapter.read && chapter.lastPageRead > 0) {
                    InfoRow(
                        label = stringResource(MR.strings.chapter_info_reading_progress),
                        value = stringResource(MR.strings.chapter_progress, chapter.lastPageRead + 1),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
