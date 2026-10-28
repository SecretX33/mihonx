package eu.kanade.tachiyomi.ui.manga.cover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.data.cache.CoverLink
import eu.kanade.tachiyomi.data.coil.LinkedCoverInterceptor
import eu.kanade.tachiyomi.data.coil.MangaCoverFetcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.app.di.appGraph
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.domain.manga.model.MangaCover as CoverModel

@Composable
fun CoverLinkConfirmation(
    mangaId: Long,
    target: Manga,
    onDismissRequest: () -> Unit,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    val graph = context.appGraph
    val scope = rememberCoroutineScope()
    var link by remember(target) { mutableStateOf<CoverLink?>(null) }
    var preview by remember(target) { mutableStateOf<CoverModel?>(null) }
    var loading by remember(target) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        val selected = CoverLink(
            sourceId = target.source,
            url = target.url,
            title = target.title,
            sourceName = graph.sourceManager.getOrStub(target.source).name,
        )
        link = selected
        preview = graph.linkedCoverResolver.resolve(selected, allowNetwork = true)
        loading = false
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismissRequest() },
        title = { Text(stringResource(MR.strings.cover_link_use)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(target.title)
                link?.sourceName?.let { Text(it) }
                if (loading) CircularProgressIndicator(Modifier.size(32.dp))
                preview?.let { cover ->
                    MangaCover.Book(
                        data = ImageRequest.Builder(context)
                            .data(cover)
                            .memoryCacheKey("linked-cover:${target.source}:${target.url}:${cover.url}")
                            .apply {
                                extras[LinkedCoverInterceptor.USE_LINKED_COVER] = false
                                extras[MangaCoverFetcher.USE_CUSTOM_COVER_KEY] = false
                            }
                            .build(),
                        modifier = Modifier.width(120.dp),
                    )
                }
                if (!loading && preview == null) Text(stringResource(MR.strings.cover_link_unavailable))
                if (failed) Text(stringResource(MR.strings.notification_cover_update_failed))
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && !loading,
                onClick = {
                    val selected = link ?: return@TextButton
                    saving = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val manga = graph.getManga.await(mangaId) ?: error("Entry unavailable")
                                graph.coverLinkStore.set(manga, selected)
                                graph.updateManga.awaitUpdateCoverLastModified(mangaId)
                            }
                            onComplete()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            failed = true
                            saving = false
                        }
                    }
                },
            ) { Text(stringResource(MR.strings.cover_link_use)) }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}
