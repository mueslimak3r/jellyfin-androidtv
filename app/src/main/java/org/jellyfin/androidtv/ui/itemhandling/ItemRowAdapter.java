package org.jellyfin.androidtv.ui.itemhandling;

import static org.koin.java.KoinJavaComponent.inject;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.auth.repository.UserRepository;
import org.jellyfin.androidtv.constant.ChangeTriggerType;
import org.jellyfin.androidtv.constant.QueryType;
import org.jellyfin.androidtv.data.model.ChapterItemInfo;
import org.jellyfin.androidtv.data.model.DataRefreshService;
import org.jellyfin.androidtv.data.model.FilterOptions;
import org.jellyfin.androidtv.data.querying.GetAdditionalPartsRequest;
import org.jellyfin.androidtv.data.querying.GetSeriesTimersRequest;
import org.jellyfin.androidtv.data.querying.GetSpecialsRequest;
import org.jellyfin.androidtv.data.querying.GetTrailersRequest;
import org.jellyfin.androidtv.data.querying.GetUserViewsRequest;
import org.jellyfin.androidtv.data.repository.UserViewsRepository;
import org.jellyfin.androidtv.ui.GridButton;
import org.jellyfin.androidtv.ui.browsing.BrowseGridFragment;
import org.jellyfin.androidtv.ui.browsing.EnhancedBrowseFragment;
import org.jellyfin.androidtv.ui.livetv.TvManager;
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter;
import org.jellyfin.androidtv.ui.presentation.TextItemPresenter;
import org.jellyfin.androidtv.util.Utils;
import org.jellyfin.androidtv.util.apiclient.EmptyLifecycleAwareResponse;
import org.jellyfin.androidtv.util.sdk.compat.JavaCompat;
import org.jellyfin.androidtv.util.sdk.compat.ModelCompat;
import org.jellyfin.apiclient.interaction.ApiClient;
import org.jellyfin.apiclient.interaction.Response;
import org.jellyfin.apiclient.model.dto.BaseItemDto;
import org.jellyfin.apiclient.model.querying.ItemQuery;
import org.jellyfin.apiclient.model.querying.ItemsResult;
import org.jellyfin.apiclient.model.querying.NextUpQuery;
import org.jellyfin.sdk.model.api.BaseItemPerson;
import org.jellyfin.sdk.model.api.ItemSortBy;
import org.jellyfin.sdk.model.api.SortOrder;
import org.jellyfin.sdk.model.api.request.GetAlbumArtistsRequest;
import org.jellyfin.sdk.model.api.request.GetArtistsRequest;
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest;
import org.jellyfin.sdk.model.api.request.GetLiveTvChannelsRequest;
import org.jellyfin.sdk.model.api.request.GetNextUpRequest;
import org.jellyfin.sdk.model.api.request.GetRecommendedProgramsRequest;
import org.jellyfin.sdk.model.api.request.GetRecordingsRequest;
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest;
import org.jellyfin.sdk.model.api.request.GetSeasonsRequest;
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest;
import org.jellyfin.sdk.model.api.request.GetUpcomingEpisodesRequest;
import org.koin.java.KoinJavaComponent;

import java.time.Instant;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import kotlin.Lazy;
import timber.log.Timber;

public class ItemRowAdapter extends MutableObjectAdapter<Object> {
    private ItemQuery mQuery;
    private GetNextUpRequest mNextUpQuery;
    private GetSeasonsRequest mSeasonQuery;
    private GetUpcomingEpisodesRequest mUpcomingQuery;
    private GetSimilarItemsRequest mSimilarQuery;
    private GetSpecialsRequest mSpecialsQuery;
    private GetAdditionalPartsRequest mAdditionalPartsQuery;
    private GetTrailersRequest mTrailersQuery;
    private GetLiveTvChannelsRequest mTvChannelQuery;
    private GetRecommendedProgramsRequest mTvProgramQuery;
    private GetRecordingsRequest mTvRecordingQuery;
    private GetArtistsRequest mArtistsQuery;
    private GetAlbumArtistsRequest mAlbumArtistsQuery;
    private GetLatestMediaRequest mLatestQuery;
    private GetResumeItemsRequest resumeQuery;
    private QueryType queryType;

