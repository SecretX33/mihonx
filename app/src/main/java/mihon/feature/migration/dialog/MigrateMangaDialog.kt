package mihon.feature.migration.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.cache.CoverLinkStore
import eu.kanade.tachiyomi.data.cache.coverLinkIdentity
import eu.kanade.tachiyomi.data.download.DownloadManager
import kotlinx.coroutines.flow.update
import mihon.core.viewmodel.StateViewModel
import mihon.domain.migration.models.MigrationFlag
import mihon.domain.migration.usecases.MigrateMangaUseCase
import mihon.feature.common.utils.getLabel
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
internal fun Screen.MigrateMangaDialog(
    current: Manga,
    target: Manga,
    onClickTitle: () -> Unit,
    onDismissRequest: () -> Unit,
    onComplete: () -> Unit = onDismissRequest,
) {
    val scope = rememberCoroutineScope()

    val viewModel = viewModel<MigrateDialogViewModel>()
    LaunchedEffect(current, target) {
        viewModel.init(current, target)
    }
    val state by viewModel.state.collectAsState()

    if (state.isMigrated) return

    if (state.isMigrating) {
        LoadingScreen(
            modifier = Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(MR.strings.migration_dialog_what_to_include))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                state.applicableFlags.fastForEach { flag ->
                    if (flag == MigrationFlag.COVER) {
                        val keepCover = flag in state.selectedFlags
                        val currentDescription = stringResource(
                            when {
                                state.hasCoverLink -> MR.strings.migration_keep_cover_link
                                state.hasCustomCover -> MR.strings.migration_keep_custom_cover
                                else -> MR.strings.migration_keep_source_cover
                            },
                        )
                        val destinationDescription = stringResource(MR.strings.migration_use_destination_cover)
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(MR.strings.migration_cover),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = keepCover,
                                    onClick = { if (!keepCover) viewModel.toggleSelection(flag) },
                                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                                    modifier = Modifier.semantics { contentDescription = currentDescription },
                                    icon = {},
                                ) {
                                    Text(
                                        stringResource(MR.strings.migration_cover_current),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                SegmentedButton(
                                    selected = !keepCover,
                                    onClick = { if (keepCover) viewModel.toggleSelection(flag) },
                                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                                    modifier = Modifier.semantics { contentDescription = destinationDescription },
                                    icon = {},
                                ) {
                                    Text(
                                        stringResource(MR.strings.migration_cover_destination),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Text(
                                text = if (keepCover) currentDescription else destinationDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LabeledCheckbox(
                            label = stringResource(flag.getLabel()),
                            checked = flag in state.selectedFlags,
                            onCheckedChange = { viewModel.toggleSelection(flag) },
                        )
                    }
                }
                if (state.coverMatchesDestination) {
                    Text(
                        text = stringResource(MR.strings.migration_cover_matches_destination),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                TextButton(
                    onClick = {
                        onDismissRequest()
                        onClickTitle()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_show_manga))
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(
                    enabled = state.canMigrate,
                    onClick = {
                        scope.launchIO {
                            viewModel.migrateManga(replace = false)
                            withUIContext { onComplete() }
                        }
                    },
                ) {
                    Text(text = stringResource(MR.strings.copy))
                }
                TextButton(
                    enabled = state.canMigrate,
                    onClick = {
                        scope.launchIO {
                            viewModel.migrateManga(replace = true)
                            withUIContext { onComplete() }
                        }
                    },
                ) {
                    Text(text = stringResource(MR.strings.migrate))
                }
            }
        },
    )
}

class MigrateDialogViewModel(
    private val sourcePreference: SourcePreferences = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val migrateManga: MigrateMangaUseCase = Injekt.get(),
    private val coverLinkStore: CoverLinkStore = Injekt.get(),
) : StateViewModel<MigrateDialogViewModel.State>(State()) {

    fun init(current: Manga, target: Manga) {
        val currentCoverLink = coverLinkStore.get(current)
        val coverMatchesDestination = currentCoverLink?.identity == target.coverLinkIdentity()
        val applicableFlags = buildList {
            MigrationFlag.entries.forEach {
                val applicable = when (it) {
                    MigrationFlag.CHAPTER -> true
                    MigrationFlag.CATEGORY -> true
                    MigrationFlag.CUSTOM_INFO ->
                        current.customTitle != null || current.customAuthor != null ||
                            current.customArtist != null || current.customDescription != null ||
                            current.customGenre != null || current.customStatus != null
                    MigrationFlag.NOTES -> current.notes.isNotBlank()
                    MigrationFlag.REMOVE_DOWNLOAD -> downloadManager.getDownloadCount(current) > 0
                    MigrationFlag.COVER -> !coverMatchesDestination
                }
                if (applicable) add(it)
            }
        }
        val selectedFlags = sourcePreference.migrationFlags.get().let {
            if (currentCoverLink != null && !coverMatchesDestination) {
                it + MigrationFlag.COVER
            } else {
                it - MigrationFlag.COVER
            }
        }
        mutableState.update {
            State(
                current = current,
                target = target,
                applicableFlags = applicableFlags,
                selectedFlags = selectedFlags,
                hasCoverLink = currentCoverLink != null,
                hasCustomCover = current.hasCustomCover(coverCache),
                coverMatchesDestination = coverMatchesDestination,
            )
        }
    }

    fun toggleSelection(flag: MigrationFlag) {
        mutableState.update {
            if (flag !in it.applicableFlags) return@update it
            val selectedFlags = it.selectedFlags.toMutableSet()
                .apply { if (contains(flag)) remove(flag) else add(flag) }
                .toSet()
            it.copy(selectedFlags = selectedFlags)
        }
    }

    suspend fun migrateManga(replace: Boolean) {
        val state = state.value
        val current = state.current ?: return
        val target = state.target ?: return
        if (!state.canMigrate) return
        sourcePreference.migrationFlags.set(state.selectedFlags)
        mutableState.update { it.copy(isMigrating = true) }
        migrateManga(current, target, replace)
        mutableState.update { it.copy(isMigrating = false, isMigrated = true) }
    }

    data class State(
        val current: Manga? = null,
        val target: Manga? = null,
        val applicableFlags: List<MigrationFlag> = emptyList(),
        val selectedFlags: Set<MigrationFlag> = emptySet(),
        val hasCoverLink: Boolean = false,
        val hasCustomCover: Boolean = false,
        val coverMatchesDestination: Boolean = false,
        val isMigrating: Boolean = false,
        val isMigrated: Boolean = false,
    ) {
        val canMigrate: Boolean
            get() = current != null && target != null && current.id != target.id &&
                current.coverLinkIdentity() != target.coverLinkIdentity()
    }
}
