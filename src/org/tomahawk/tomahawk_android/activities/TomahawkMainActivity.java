/* == This file is part of Tomahawk Player - <http://tomahawk-player.org> ===
 *
 *   Copyright 2012, Christopher Reichert <creichert07@gmail.com>
 *   Copyright 2012, Enno Gottschalk <mrmaffen@googlemail.com>
 *
 *   Tomahawk is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Tomahawk is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Tomahawk. If not, see <http://www.gnu.org/licenses/>.
 */
package org.tomahawk.tomahawk_android.activities;

import org.tomahawk.libtomahawk.collection.Album;
import org.tomahawk.libtomahawk.collection.Artist;
import org.tomahawk.libtomahawk.collection.Collection;
import org.tomahawk.libtomahawk.collection.CollectionLoader;
import org.tomahawk.libtomahawk.collection.UserPlaylist;
import org.tomahawk.libtomahawk.collection.SourceList;
import org.tomahawk.libtomahawk.collection.Track;
import org.tomahawk.libtomahawk.collection.UserCollection;
import org.tomahawk.libtomahawk.hatchet.InfoSystem;
import org.tomahawk.libtomahawk.resolver.PipeLine;
import org.tomahawk.libtomahawk.utils.TomahawkUtils;
import org.tomahawk.tomahawk_android.R;
import org.tomahawk.tomahawk_android.TomahawkApp;
import org.tomahawk.tomahawk_android.adapters.TomahawkMenuAdapter;
import org.tomahawk.tomahawk_android.fragments.AlbumsFragment;
import org.tomahawk.tomahawk_android.fragments.ArtistsFragment;
import org.tomahawk.tomahawk_android.fragments.FakePreferenceFragment;
import org.tomahawk.tomahawk_android.fragments.PlaybackFragment;
import org.tomahawk.tomahawk_android.fragments.SearchableFragment;
import org.tomahawk.tomahawk_android.fragments.TomahawkFragment;
import org.tomahawk.tomahawk_android.fragments.TracksFragment;
import org.tomahawk.tomahawk_android.fragments.UserCollectionFragment;
import org.tomahawk.tomahawk_android.fragments.UserPlaylistsFragment;
import org.tomahawk.tomahawk_android.services.PlaybackService;
import org.tomahawk.tomahawk_android.services.PlaybackService.PlaybackServiceConnection;
import org.tomahawk.tomahawk_android.services.PlaybackService.PlaybackServiceConnection.PlaybackServiceConnectionListener;
import org.tomahawk.tomahawk_android.services.TomahawkService;
import org.tomahawk.tomahawk_android.ui.widgets.SquareHeightRelativeLayout;
import org.tomahawk.tomahawk_android.utils.ContentViewer;

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The main Tomahawk activity
 */