    private ItemSortBy mSortBy;
    private SortOrder sortOrder;
    private FilterOptions mFilters;

    private EmptyLifecycleAwareResponse mRetrieveFinishedListener;

    private ChangeTriggerType[] reRetrieveTriggers = new ChangeTriggerType[]{};
    private Instant lastFullRetrieve;

    private BaseItemPerson[] mPersons;
    private List<ChapterItemInfo> mChapters;
    private List<org.jellyfin.sdk.model.api.BaseItemDto> mItems;
    private MutableObjectAdapter<Row> mParent;
    private ListRow mRow;
    private int chunkSize = 0;

    private int itemsLoaded = 0;
    private int totalItems = 0;
    private boolean fullyLoaded = false;

    private final Object currentlyRetrievingSemaphore = new Object();
    private boolean currentlyRetrieving = false;

    private boolean preferParentThumb = false;
    private boolean staticHeight = false;

    private final Lazy<ApiClient> apiClient = inject(ApiClient.class);
    private final Lazy<org.jellyfin.sdk.api.client.ApiClient> api = inject(org.jellyfin.sdk.api.client.ApiClient.class);
    private final Lazy<UserViewsRepository> userViewsRepository = inject(UserViewsRepository.class);
    private Context context;

    private boolean isCurrentlyRetrieving() {
        synchronized (currentlyRetrievingSemaphore) {
            return currentlyRetrieving;
        }
    }

    private void setCurrentlyRetrieving(boolean currentlyRetrieving) {
        synchronized (currentlyRetrievingSemaphore) {
            this.currentlyRetrieving = currentlyRetrieving;
        }
    }

    protected boolean getPreferParentThumb() {
        return preferParentThumb;
    }

    protected boolean isStaticHeight() {
        return staticHeight;
    }

    public QueryType getQueryType() {
        return queryType;
    }

    public void setRow(ListRow row) {
        mRow = row;
    }

    public void setReRetrieveTriggers(ChangeTriggerType[] reRetrieveTriggers) {
        this.reRetrieveTriggers = reRetrieveTriggers;
    }

    public ItemRowAdapter(Context context, ItemQuery query, int chunkSize, boolean preferParentThumb, Presenter presenter, MutableObjectAdapter<Row> parent) {
        this(context, query, chunkSize, preferParentThumb, false, presenter, parent);
    }

    public ItemRowAdapter(Context context, ItemQuery query, int chunkSize, boolean preferParentThumb, boolean staticHeight, Presenter presenter, MutableObjectAdapter<Row> parent, QueryType queryType) {
        super(presenter);
        this.context = context;
        mParent = parent;
        mQuery = query;
        mQuery.setUserId(KoinJavaComponent.<UserRepository>get(UserRepository.class).getCurrentUser().getValue().getId().toString());
        this.chunkSize = chunkSize;
        this.preferParentThumb = preferParentThumb;
        this.staticHeight = staticHeight;
        if (chunkSize > 0) {
            mQuery.setLimit(chunkSize);
        }
        this.queryType = queryType;
    }

    public ItemRowAdapter(Context context, ItemQuery query, int chunkSize, boolean preferParentThumb, boolean staticHeight, Presenter presenter, MutableObjectAdapter<Row> parent) {
        this(context, query, chunkSize, preferParentThumb, staticHeight, presenter, parent, QueryType.Items);
    }

    public ItemRowAdapter(Context context, GetArtistsRequest query, int chunkSize, Presenter presenter, MutableObjectAdapter<Row> parent) {
        super(presenter);
        this.context = context;
        mParent = parent;
        mArtistsQuery = query;
        staticHeight = true;
        this.chunkSize = chunkSize;
        queryType = QueryType.Artists;
    }

