package eu.kanade.tachiyomi.ui.browse.source.browse

import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.source.model.SourceFilterSnapshot
import eu.kanade.domain.source.service.SourceFilterDefaults
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceViewModel.DefaultFilterStatus
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceViewModel.Listing
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.domain.source.service.SourceManager
import kotlin.time.Duration.Companion.seconds

class BrowseSourceViewModelTest {

    private val saved = mutableMapOf<Long, SourceFilterSnapshot>()
    private val models = mutableListOf<BrowseSourceViewModel>()
    private val defaults = mockk<SourceFilterDefaults> {
        every { this@mockk.get(any<Long>()) } answers { saved[firstArg()] }
        every { isSet(any()) } answers { saved.containsKey(firstArg()) }
        every { set(any(), any()) } answers { saved[firstArg()] = secondArg() }
        every { clear(any()) } answers {
            saved.remove(firstArg())
            Unit
        }
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    @Test
    fun `sources without saved defaults retain their original listing and filter values`() = runTest {
        val model = createModel(sourceFilters = { filters(completed = true) })

        model.state.value.listing shouldBe Listing.Popular
        model.state.value.defaultFilterStatus shouldBe DefaultFilterStatus.None
        SourceFilterSnapshot.capture(model.state.value.filters) shouldBe snapshot(completed = true)
        saved.isEmpty() shouldBe true
    }

    @Test
    fun `normal source entry applies saved defaults before publishing its listing`() = runTest {
        saved[1] = snapshot(completed = true, action = Filter.TriState.STATE_EXCLUDE)
        val model = createModel()

        val listing = model.state.value.listing.shouldBeInstanceOf<Listing.Search>()
        listing.query shouldBe null
        SourceFilterSnapshot.capture(listing.filters) shouldBe saved[1]
        SourceFilterSnapshot.capture(model.state.value.filters) shouldBe saved[1]
        model.state.value.defaultFilterStatus shouldBe DefaultFilterStatus.Matches
    }

    @Test
    fun `temporary changes disappear on reopening and never affect another source`() = runTest {
        val model = createModel()
        model.setFilters(filters(completed = true))
        model.saveDefaultFilters()
        model.setFilters(filters(action = Filter.TriState.STATE_INCLUDE))
        model.search(filters = model.state.value.filters)

        val reopened = createModel()
        val otherSource = createModel(sourceId = 2)

        SourceFilterSnapshot.capture(reopened.state.value.listing.filters) shouldBe snapshot(completed = true)
        otherSource.state.value.listing shouldBe Listing.Popular
        otherSource.state.value.hasSavedFilters shouldBe false
    }

    @Test
    fun `saving replaces defaults without applying filters or saving the toolbar query`() = runTest {
        saved[1] = snapshot(completed = true)
        val model = createModel()
        model.search(query = "Vampires")
        val before = SourceFilterSnapshot.capture(model.state.value.listing.filters)
        model.setFilters(filters(action = Filter.TriState.STATE_EXCLUDE))
        model.saveDefaultFilters()

        SourceFilterSnapshot.capture(model.state.value.listing.filters) shouldBe before
        model.state.value.listing.query shouldBe "Vampires"
        saved[1] shouldBe snapshot(action = Filter.TriState.STATE_EXCLUDE)

        val reopened = createModel()
        reopened.state.value.listing.query shouldBe null
        reopened.state.value.toolbarQuery shouldBe null
        SourceFilterSnapshot.capture(reopened.state.value.listing.filters) shouldBe saved[1]
    }

    @Test
    fun `clearing defaults leaves active filters intact and supports undo`() = runTest {
        val original = snapshot(completed = true)
        saved[1] = original
        val model = createModel()
        model.clearDefaultFilters()

        SourceFilterSnapshot.capture(model.state.value.listing.filters) shouldBe original
        model.state.value.defaultFilterStatus shouldBe DefaultFilterStatus.None
        createModel().state.value.listing shouldBe Listing.Popular

        model.undoClearDefaultFilters(original)

        SourceFilterSnapshot.capture(createModel().state.value.listing.filters) shouldBe original
    }

    @Test
    fun `reset and restore only change the filter controls`() = runTest {
        val original = snapshot(completed = true)
        saved[1] = original
        val model = createModel()
        model.resetFilters()

        model.state.value.defaultFilterStatus shouldBe DefaultFilterStatus.Different
        SourceFilterSnapshot.capture(model.state.value.filters) shouldBe snapshot()
        SourceFilterSnapshot.capture(model.state.value.listing.filters) shouldBe original
        saved[1] shouldBe original

        model.restoreDefaultFilters()

        model.state.value.defaultFilterStatus shouldBe DefaultFilterStatus.Matches
        SourceFilterSnapshot.capture(model.state.value.filters) shouldBe original
    }

    @Test
    fun `editing the filter sheet cannot mutate filters used by the current listing or saved defaults`() = runTest {
        val original = snapshot(completed = true)
        saved[1] = original
        val model = createModel()
        model.openFilterSheet()
        val editing = model.state.value.filters
        (editing[0] as Filter.CheckBox).state = false
        model.setFilters(editing)

        SourceFilterSnapshot.capture(model.state.value.listing.filters) shouldBe original
        saved[1] shouldBe original
        model.state.value.defaultFilterStatus shouldBe DefaultFilterStatus.Different

        model.search(filters = editing)
        model.setDialog(null)
        model.openFilterSheet()
        (model.state.value.filters[0] as Filter.CheckBox).state = true
        model.setFilters(model.state.value.filters)

        SourceFilterSnapshot.capture(model.state.value.listing.filters) shouldBe snapshot()
        saved[1] shouldBe original
    }

    @Test
    fun `explicit Popular Latest and text search entries bypass saved defaults`() = runTest {
        saved[1] = snapshot(completed = true)
        listOf(GetRemoteManga.QUERY_POPULAR, GetRemoteManga.QUERY_LATEST, "Vampires").forEach { query ->
            val model = createModel(query = query, openWithDefaultFilters = false)

            model.state.value.listing.query shouldBe query
            model.state.value.defaultFilterStatus shouldBe DefaultFilterStatus.Different
            SourceFilterSnapshot.capture(model.state.value.filters) shouldBe snapshot()
            if (model.state.value.listing is Listing.Search) {
                SourceFilterSnapshot.capture(model.state.value.listing.filters) shouldBe snapshot()
            }
        }
    }

    @Test
    fun `switching to Popular or Latest does not change saved defaults`() = runTest {
        val original = snapshot(completed = true)
        saved[1] = original
        val model = createModel()

        listOf(Listing.Popular, Listing.Latest).forEach { listing ->
            model.resetFilters()
            model.setListing(listing)
            model.state.value.listing shouldBe listing
            saved[1] shouldBe original
        }
    }

    @Test
    fun `genre searches retain their explicit filters without changing saved defaults`() = runTest {
        val original = snapshot(completed = true)
        saved[1] = original
        val model = createModel()
        model.searchGenre("Action")

        SourceFilterSnapshot.capture(model.state.value.listing.filters) shouldBe
            snapshot(action = Filter.TriState.STATE_INCLUDE)
        saved[1] shouldBe original
    }

    @Test
    fun `changed source filters fall back without deleting defaults and allow replacement`() = runTest {
        saved[1] = SourceFilterSnapshot.capture(FilterList(object : Filter.CheckBox("Old filter", true) {}))
        val model = createModel()

        model.state.value.listing shouldBe Listing.Popular
        model.state.value.defaultFilterStatus shouldBe DefaultFilterStatus.Unavailable
        saved.containsKey(1) shouldBe true

        model.saveDefaultFilters()

        saved[1] shouldBe snapshot()
        model.state.value.defaultFilterStatus shouldBe DefaultFilterStatus.Matches
    }

    private suspend fun TestScope.createModel(
        sourceId: Long = 1,
        query: String = GetRemoteManga.QUERY_POPULAR,
        openWithDefaultFilters: Boolean = true,
        sourceFilters: () -> FilterList = { filters() },
    ): BrowseSourceViewModel {
        val source = mockk<Source> {
            every { id } returns sourceId
            every { getFilterList() } answers { sourceFilters() }
        }
        val sourceManager = mockk<SourceManager> {
            coEvery { getOrStub(sourceId) } returns source
        }
        val preferences = mockk<SourcePreferences> {
            every { sourceDisplayMode } returns InMemoryPreference("display", null, LibraryDisplayMode.default)
            every { hideInLibraryItems } returns InMemoryPreference("hide", null, false)
        }
        val incognitoState = mockk<GetIncognitoState> {
            coEvery { await(sourceId) } returns true
        }
        val model = BrowseSourceViewModel(
            sourceId = sourceId,
            listingQuery = query,
            openWithDefaultFilters = openWithDefaultFilters,
            appScope = backgroundScope,
            sourceManager = sourceManager,
            sourcePreferences = preferences,
            sourceFilterDefaults = defaults,
            libraryPreferences = mockk(),
            coverCache = mockk(),
            coverLinkStore = mockk(),
            getRemoteManga = mockk(),
            getDuplicateLibraryManga = mockk(),
            getCategories = mockk(),
            setMangaCategories = mockk(),
            setMangaDefaultChapterFlags = mockk(),
            getManga = mockk(),
            updateManga = mockk(),
            addTracks = mockk(),
            getIncognitoState = incognitoState,
        )
        models += model
        withContext(Dispatchers.Default) {
            withTimeout(10.seconds) { model.state.first { it.source != null } }
        }
        return model
    }

    private fun filters(completed: Boolean = false, action: Int = Filter.TriState.STATE_IGNORE) = FilterList(
        object : Filter.CheckBox("Completed", completed) {},
        object : Filter.Group<Filter<*>>(
            "Genres",
            listOf(object : Filter.TriState("Action", action) {}),
        ) {},
    )

    private fun snapshot(completed: Boolean = false, action: Int = Filter.TriState.STATE_IGNORE) =
        SourceFilterSnapshot.capture(filters(completed, action))
}