public class TomahawkMainActivity extends ActionBarActivity
        implements PlaybackServiceConnectionListener,
        TomahawkService.TomahawkServiceConnection.TomahawkServiceConnectionListener,
        LoaderManager.LoaderCallbacks<Collection> {

    public static final String COLLECTION_ID_STOREDBACKSTACK
            = "org.tomahawk.tomahawk_android.collection_id_storedbackstack";

    public static final String TOMAHAWKSERVICE_READY
            = "org.tomahawk.tomahawk_android.tomahawkservice_ready";

    public static final String PLAYBACKSERVICE_READY
            = "org.tomahawk.tomahawk_android.playbackservice_ready";

    public static final String SHOW_PLAYBACKFRAGMENT_ON_STARTUP
            = "org.tomahawk.tomahawk_android.show_playbackfragment_on_startup";

    private TomahawkApp mTomahawkApp;

    private PipeLine mPipeLine;

    private InfoSystem mInfoSystem;

    private CharSequence mTitle;

    private PlaybackServiceConnection mPlaybackServiceConnection = new PlaybackServiceConnection(
            this);

    private PlaybackService mPlaybackService;

    private TomahawkService.TomahawkServiceConnection mTomahawkServiceConnection
            = new TomahawkService.TomahawkServiceConnection(this);

    private TomahawkService mTomahawkService;

    private DrawerLayout mDrawerLayout;

    private ListView mDrawerList;

    private ActionBarDrawerToggle mDrawerToggle;

    private CharSequence mDrawerTitle;

    private ContentViewer mContentViewer;

    private UserCollection mUserCollection;

    private TomahawkMainReceiver mTomahawkMainReceiver;

    private View mNowPlayingFrame;

    private Drawable mProgressDrawable;

    private static final int MSG_UPDATE_ANIMATION = 0x20;

    // Used to display an animated progress drawable, as long as the PipeLine is resolving something
    private Handler mAnimationHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_ANIMATION:
                    if ((mPipeLine != null && mPipeLine.isResolving()) ||
                            (mPlaybackService != null && mPlaybackService.isPreparing()) ||
                            (mInfoSystem != null && mInfoSystem.isResolving())) {
                        mProgressDrawable.setLevel(mProgressDrawable.getLevel() + 500);
                        getSupportActionBar().setLogo(mProgressDrawable);
                        mAnimationHandler.removeMessages(MSG_UPDATE_ANIMATION);
                        mAnimationHandler.sendEmptyMessageDelayed(MSG_UPDATE_ANIMATION, 50);
                    } else {
                        stopLoadingAnimation();
                    }
                    break;
            }
            return true;
        }
    });

    /**
     * Handles incoming broadcasts.
     */
    private class TomahawkMainReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Collection.COLLECTION_UPDATED.equals(intent.getAction())) {
                onCollectionUpdated();
            }
            if (PlaybackService.BROADCAST_NEWTRACK.equals(intent.getAction())) {
                if (mPlaybackService != null) {
                    setNowPlayingInfo();
                }
            }
        }
    }

    private class DrawerItemClickListener implements ListView.OnItemClickListener {

        /**
         * Called every time an item inside the {@link android.widget.ListView} is clicked
         *
         * @param parent   The AdapterView where the click happened.
         * @param view     The view within the AdapterView that was clicked (this will be a view
         *                 provided by the adapter)
         * @param position The position of the view in the adapter.
         * @param id       The row id of the item that was clicked.
         */
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            // Show the correct hub, and if needed, display the search editText inside the ActionBar
            switch ((int) id) {
                case ContentViewer.HUB_ID_COLLECTION:
                    mContentViewer.showHub(ContentViewer.HUB_ID_COLLECTION);
                    break;
                case ContentViewer.HUB_ID_PLAYLISTS:
                    mContentViewer.showHub(ContentViewer.HUB_ID_PLAYLISTS);
                    break;
                case ContentViewer.HUB_ID_SETTINGS:
                    mContentViewer.showHub(ContentViewer.HUB_ID_SETTINGS);
                    break;
            }
            if (mDrawerLayout != null) {
                mDrawerLayout.closeDrawer(mDrawerList);
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.tomahawk_main_activity);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        mTomahawkApp = ((TomahawkApp) getApplication());
        mPipeLine = mTomahawkApp.getPipeLine();
        mInfoSystem = mTomahawkApp.getInfoSystem();

        mProgressDrawable = getResources().getDrawable(R.drawable.progress_indeterminate_tomahawk);

        mTitle = mDrawerTitle = getTitle();

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        mNowPlayingFrame = findViewById(R.id.now_playing_frame);
        mNowPlayingFrame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mContentViewer.showHub(ContentViewer.HUB_ID_PLAYBACK);
            }
        });

        if (mDrawerLayout != null) {
            mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.drawable.ic_drawer,
                    R.string.drawer_open, R.string.drawer_close) {

                /** Called when a drawer has settled in a completely closed state. */
                public void onDrawerClosed(View view) {
                    getSupportActionBar().setTitle(mTitle);
                    supportInvalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
                }

                /** Called when a drawer has settled in a completely open state. */
                public void onDrawerOpened(View drawerView) {
                    getSupportActionBar().setTitle(mDrawerTitle);
                    supportInvalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
                }
            };
            // Set the drawer toggle as the DrawerListener
            mDrawerLayout.setDrawerListener(mDrawerToggle);
        }

        // Set up the TomahawkMenuAdapter. Give it its set of menu item texts and icons to display
        mDrawerList = (ListView) findViewById(R.id.left_drawer);
        TomahawkMenuAdapter slideMenuAdapter = new TomahawkMenuAdapter(this,
                getResources().getStringArray(R.array.slide_menu_items),
                getResources().obtainTypedArray(R.array.slide_menu_items_icons));
        mDrawerList.setAdapter(slideMenuAdapter);

        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());

        // set customization variables on the ActionBar
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayShowCustomEnabled(true);

        // initialize our ContentViewer, which will handle switching the fragments whenever an
        // entry in the slidingmenu is being clicked. Restore our saved state, if one exists.
        mContentViewer = new ContentViewer(this, getSupportFragmentManager(),
                R.id.content_viewer_frame);
        if (savedInstanceState == null) {
            mContentViewer.showHub(ContentViewer.HUB_ID_COLLECTION);
        } else {
            ArrayList<ContentViewer.FragmentStateHolder> storedBackStack
                    = new ArrayList<ContentViewer.FragmentStateHolder>();
            if (savedInstanceState
                    .getSerializable(COLLECTION_ID_STOREDBACKSTACK) instanceof ArrayList) {
                storedBackStack = (ArrayList<ContentViewer.FragmentStateHolder>) savedInstanceState
                        .getSerializable(COLLECTION_ID_STOREDBACKSTACK);
            }

            if (storedBackStack != null && storedBackStack.size() > 0) {
                mContentViewer.setBackStack(storedBackStack);
            } else {
                mContentViewer.showHub(ContentViewer.HUB_ID_COLLECTION);
            }
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Sync the toggle state after onRestoreInstanceState has occurred.
        if (mDrawerToggle != null) {
            mDrawerToggle.syncState();
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        //Setup our services
        Intent intent = new Intent(this, PlaybackService.class);
        startService(intent);
        bindService(intent, mPlaybackServiceConnection, Context.BIND_AUTO_CREATE);
        intent = new Intent(this, TomahawkService.class);
        startService(intent);
        bindService(intent, mTomahawkServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (SHOW_PLAYBACKFRAGMENT_ON_STARTUP.equals(getIntent().getAction())) {
            // if this Activity is being shown after the user clicked the notification
            mContentViewer.showHub(ContentViewer.HUB_ID_PLAYBACK);
        }
        if (getIntent().hasExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE)) {
            ContentViewer.FragmentStateHolder fragmentStateHolder = mContentViewer.getBackStack()
                    .get(0);
            fragmentStateHolder.tomahawkListItemType = TomahawkService.AUTHENTICATOR_ID;
            fragmentStateHolder.tomahawkListItemKey = String.valueOf(
                    getIntent().getIntExtra(TomahawkService.AUTHENTICATOR_ID, -1));
        }

        SourceList sl = ((TomahawkApp) getApplication()).getSourceList();
        mUserCollection = (UserCollection) sl
                .getCollectionFromId(sl.getLocalSource().getCollection().getId());
        if (mPlaybackService != null) {
            setNowPlayingInfo();
        }

        getSupportLoaderManager().destroyLoader(0);
        getSupportLoaderManager().initLoader(0, null, this);

        if (mTomahawkMainReceiver == null) {
            mTomahawkMainReceiver = new TomahawkMainReceiver();
        }

        // Register intents that the BroadcastReceiver should listen to
        IntentFilter intentFilter = new IntentFilter(Collection.COLLECTION_UPDATED);
        registerReceiver(mTomahawkMainReceiver, intentFilter);
        intentFilter = new IntentFilter(PlaybackService.BROADCAST_NEWTRACK);
        registerReceiver(mTomahawkMainReceiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mTomahawkMainReceiver != null) {
            unregisterReceiver(mTomahawkMainReceiver);
            mTomahawkMainReceiver = null;
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mPlaybackService != null) {
            unbindService(mPlaybackServiceConnection);
        }
        if (mTomahawkService != null) {
            unbindService(mTomahawkServiceConnection);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        bundle.putSerializable(COLLECTION_ID_STOREDBACKSTACK,
                getContentViewer().getBackStack());
        super.onSaveInstanceState(bundle);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;
        getSupportActionBar().setTitle(mTitle);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.clear();
        getMenuInflater().inflate(R.menu.tomahawk_main_menu, menu);
        // customize the searchView
        final MenuItem searchItem = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        View searchEditText = searchView
                .findViewById(android.support.v7.appcompat.R.id.search_plate);
        searchEditText.setBackgroundResource(R.drawable.edit_text_holo_dark);
        searchView.setQueryHint(getString(R.string.searchfragment_title_string));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (query != null && !TextUtils.isEmpty(query)) {
                    ContentViewer.FragmentStateHolder fragmentStateHolder
                            = new ContentViewer.FragmentStateHolder(SearchableFragment.class,
                            null);
                    fragmentStateHolder.queryString = query;
                    mContentViewer.replace(fragmentStateHolder, false);
                    if (searchItem != null) {
                        MenuItemCompat.collapseActionView(searchItem);
                    }
                    searchView.clearFocus();
                    return true;
                }
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
        searchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override
            public boolean onSuggestionSelect(int position) {
                return false;
            }

            @Override
            public boolean onSuggestionClick(int position) {
                return false;
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // If the nav drawer is open, hide action items related to the content view
        if (mDrawerLayout != null) {
            boolean drawerOpen = mDrawerLayout.isDrawerOpen(mDrawerList);
            getSupportActionBar().setDisplayShowCustomEnabled(!drawerOpen);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        return mDrawerToggle != null && mDrawerToggle.onOptionsItemSelected(item) ||
                super.onOptionsItemSelected(item);
    }

    /**
     * If the PlaybackService signals, that it is ready, this method is being called
     */
    @Override
    public void onPlaybackServiceReady() {
        updateViewVisibility();
        setNowPlayingInfo();
        sendBroadcast(new Intent(PLAYBACKSERVICE_READY));
    }

    @Override
    public void setPlaybackService(PlaybackService ps) {
        mPlaybackService = ps;
    }

    @Override
    public void setTomahawkService(TomahawkService ps) {
        mTomahawkService = ps;
    }

    /**
     * If the TomahawkService signals, that it is ready, this method is being called
     */
    @Override
    public void onTomahawkServiceReady() {
        sendBroadcast(new Intent(TOMAHAWKSERVICE_READY));
    }

    public TomahawkService getTomahawkService() {
        return mTomahawkService;
    }

    /**
     * Whenever the back-button is pressed, go back in the ContentViewer, until the root fragment is
     * reached. After that use the normal back-button functionality.
     */
    @Override
    public void onBackPressed() {
        if (!mContentViewer.back()) {
            super.onBackPressed();
        }
    }

    @Override
    public Loader<Collection> onCreateLoader(int id, Bundle args) {
        return new CollectionLoader(this,
                ((TomahawkApp) getApplication()).getSourceList().getLocalSource().getCollection());
    }

    @Override
    public void onLoaderReset(Loader<Collection> loader) {
    }

    @Override
    public void onLoadFinished(Loader<Collection> loader, Collection coll) {
        mUserCollection = (UserCollection) coll;
    }

    public PlaybackService getPlaybackService() {
        return mPlaybackService;
    }

    /**
     * Called when a {@link Collection} has been updated.
     */
    protected void onCollectionUpdated() {
        getSupportLoaderManager().restartLoader(0, null, this);
    }

    /**
     * Setup this {@link SearchableFragment}s {@link AutoCompleteTextView}
     */
    private void setupAutoComplete() {
        /*AutoCompleteTextView textView = (AutoCompleteTextView) getSupportActionBar().getCustomView()
                .findViewById(R.id.search_edittext);
        textView.setDropDownBackgroundResource(R.drawable.menu_dropdown_panel_tomahawk);
        ArrayList<String> autoCompleteSuggestions = getAutoCompleteArray();
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, autoCompleteSuggestions);
        textView.setAdapter(adapter);*/
    }

    /**
     * Add the given {@link String} to the {@link ArrayList}, which is being persisted as a {@link
     * android.content.SharedPreferences}
     */
    public void addToAutoCompleteArray(String newString) {
        ArrayList<String> myArrayList = getAutoCompleteArray();
        int highestIndex = myArrayList.size();

        for (String aMyArrayList : myArrayList) {
            if (newString != null && newString.equals(aMyArrayList)) {
                return;
            }
        }

        myArrayList.add(newString);

        SharedPreferences sPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        SharedPreferences.Editor sEdit = sPrefs.edit();

        sEdit.putString("autocomplete_" + highestIndex, myArrayList.get(highestIndex));
        sEdit.putInt("autocomplete_size", myArrayList.size());
        sEdit.commit();
    }

    /**
     * @return the {@link ArrayList} of {@link String}s containing every {@link String} in our
     * autocomplete array
     */
    public ArrayList<String> getAutoCompleteArray() {
        SharedPreferences sPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        ArrayList<String> myAList = new ArrayList<String>();
        int size = sPrefs.getInt("autocomplete_size", 0);

        for (int j = 0; j < size; j++) {
            myAList.add(sPrefs.getString("autocomplete_" + j, null));
        }
        return myAList;
    }

    /**
     * Sets the playback information
     */
    public void setNowPlayingInfo() {
        Track track = null;
        if (mPlaybackService != null) {
            track = mPlaybackService.getCurrentTrack();
        }
        if (mNowPlayingFrame != null) {
            ImageView nowPlayingInfoAlbumArt = (ImageView) mNowPlayingFrame
                    .findViewById(R.id.now_playing_album_art);
            TextView nowPlayingInfoArtist = (TextView) mNowPlayingFrame
                    .findViewById(R.id.now_playing_artist);
            TextView nowPlayingInfoTitle = (TextView) mNowPlayingFrame
                    .findViewById(R.id.now_playing_title);

            if (track != null) {
                if (nowPlayingInfoAlbumArt != null && nowPlayingInfoArtist != null
                        && nowPlayingInfoTitle != null) {
                    if (track.getAlbum() != null) {
                        TomahawkUtils.loadImageIntoImageView(this, nowPlayingInfoAlbumArt,
                                track.getAlbum());
                    }
                    nowPlayingInfoArtist.setText(track.getArtist().toString());
                    nowPlayingInfoTitle.setText(track.getName());
                }
            }
        }
    }

    public void updateViewVisibility() {
        ContentViewer.FragmentStateHolder currentFSH = mContentViewer
                .getCurrentFragmentStateHolder();
        if (currentFSH.clss == PlaybackFragment.class
                || mPlaybackService == null || mPlaybackService.getCurrentQuery() == null) {
            setNowPlayingInfoVisibility(false);
        } else {
            setNowPlayingInfoVisibility(true);
        }
        if (currentFSH.clss == SearchableFragment.class) {
            setSearchPanelVisibility(true);
        } else {
            setSearchPanelVisibility(false);
        }
    }

    public void setSearchPanelVisibility(boolean enabled) {
        View searchPanel = findViewById(R.id.search_panel);
        if (searchPanel != null) {
            if (enabled) {
                searchPanel.setVisibility(View.VISIBLE);
            } else {
                searchPanel.setVisibility(View.GONE);
            }
        }
    }

    public void setNowPlayingInfoVisibility(boolean enabled) {
        if (enabled) {
            mNowPlayingFrame.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            mNowPlayingFrame.setVisibility(View.VISIBLE);
            if (mPlaybackService != null) {
                setNowPlayingInfo();
            }
        } else {
            mNowPlayingFrame.setLayoutParams(new LinearLayout.LayoutParams(0, 0));
            mNowPlayingFrame.setVisibility(View.GONE);
        }
    }

    /**
     * Returns this {@link Activity}s current {@link org.tomahawk.libtomahawk.collection.UserCollection}.
     *
     * @return the current {@link org.tomahawk.libtomahawk.collection.UserCollection} in this {@link
     * Activity}.
     */
    public UserCollection getUserCollection() {
        return mUserCollection;
    }

    /**
     * @return the mContentViewer
     */
    public ContentViewer getContentViewer() {
        return mContentViewer;
    }

    /**
     * Start the loading animation. Called when beginning login process.
     */
    public void startLoadingAnimation() {
        mAnimationHandler.sendEmptyMessageDelayed(MSG_UPDATE_ANIMATION, 50);
    }

    /**
     * Stop the loading animation. Called when login/logout process has finished.
     */
    public void stopLoadingAnimation() {
        mAnimationHandler.removeMessages(MSG_UPDATE_ANIMATION);
        getSupportActionBar().setLogo(R.drawable.ic_launcher);
    }
}