    public ItemRowAdapter(Context context, GetAlbumArtistsRequest query, int chunkSize, Presenter presenter, MutableObjectAdapter<Row> parent) {
        super(presenter);
        this.context = context;
        mParent = parent;
        mAlbumArtistsQuery = query;
        staticHeight = true;
        this.chunkSize = chunkSize;
        queryType = QueryType.AlbumArtists;
    }

    public ItemRowAdapter(Context context, GetNextUpRequest query, boolean preferParentThumb, Presenter presenter, MutableObjectAdapter<Row> parent) {
        super(presenter);
        this.context = context;
        mParent = parent;
        mNextUpQuery = query;
        queryType = QueryType.NextUp;
        this.preferParentThumb = preferParentThumb;
        this.staticHeight = true;
    }

    public ItemRowAdapter(Context context, GetSeriesTimersRequest query, Presenter presenter, MutableObjectAdapter<Row> parent) {
        super(presenter);
        this.context = context;
        mParent = parent;
        queryType = QueryType.SeriesTimer;
    }

    public ItemRowAdapter(Context context, GetLatestMediaRequest query, boolean preferParentThumb, Presenter presenter, MutableObjectAdapter<Row> parent) {
        super(presenter);
        this.context = context;
        mParent = parent;
        mLatestQuery = query;
        queryType = QueryType.LatestItems;
        this.preferParentThumb = preferParentThumb;
        staticHeight = true;
    }

    public ItemRowAdapter(List<BaseItemPerson> people, Context context, Presenter presenter, MutableObjectAdapter<Row> parent) {
        super(presenter);
        this.context = context;
        mParent = parent;
        mPersons = people.toArray(new BaseItemPerson[people.size()]);
        staticHeight = true;
        queryType = QueryType.StaticPeople;
    }

    public ItemRowAdapter(Context context, List<ChapterItemInfo> chapters, Presenter presenter, MutableObjectAdapter<Row> parent) {
        super(presenter);
        this.context = context;
        mParent = parent;
        mChapters = chapters;
        staticHeight = true;
        queryType = QueryType.StaticChapters;
    }

    public ItemRowAdapter(Context context, List<org.jellyfin.sdk.model.api.BaseItemDto> items, Presenter presenter, MutableObjectAdapter<Row> parent, QueryType queryType) {
        super(presenter);
        this.context = context;
        mParent = parent;
        mItems = items;
        this.queryType = queryType;
    }

    public ItemRowAdapter(Context context, List<BaseItemDto> items, Presenter presenter, MutableObjectAdapter<Row> parent, boolean staticItems) { // last param is just for sig
        super(presenter);
        this.context = context;
        mParent = parent;
        mItems = JavaCompat.mapBaseItemCollection(items);
        queryType = QueryType.StaticItems;
    }

    public ItemRowAdapter(Context context, GetSpecialsRequest query, Presenter presenter, MutableObjectAdapter<Row> parent) {
        super(presenter);
        this.context = context;
        mParent = parent;
        mSpecialsQuery = query;
        queryType = QueryType.Specials;
    }

    public ItemRowAdapter(Context context, GetAdditionalPartsRequest query, Presenter presenter, MutableObjectAdapter<Row> parent) {
        super(presenter);
        this.context = context;
        mParent = parent;
        mAdditionalPartsQuery = query;
        queryType = QueryType.AdditionalParts;
    }

    public ItemRowAdapter(Context context, GetTrailersRequest query, Presenter presenter, MutableObjectAdapter<Row> parent) {
        super(presenter);
        this.context = context;
        mParent = parent;
        mTrailersQuery = query;
        queryType = QueryType.Trailers;
    }

    public ItemRowAdapter(Context context, GetLiveTvChannelsRequest query, int chunkSize, Presenter presenter, MutableObjectAdapter<Row> parent) {
        super(presenter);
        this.context = context;
        mParent = parent;
        mTvChannelQuery = query;
        this.chunkSize = chunkSize;
        queryType = QueryType.LiveTvChannel;
    }

    public ItemRowAdapter(Context context, GetRecommendedProgramsRequest query, Presenter presenter, MutableObjectAdapter<Row> parent) {
        super(presenter);
        this.context = context;
        mParent = parent;
        mTvProgramQuery = query;
        queryType = QueryType.LiveTvProgram;
        staticHeight = true;
    }

    public ItemRowAdapter(Context context, GetRecordingsRequest query, int chunkSize, Presenter presenter, MutableObjectAdapter<Row> parent) {
        super(presenter);
        this.context = context;
        mParent = parent;
        mTvRecordingQuery = query;
        this.chunkSize = chunkSize;
        queryType = QueryType.LiveTvRecording;
        staticHeight = true;
    }

    public ItemRowAdapter(Context context, GetSimilarItemsRequest query, QueryType queryType, Presenter presenter, MutableObjectAdapter<Row> parent) {
        super(presenter);
        this.context = context;
        mParent = parent;
        mSimilarQuery = query;
        this.queryType = queryType;
    }

    public ItemRowAdapter(Context context, GetUpcomingEpisodesRequest query, Presenter presenter, MutableObjectAdapter<Row> parent) {
        super(presenter);
        this.context = context;
        mParent = parent;
        mUpcomingQuery = query;
        queryType = QueryType.Upcoming;
    }

    public ItemRowAdapter(Context context, GetSeasonsRequest query, Presenter presenter, MutableObjectAdapter<Row> parent) {
        super(presenter);
        this.context = context;
        mParent = parent;
        mSeasonQuery = query;
        queryType = QueryType.Season;
    }

    public ItemRowAdapter(Context context, GetUserViewsRequest query, Presenter presenter, MutableObjectAdapter<Row> parent) {
        super(presenter);
        this.context = context;
        mParent = parent;
        queryType = QueryType.Views;
        staticHeight = true;
    }

    public ItemRowAdapter(Context context, GetResumeItemsRequest query, int chunkSize, boolean preferParentThumb, boolean staticHeight, Presenter presenter, MutableObjectAdapter<Row> parent) {
        super(presenter);
        this.context = context;
        mParent = parent;
        resumeQuery = query;
        this.chunkSize = chunkSize;
        this.preferParentThumb = preferParentThumb;
        this.staticHeight = staticHeight;
        this.queryType = QueryType.Resume;
    }

    public void setItemsLoaded(int itemsLoaded) {
        this.itemsLoaded = itemsLoaded;
        this.fullyLoaded = chunkSize == 0 || itemsLoaded >= totalItems;
    }

    public int getItemsLoaded() {
        return itemsLoaded;
    }

    public void setTotalItems(int amt) {
        totalItems = amt;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public void setSortBy(BrowseGridFragment.SortOption option) {
        if (!option.value.equals(mSortBy) || !option.order.equals(sortOrder)) {
            mSortBy = option.value;
            sortOrder = option.order;
            switch (queryType) {
                case Artists:
                    mArtistsQuery = ItemRowAdapterHelperKt.setArtistsSorting(mArtistsQuery, option);
                    break;
                case AlbumArtists:
                    mAlbumArtistsQuery = ItemRowAdapterHelperKt.setAlbumArtistsSorting(mAlbumArtistsQuery, option);
                    break;
                default:
                    mQuery.setSortBy(new String[]{mSortBy.getSerialName(), ItemSortBy.SORT_NAME.getSerialName()});
                    mQuery.setSortOrder(ModelCompat.asLegacy(option.order));
                    break;
            }
            if (!ItemSortBy.SORT_NAME.equals(option.value)) {
                setStartLetter(null);
            }
        }
    }

    public ItemSortBy getSortBy() {
        return mSortBy;
    }

    public SortOrder getSortOrder() {
        return sortOrder;
    }

    public FilterOptions getFilters() {
        return mFilters;
    }

    public void setFilters(FilterOptions filters) {
        mFilters = filters;
        switch (queryType) {
            case Artists:
                mArtistsQuery = ItemRowAdapterHelperKt.setArtistsFilter(mArtistsQuery, ModelCompat.asSdk(filters.getFilters()));
                break;
            case AlbumArtists:
                mAlbumArtistsQuery = ItemRowAdapterHelperKt.setAlbumArtistsFilter(mAlbumArtistsQuery, ModelCompat.asSdk(filters.getFilters()));
                break;
            default:
                mQuery.setFilters(mFilters != null ? mFilters.getFilters() : null);
        }
        removeRow();
    }

    public @Nullable String getStartLetter() {
        return mQuery != null ? mQuery.getNameStartsWithOrGreater() : null;
    }

    public void setStartLetter(String value) {
        switch (queryType) {
            case Artists:
                if (value != null && value.equals("#")) {
                    mArtistsQuery = ItemRowAdapterHelperKt.setArtistsStartLetter(mArtistsQuery, null);
                } else {
                    mArtistsQuery = ItemRowAdapterHelperKt.setArtistsStartLetter(mArtistsQuery, value);
                }
                break;
            case AlbumArtists:
                if (value != null && value.equals("#")) {
                    mAlbumArtistsQuery = ItemRowAdapterHelperKt.setAlbumArtistsStartLetter(mAlbumArtistsQuery, null);
                } else {
                    mAlbumArtistsQuery = ItemRowAdapterHelperKt.setAlbumArtistsStartLetter(mAlbumArtistsQuery, value);
                }
                break;
            default:
                if (value != null && value.equals("#")) {
                    mQuery.setNameStartsWithOrGreater(null);
                } else {
                    mQuery.setNameStartsWithOrGreater(value);
                }
                break;
        }
    }

    protected void removeRow() {
        if (mParent == null) {
            // just clear us
            clear();
            return;
        }

        if (mParent.size() == 1) {
            // we will be removing the last row - show something and prevent the framework from crashing
            // because there is nowhere for focus to land
            ArrayObjectAdapter emptyRow = new ArrayObjectAdapter(new TextItemPresenter());
            emptyRow.add(context.getString(R.string.lbl_no_items));
            mParent.add(new ListRow(new HeaderItem(context.getString(R.string.lbl_empty)), emptyRow));
        }

        mParent.remove(mRow);
    }

    public void loadMoreItemsIfNeeded(int pos) {
        if (fullyLoaded) {
            //context.getLogger().Debug("Row is fully loaded");
            return;
        }
        if (isCurrentlyRetrieving()) {
            Timber.d("Not loading more because currently retrieving");
            return;
        }
        // This needs tobe based on the actual estimated cards on screen via type of presenter and WindowAlignmentOffsetPercent
        if (chunkSize > 0) {
            // we can use chunkSize as indicator on when to load
            if (pos >= (itemsLoaded - (chunkSize / 1.7))) {
                Timber.d("Loading more items trigger pos <%s> itemsLoaded <%s> from total <%s> with chunkSize <%s>", pos, itemsLoaded, totalItems, chunkSize);
                retrieveNext();
            }
        } else if (pos >= itemsLoaded - 20) {
            Timber.d("Loading more items trigger pos <%s> itemsLoaded <%s> from total <%s>", pos, itemsLoaded, totalItems);
            retrieveNext();
        }
    }

    private void retrieveNext() {
        if (fullyLoaded || isCurrentlyRetrieving()) {
            return;
        }

        Integer savedIdx;
        switch (queryType) {
            case LiveTvChannel:
                if (mTvChannelQuery == null) {
                    return;
                }
                notifyRetrieveStarted();

                ItemRowAdapterHelperKt.retrieveLiveTvChannels(this, api.getValue(), mTvChannelQuery, itemsLoaded, chunkSize);
                break;

            case Artists:
                if (mArtistsQuery == null) {
                    return;
                }
                notifyRetrieveStarted();

                ItemRowAdapterHelperKt.retrieveArtists(this, api.getValue(), mArtistsQuery, itemsLoaded, chunkSize);
                break;

            case AlbumArtists:
                if (mAlbumArtistsQuery == null) {
                    return;
                }
                notifyRetrieveStarted();

                ItemRowAdapterHelperKt.retrieveAlbumArtists(this, api.getValue(), mAlbumArtistsQuery, itemsLoaded, chunkSize);
                break;

            default:
                if (mQuery == null) {
                    return;
                }
                notifyRetrieveStarted();

                savedIdx = mQuery.getStartIndex();
                //set the query to go get the next chunk
                mQuery.setStartIndex(itemsLoaded);
                retrieve(mQuery);
                mQuery.setStartIndex(savedIdx); // is reused so reset
                break;
        }
    }

    public boolean ReRetrieveIfNeeded() {
        if (reRetrieveTriggers == null) {
            return false;
        }

        boolean retrieve = false;
        DataRefreshService dataRefreshService = KoinJavaComponent.get(DataRefreshService.class);
        for (ChangeTriggerType trigger : reRetrieveTriggers) {
            switch (trigger) {
                case LibraryUpdated:
                    retrieve |= dataRefreshService.getLastLibraryChange() != null && lastFullRetrieve.isBefore(dataRefreshService.getLastLibraryChange());
                    break;
                case MoviePlayback:
                    retrieve |= dataRefreshService.getLastMoviePlayback() != null && lastFullRetrieve.isBefore(dataRefreshService.getLastMoviePlayback());
                    break;
                case TvPlayback:
                    retrieve |= dataRefreshService.getLastTvPlayback() != null && lastFullRetrieve.isBefore(dataRefreshService.getLastTvPlayback());
                    break;
                case FavoriteUpdate:
                    retrieve |= dataRefreshService.getLastFavoriteUpdate() != null && lastFullRetrieve.isBefore(dataRefreshService.getLastFavoriteUpdate());
                    break;
                case GuideNeedsLoad:
                    Calendar start = new GregorianCalendar(TimeZone.getTimeZone("Z"));
                    start.set(Calendar.MINUTE, start.get(Calendar.MINUTE) >= 30 ? 30 : 0);
                    start.set(Calendar.SECOND, 0);
                    retrieve |= TvManager.programsNeedLoad(start);
                    break;
            }
        }

        if (retrieve) {
            Timber.i("Re-retrieving row of type %s", queryType.toString());
            Retrieve();
        }

        return retrieve;
    }

    public void Retrieve() {
        notifyRetrieveStarted();
        lastFullRetrieve = Instant.now();
        itemsLoaded = 0;
        switch (queryType) {
            case Items:
                retrieve(mQuery);
                break;
            case NextUp:
                ItemRowAdapterHelperKt.retrieveNextUpItems(this, api.getValue(), mNextUpQuery);
                break;
            case LatestItems:
                ItemRowAdapterHelperKt.retrieveLatestMedia(this, api.getValue(), mLatestQuery);
                break;
            case Upcoming:
                ItemRowAdapterHelperKt.retrieveUpcomingEpisodes(this, api.getValue(), mUpcomingQuery);
                break;
            case Season:
                ItemRowAdapterHelperKt.retrieveSeasons(this, api.getValue(), mSeasonQuery);
                break;
            case Views:
                ItemRowAdapterHelperKt.retrieveUserViews(this, api.getValue(), userViewsRepository.getValue());
                break;
            case SimilarSeries:
            case SimilarMovies:
                ItemRowAdapterHelperKt.retrieveSimilarItems(this, api.getValue(), mSimilarQuery);
                break;
            case LiveTvChannel:
                ItemRowAdapterHelperKt.retrieveLiveTvChannels(this, api.getValue(), mTvChannelQuery, 0, chunkSize);
                break;
            case LiveTvProgram:
                ItemRowAdapterHelperKt.retrieveLiveTvRecommendedPrograms(this, api.getValue(), mTvProgramQuery);
                break;
            case LiveTvRecording:
                ItemRowAdapterHelperKt.retrieveLiveTvRecordings(this, api.getValue(), mTvRecordingQuery);
                break;
            case StaticPeople:
                loadPeople();
                break;
            case StaticChapters:
                loadChapters();
                break;
            case StaticItems:
                loadStaticItems();
                break;
            case StaticAudioQueueItems:
                loadStaticAudioItems();
                break;
            case Specials:
                ItemRowAdapterHelperKt.retrieveSpecialFeatures(this, api.getValue(), mSpecialsQuery);
                break;
            case AdditionalParts:
                ItemRowAdapterHelperKt.retrieveAdditionalParts(this, api.getValue(), mAdditionalPartsQuery);
                break;
            case Trailers:
                ItemRowAdapterHelperKt.retrieveTrailers(this, api.getValue(), mTrailersQuery);
                break;
            case Search:
                loadStaticItems();
                addToParentIfResultsReceived();
                break;
            case Artists:
                ItemRowAdapterHelperKt.retrieveArtists(this, api.getValue(), mArtistsQuery, 0, chunkSize);
                break;
            case AlbumArtists:
                ItemRowAdapterHelperKt.retrieveAlbumArtists(this, api.getValue(), mAlbumArtistsQuery, 0, chunkSize);
                break;
            case AudioPlaylists:
                retrieveAudioPlaylists(mQuery);
                break;
            case Premieres:
                retrievePremieres(mQuery);
                break;
            case SeriesTimer:
                boolean canManageRecordings = Utils.canManageRecordings(KoinJavaComponent.<UserRepository>get(UserRepository.class).getCurrentUser().getValue());
                ItemRowAdapterHelperKt.retrieveLiveTvSeriesTimers(this, api.getValue(), context, canManageRecordings);
                break;
            case Resume:
                ItemRowAdapterHelperKt.retrieveResumeItems(this, api.getValue(), resumeQuery);
            break;
        }
    }

    private void loadPeople() {
        if (mPersons != null) {
            for (BaseItemPerson person : mPersons) {
                add(new BaseItemPersonBaseRowItem(person));
            }

        } else {
            removeRow();
        }

        notifyRetrieveFinished();
    }

    private void loadChapters() {
        if (mChapters != null) {
            for (ChapterItemInfo chapter : mChapters) {
                add(new ChapterItemInfoBaseRowItem(chapter));
            }

        } else {
            removeRow();
        }

        notifyRetrieveFinished();
    }

    private void loadStaticItems() {
        if (mItems != null) {
            for (org.jellyfin.sdk.model.api.BaseItemDto item : mItems) {
                add(new BaseItemDtoBaseRowItem(item));
            }
            itemsLoaded = mItems.size();
        } else {
            removeRow();
        }

        notifyRetrieveFinished();
    }

    private void loadStaticAudioItems() {
        if (mItems != null) {
            for (org.jellyfin.sdk.model.api.BaseItemDto item : mItems) {
                add(new AudioQueueBaseRowItem(item));
            }
            itemsLoaded = mItems.size();

        } else {
            removeRow();
        }

        notifyRetrieveFinished();
    }

    private void addToParentIfResultsReceived() {
        if (itemsLoaded > 0 && mParent != null) {
            mParent.add(mRow);
        }
    }

    private void retrieve(final ItemQuery query) {
        apiClient.getValue().GetItemsAsync(query, new Response<ItemsResult>() {
            @Override
            public void onResponse(ItemsResult response) {
                if (response.getItems() != null && response.getItems().length > 0) {
                    setTotalItems(query.getEnableTotalRecordCount() ? response.getTotalRecordCount() : response.getItems().length);

                    ItemRowAdapterHelperKt.setItems(ItemRowAdapter.this, response.getItems(), (item, i) -> new BaseItemDtoBaseRowItem(ModelCompat.asSdk(item), getPreferParentThumb(), isStaticHeight()));
                } else if (getItemsLoaded() == 0) {
                    removeRow();
                }

                notifyRetrieveFinished();
            }

            @Override
            public void onError(Exception exception) {
                Timber.e(exception, "Error retrieving items");
                removeRow();
                notifyRetrieveFinished(exception);
            }
        });
    }

    private void retrieveAudioPlaylists(final ItemQuery query) {
        //Add specialized playlists first
        clear();
        add(new GridButton(EnhancedBrowseFragment.FAVSONGS, context.getString(R.string.lbl_favorites), R.drawable.favorites));
        itemsLoaded = 1;
        retrieve(query);
    }

    private void retrievePremieres(final ItemQuery query) {
        final ItemRowAdapter adapter = this;
        //First we need current Next Up to filter our list with
        NextUpQuery nextUp = new NextUpQuery();
        nextUp.setUserId(query.getUserId());
        nextUp.setParentId(query.getParentId());
        nextUp.setLimit(50);
        apiClient.getValue().GetNextUpEpisodesAsync(nextUp, new Response<ItemsResult>() {
            @Override
            public void onResponse(final ItemsResult nextUpResponse) {
                apiClient.getValue().GetItemsAsync(query, new Response<ItemsResult>() {
                    @Override
                    public void onResponse(ItemsResult response) {
                        if (adapter.size() > 0) {
                            adapter.clear();
                        }
                        if (response.getItems() != null && response.getItems().length > 0) {
                            Calendar compare = Calendar.getInstance();
                            compare.add(Calendar.MONTH, -2);
                            BaseItemDto[] nextUpItems = nextUpResponse.getItems();
                            for (BaseItemDto item : response.getItems()) {
                                if (item.getIndexNumber() != null && item.getIndexNumber() == 1 && (item.getDateCreated() == null || item.getDateCreated().after(compare.getTime()))
                                        && (item.getUserData() == null || item.getUserData().getLikes() == null || item.getUserData().getLikes())
                                ) {
                                    // new unwatched episode 1 not disliked - check to be sure prev episode not already in next up
                                    BaseItemDto nextUpItem = null;
                                    for (BaseItemDto upItem : nextUpItems) {
                                        if (upItem.getSeriesId().equals(item.getSeriesId())) {
                                            nextUpItem = upItem;
                                            break;
                                        }
                                    }

                                    if (nextUpItem == null || nextUpItem.getId().equals(item.getId())) {
                                        //Now - let's be sure there isn't already a premiere for this series
                                        BaseRowItem existing = null;
                                        int existingPos = -1;
                                        for (int n = 0; n < adapter.size(); n++) {
                                            if (((BaseRowItem) adapter.get(n)).getBaseItem().getSeriesId().equals(item.getSeriesId())) {
                                                existing = (BaseRowItem) adapter.get(n);
                                                existingPos = n;
                                                break;
                                            }
                                        }
                                        if (existing == null) {
                                            Timber.d("Adding new episode 1 to premieres %s", item.getSeriesName());
                                            adapter.add(new BaseItemDtoBaseRowItem(ModelCompat.asSdk(item), preferParentThumb, true));

                                        } else if (existing.getBaseItem().getParentIndexNumber() > item.getParentIndexNumber()) {
                                            //Replace the newer item with the earlier season
                                            Timber.d("Replacing newer episode 1 with an older season for %s", item.getSeriesName());
                                            adapter.set(existingPos, new BaseItemDtoBaseRowItem(ModelCompat.asSdk(item), preferParentThumb, false));
                                        } // otherwise, just ignore this newer season premiere since we have the older one already

                                    } else {
                                        Timber.i("Didn't add %s to premieres because different episode is in next up.", item.getSeriesName());
                                    }
                                }
                            }
                            setItemsLoaded(itemsLoaded + response.getItems().length);
                        }


                        if (adapter.size() == 0) {
                            removeRow();
                        }
                        notifyRetrieveFinished();
                    }
                });

            }

        });

    }

    protected void notifyRetrieveFinished() {
        notifyRetrieveFinished(null);
    }

    protected void notifyRetrieveFinished(@Nullable Exception exception) {
        setCurrentlyRetrieving(false);
        if (mRetrieveFinishedListener != null && mRetrieveFinishedListener.getActive()) {
            if (exception == null) mRetrieveFinishedListener.onResponse();
            else mRetrieveFinishedListener.onError(exception);
        }
    }

    public void setRetrieveFinishedListener(EmptyLifecycleAwareResponse response) {
        this.mRetrieveFinishedListener = response;
    }

    private void notifyRetrieveStarted() {
        setCurrentlyRetrieving(true);
    }
}
