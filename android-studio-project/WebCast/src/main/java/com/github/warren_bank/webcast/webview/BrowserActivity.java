package com.github.warren_bank.webcast.webview;

import com.github.warren_bank.webcast.R;
import com.github.warren_bank.webcast.ExitActivity;
import com.github.warren_bank.webcast.WebCastApplication;
import com.github.warren_bank.webcast.webview.single_page_app.ExoAirPlayerSenderActivity;
import com.github.warren_bank.webcast.webview.single_page_app.HlsProxyConfigurationActivity;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.snackbar.Snackbar;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import androidx.media3.common.MimeTypes;

import java.lang.reflect.Method;
import java.util.ArrayList;

public class BrowserActivity extends AppCompatActivity {

    // ---------------------------------------------------------------------------------------------
    // Data Structures:
    // ---------------------------------------------------------------------------------------------

    // Stored Preferences --------------------------------------------------------------------------

    private static final String PREFS_FILENAME = "DRAWER_LIST_ITEMS";
    private static final String PREF_BOOKMARKS = "BOOKMARKS";

    // Nested Class Definitions --------------------------------------------------------------------

    private static final class DrawerListItem {
        public final String uri;
        public       String title;
        public final String mimeType;
        public final String referer;

        public DrawerListItem(String uri, String title, String mimeType, String referer) {
            this.uri      = uri;
            this.title    = title;
            this.mimeType = mimeType;
            this.referer  = referer;
        }

        @Override
        public String toString() {
            return ((title != null) && (title.length() > 0)) ? title : uri;
        }

        public boolean equal(DrawerListItem that) {
            return (this.uri.equals(that.uri));
        }

        public boolean equal(String that_uri) {
            return (this.uri.equals(that_uri));
        }

        // helpers

        public static ArrayList<DrawerListItem> fromJson(String jsonBookmarks) {
            ArrayList<DrawerListItem> arrayList;
            Gson gson = new Gson();
            arrayList = gson.fromJson(jsonBookmarks, new TypeToken<ArrayList<DrawerListItem>>(){}.getType());
            return arrayList;
        }

        public static DrawerListItem find(ArrayList<DrawerListItem> items, DrawerListItem item) {
            for (int i=0; i < items.size(); i++) {
                DrawerListItem nextItem = items.get(i);
                if (nextItem.equal(item)) return nextItem;
            }
            return null;
        }

        public static DrawerListItem find(ArrayList<DrawerListItem> items, String uri) {
            for (int i=0; i < items.size(); i++) {
                DrawerListItem nextItem = items.get(i);
                if (nextItem.equal(uri)) return nextItem;
            }
            return null;
        }

        public static boolean contains(ArrayList<DrawerListItem> items, DrawerListItem item) {
            DrawerListItem matchingItem = find(items, item);
            return (matchingItem != null);
        }

        public static boolean contains(ArrayList<DrawerListItem> items, String uri) {
            DrawerListItem matchingItem = find(items, uri);
            return (matchingItem != null);
        }
    }

    // Drawers -------------------------------------------------------------------------------------

    private DrawerLayout                 drawer_layout;

    private ViewGroup                    drawer_left_bookmarks_viewGroup;
    private ListView                     drawer_left_bookmarks_listView;
    private ArrayList<DrawerListItem>    drawer_left_bookmarks_arrayList;
    private ArrayAdapter<DrawerListItem> drawer_left_bookmarks_arrayAdapter;

    private ViewGroup                    drawer_right_videos_viewGroup;
    private ListView                     drawer_right_videos_listView;
    private ArrayList<DrawerListItem>    drawer_right_videos_arrayList;
    private ArrayAdapter<DrawerListItem> drawer_right_videos_arrayAdapter;
    private View                         drawer_right_videos_icon_watch_all;

    // Content: WebView ----------------------------------------------------------------------------

    private static final String default_page_url = "about:blank";

    private String current_page_url;
    private WebView webView;
    private boolean shouldUpdateWebViewDebugConfigs;
    private boolean shouldClearWebView;
    private BrowserWebViewClient webViewClient;
    private BrowserDownloadListener downloadListener;
    private ProgressBar progressBar;

    // Content: Search Form ------------------------------------------------------------------------

    private SearchView search;

    // Content: UI ---------------------------------------------------------------------------------

    private ViewGroup parentView;
    private Snackbar snackbar;
    private AlertDialog alertDialog;

    // ---------------------------------------------------------------------------------------------
    // Lifecycle Events:
    // ---------------------------------------------------------------------------------------------

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.browser_activity);

        // Drawers ---------------------------------------------------------------------------------

        initDrawers();

        // Content: ActionBar ----------------------------------------------------------------------

        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("");
        toolbar.setNavigationIcon(null);

        // Content: WebView ------------------------------------------------------------------------

        current_page_url = default_page_url;

        Intent intent = getIntent();
        if (intent.hasExtra("url")) {
            current_page_url = intent.getStringExtra("url");
        }
        else {
            String action = intent.getAction();

            if (Intent.ACTION_VIEW.equals(action)) {
                current_page_url = intent.getDataString();
            }
        }

        webView     = (WebView)findViewById(R.id.webView);
        progressBar = (ProgressBar)findViewById(R.id.progressBar);
        initWebView();

        // Content: Search Form --------------------------------------------------------------------

        search = (SearchView)findViewById(R.id.search);

        search.setIconifiedByDefault(false);
        search.setSubmitButtonEnabled(true);

        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                String url = query.contains(":") ? query : ("http://" + query);

                updateCurrentPage(url, true);
                BrowserUtils.hideKeyboard(BrowserActivity.this);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // noop
                return true;
            }
        });

        // Content: UI -----------------------------------------------------------------------------

        parentView = (ViewGroup)findViewById(R.id.viewgroup_content);
    }

    @Override
    public void onStart() {
        super.onStart();

        updateCurrentPage(current_page_url, false);

        if (current_page_url.equals(default_page_url)) {
            openDrawerBookmarks();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        WebCastApplication.activityResumed();

        if (shouldUpdateWebViewDebugConfigs) {
            shouldUpdateWebViewDebugConfigs = false;
            BrowserDebugUtils.configWebView(BrowserActivity.this);
        }

        webView.loadUrl(current_page_url);
        shouldClearWebView = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        WebCastApplication.activityPaused();

        webView.loadUrl(default_page_url);
    }

    @Override
    public void onStop() {
        super.onStop();

        if (shouldClearWebView)
            clearWebView();
    }

    // ---------------------------------------------------------------------------------------------
    // User Events:
    // ---------------------------------------------------------------------------------------------

    @Override
    public void onBackPressed() {
        if ((alertDialog instanceof AlertDialog) && alertDialog.isShowing()) {
            alertDialog.dismiss();
            return;
        }

        if ((snackbar instanceof Snackbar) && snackbar.isShown()) {
            snackbar.dismiss();
            return;
        }

        if (closeDrawerVideos()) {
            return;
        }

        if (closeDrawerBookmarks()) {
            return;
        }

        if (webView.canGoBack()) {
            webView.goBack();
            return;
        }

        super.onBackPressed();
    }

    // ---------------------------------------------------------------------------------------------
    // Bookmarks:
    // ---------------------------------------------------------------------------------------------

    private void setSavedBookmarks() {
        String jsonBookmarks = new Gson().toJson(drawer_left_bookmarks_arrayList);

        setSavedBookmarks(jsonBookmarks);
    }

    private void setSavedBookmarks(String jsonBookmarks) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE);

        setSavedBookmarks(sharedPreferences, jsonBookmarks);
    }

    private void setSavedBookmarks(SharedPreferences sharedPreferences, String jsonBookmarks) {
        SharedPreferences.Editor prefs_editor = sharedPreferences.edit();
        prefs_editor.putString(PREF_BOOKMARKS, jsonBookmarks);
        prefs_editor.apply();
    }

    private ArrayList<DrawerListItem> getSavedBookmarks() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE);
        String jsonBookmarks = sharedPreferences.getString(PREF_BOOKMARKS, null);

        if (jsonBookmarks == null) {
            jsonBookmarks = BrowserConfigs.getDefaultBookmarks(BrowserActivity.this);

            // update SharedPreferences
            setSavedBookmarks(sharedPreferences, jsonBookmarks);
        }

        ArrayList<DrawerListItem> savedBookmarks = DrawerListItem.fromJson(jsonBookmarks);
        return savedBookmarks;
    }

    private void updateSavedBookmark(DrawerListItem item, boolean is_add) {
        boolean is_saved = DrawerListItem.contains(drawer_left_bookmarks_arrayList, item);
        String message = null;

        // sanity checks
        if (is_add && is_saved) {
            message = "Bookmarked";
        }
        if (!is_add && !is_saved) {
            message = "Bookmark Removed";
        }
        if ((message != null) && (message.length() > 0)) {
            snackbar = Snackbar.make(parentView, message, Snackbar.LENGTH_SHORT);
            snackbar.show();
            return;
        }

        if (is_add) {
            drawer_left_bookmarks_arrayList.add(item);
            message = "Bookmarked";
        }
        else {
            // up until this point, equality has been tested by value.
            // now, equality will be tested by object reference.
            //  => need to obtain reference to object in array.
            DrawerListItem matchingItem = DrawerListItem.find(drawer_left_bookmarks_arrayList, item);

            drawer_left_bookmarks_arrayList.remove(matchingItem);
            message = "Bookmark Removed";
        }

        // notify the ListView adapter
        drawer_left_bookmarks_arrayAdapter.notifyDataSetChanged();

        // update SharedPreferences
        setSavedBookmarks();

        // show message
        snackbar = Snackbar.make(parentView, message, Snackbar.LENGTH_SHORT);
        snackbar.show();

        // update 'bookmark' icon in top ActionBar
        invalidateOptionsMenu();
    }

    private void toggleSavedBookmark(DrawerListItem item) {
        boolean is_add = (DrawerListItem.contains(drawer_left_bookmarks_arrayList, item) == false);
        updateSavedBookmark(item, is_add);
    }

    private void addSavedBookmark(DrawerListItem item) {
        updateSavedBookmark(item, true);
    }

    private void removeSavedBookmark(DrawerListItem item) {
        updateSavedBookmark(item, false);
    }

    private void showDialog_options_modifySavedBookmark(DrawerListItem item) {
        alertDialog = new AlertDialog.Builder(BrowserActivity.this)
            .setTitle("Edit Bookmark")
            .setMessage(item.uri)
            // button #1 of 3
            .setNeutralButton("Dismiss", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                }
            })
            // button #2 of 3
            .setNegativeButton("DELETE", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();

                    showDialog_confirm_removeSavedBookmark(item);
                }
            })
            // button #3 of 3
            .setPositiveButton("RENAME", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();

                    showDialog_edit_renameSavedBookmark(item);
                }
            })
            .show();
    }

    private void showDialog_edit_renameSavedBookmark(DrawerListItem item) {
        final EditText editText = new EditText(BrowserActivity.this);

        editText.setText(item.title, TextView.BufferType.EDITABLE);

        alertDialog = new AlertDialog.Builder(BrowserActivity.this)
            .setTitle("Rename Bookmark")
            .setMessage(item.uri)
            .setView(editText)
            .setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    BrowserUtils.hideKeyboard(BrowserActivity.this, editText);
                    dialogInterface.dismiss();
                }
            })
            .setPositiveButton("SAVE", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    BrowserUtils.hideKeyboard(BrowserActivity.this, editText);
                    dialogInterface.dismiss();

                    // update DrawerListItem
                    item.title = String.valueOf(editText.getText());

                    // notify the ListView adapter
                    drawer_left_bookmarks_arrayAdapter.notifyDataSetChanged();

                    // update SharedPreferences
                    setSavedBookmarks();
                }
            })
            .show();
    }

    private void showDialog_confirm_removeSavedBookmark(DrawerListItem item) {
        alertDialog = new AlertDialog.Builder(BrowserActivity.this)
            .setTitle("DELETE")
            .setMessage("Confirm that you want to delete this bookmark?")
            .setNegativeButton("NO", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                }
            })
            .setPositiveButton("YES", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();

                    removeSavedBookmark(item);
                }
            })
            .show();
    }

    // ---------------------------------------------------------------------------------------------
    // Videos:
    // ---------------------------------------------------------------------------------------------

    private void resetSavedVideos() {
        drawer_right_videos_arrayList.clear();

        // notify the ListView adapter
        drawer_right_videos_arrayAdapter.notifyDataSetChanged();

        // hide icon
        drawer_right_videos_icon_watch_all.setVisibility(View.GONE);
    }

    protected void addSavedVideo(String uri, String mimeType, String referer) {
        if (DrawerListItem.contains(drawer_right_videos_arrayList, uri)) return;

        DrawerListItem item = new DrawerListItem(
            uri,
            /* title= */ null,
            mimeType,
            referer
        );

        drawer_right_videos_arrayList.add(item);

        // notify the ListView adapter
        drawer_right_videos_arrayAdapter.notifyDataSetChanged();

        // conditionally show icon
        if ((drawer_right_videos_arrayList.size() > 1) && (BrowserUtils.getVideoPlayerPreferenceIndex(BrowserActivity.this) == 0)) {
            drawer_right_videos_icon_watch_all.setVisibility(View.VISIBLE);
        }
    }

    private void removeSavedVideo(DrawerListItem item) {
        if (drawer_right_videos_arrayList.remove(item)) {
            // notify the ListView adapter
            drawer_right_videos_arrayAdapter.notifyDataSetChanged();

            // conditionally hide icon
            if (drawer_right_videos_arrayList.size() <= 1) {
                drawer_right_videos_icon_watch_all.setVisibility(View.GONE);
            }
        }
    }

    protected boolean isVideo(String mimeType) {
        if (mimeType == null) return false;

        switch (mimeType) {
            case MimeTypes.APPLICATION_M3U8:
            case MimeTypes.APPLICATION_MPD:
            case MimeTypes.APPLICATION_SS:
                return true;
            default:
                return MimeTypes.isVideo(mimeType);
        }
    }

    private void showDialog_confirm_removeSavedVideo(DrawerListItem item) {
        alertDialog = new AlertDialog.Builder(BrowserActivity.this)
            .setTitle("DELETE")
            .setMessage("Confirm that you want to delete this video?")
            .setNegativeButton("NO", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                }
            })
            .setPositiveButton("YES", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();

                    removeSavedVideo(item);
                }
            })
            .show();
    }

    // ---------------------------------------------------------------------------------------------
    // Drawers:
    // ---------------------------------------------------------------------------------------------

    private void initDrawerBookmarks() {
        drawer_left_bookmarks_listView.setAdapter(drawer_left_bookmarks_arrayAdapter);

        drawer_left_bookmarks_listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                DrawerListItem item = (DrawerListItem) parent.getItemAtPosition(position);

                AlertDialog.Builder builder = new AlertDialog.Builder(BrowserActivity.this)
                    .setTitle("Bookmark URL")
                    .setMessage(item.uri)
                    .setNegativeButton("Dismiss", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                        }
                    });

                if (isVideo(item.mimeType)) {
                    builder.setPositiveButton("Watch", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                            closeDrawerBookmarks();

                            openVideo(item);
                        }
                    });
                }
                else {
                    builder.setPositiveButton("Open", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                            closeDrawerBookmarks();

                            updateCurrentPage(item.uri, true);
                        }
                    });
                }

                alertDialog = builder.show();
            }
        });

        drawer_left_bookmarks_listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                DrawerListItem item = (DrawerListItem) parent.getItemAtPosition(position);
                showDialog_options_modifySavedBookmark(item);
                return true;
            }
        });
    }

    private void initDrawerVideos() {
        drawer_right_videos_listView.setAdapter(drawer_right_videos_arrayAdapter);

        drawer_right_videos_listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                DrawerListItem item = (DrawerListItem) parent.getItemAtPosition(position);

                AlertDialog.Builder builder = new AlertDialog.Builder(BrowserActivity.this)
                    .setTitle("Video URL")
                    .setMessage(item.uri)
                    // button #1 of 3
                    .setPositiveButton("Watch", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                            closeDrawerVideos();

                            openVideo(item);
                        }
                    })
                    // button #3 of 3
                    .setNeutralButton("Dismiss", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                        }
                    });

                if (DrawerListItem.contains(drawer_left_bookmarks_arrayList, item) == false) {
                    // button #2 of 3
                    builder.setNegativeButton("Bookmark", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();

                            addSavedBookmark(item);
                        }
                    });
                }

                alertDialog = builder.show();
            }
        });

        drawer_right_videos_listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                DrawerListItem item = (DrawerListItem) parent.getItemAtPosition(position);
                showDialog_confirm_removeSavedVideo(item);
                return true;
            }
        });

        // hide icon
        drawer_right_videos_icon_watch_all.setVisibility(View.GONE);
    }

    private void initDrawers() {
        drawer_layout                      = (DrawerLayout)findViewById(R.id.drawer_layout);

        drawer_left_bookmarks_viewGroup    = (ViewGroup)findViewById(R.id.viewgroup_drawer_left_bookmarks);
        drawer_left_bookmarks_listView     = (ListView)findViewById(R.id.drawer_left_bookmarks);
        drawer_left_bookmarks_arrayList    = getSavedBookmarks();
        drawer_left_bookmarks_arrayAdapter = new ArrayAdapter<DrawerListItem>(BrowserActivity.this, R.layout.singleline_listitem, drawer_left_bookmarks_arrayList);

        drawer_right_videos_viewGroup      = (ViewGroup)findViewById(R.id.viewgroup_drawer_right_videos);
        drawer_right_videos_listView       = (ListView)findViewById(R.id.drawer_right_videos);
        drawer_right_videos_arrayList      = new ArrayList<DrawerListItem>();
        drawer_right_videos_arrayAdapter   = new ArrayAdapter<DrawerListItem>(BrowserActivity.this, R.layout.singleline_listitem, drawer_right_videos_arrayList);
        drawer_right_videos_icon_watch_all = (View)findViewById(R.id.action_watch_all_videos);

        initDrawerBookmarks();
        initDrawerVideos();

        drawer_layout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened (View drawerView) {
                BrowserUtils.hideKeyboard(BrowserActivity.this);
            }
        });

        // resize each drawer to 85% of total screen width
        BrowserUtils.resizeDrawerWidthByPercentOfScreen(
            BrowserActivity.this,
            getDrawerBookmarks(),
            85.0f
        );
        BrowserUtils.resizeDrawerWidthByPercentOfScreen(
            BrowserActivity.this,
            getDrawerVideos(),
            85.0f
        );
    }

    private void toggleDrawer(View drawer, boolean animate) {
        if (drawer_layout.isDrawerOpen(drawer)) {
            drawer_layout.closeDrawer(drawer, animate);
        }
        else {
            drawer_layout.openDrawer( drawer, animate);
        }
    }

    private void toggleDrawer(View drawer) {
        toggleDrawer(drawer, true);
    }

    private void toggleDrawerBookmarks() {
        View drawer = getDrawerBookmarks();
        toggleDrawer(drawer);
    }

    private void toggleDrawerVideos() {
        View drawer = getDrawerVideos();
        toggleDrawer(drawer);
    }

    private boolean openDrawer(View drawer, boolean animate) {
        boolean was_closed = (drawer_layout.isDrawerOpen(drawer) == false);

        if (was_closed) {
            drawer_layout.openDrawer(drawer, animate);
        }
        return was_closed;
    }

    private boolean openDrawer(View drawer) {
        return openDrawer(drawer, true);
    }

    private boolean openDrawerBookmarks() {
        View drawer = getDrawerBookmarks();
        return openDrawer(drawer);
    }

    private boolean openDrawerVideos() {
        View drawer = getDrawerVideos();
        return openDrawer(drawer);
    }

    private boolean closeDrawer(View drawer, boolean animate) {
        boolean was_open = drawer_layout.isDrawerOpen(drawer);

        if (was_open) {
            drawer_layout.closeDrawer(drawer, animate);
        }
        return was_open;
    }

    private boolean closeDrawer(View drawer) {
        return closeDrawer(drawer, true);
    }

    private boolean closeDrawerBookmarks() {
        View drawer = getDrawerBookmarks();
        return closeDrawer(drawer);
    }

    private boolean closeDrawerVideos() {
        View drawer = getDrawerVideos();
        return closeDrawer(drawer);
    }

    private View getDrawerBookmarks() {
        return (View)drawer_left_bookmarks_viewGroup;
    }

    private View getDrawerVideos() {
        return (View)drawer_right_videos_viewGroup;
    }

    // drawer_right_videos_icon_watch_all
    public void onClick_action_watch_all_videos(View view) {
        closeDrawerVideos();

        openAllVideos();
    }

    // ---------------------------------------------------------------------------------------------
    // WebView:
    // ---------------------------------------------------------------------------------------------

    private void initWebView() {
        BrowserDebugUtils.configWebView(BrowserActivity.this);
        shouldUpdateWebViewDebugConfigs = false;

        webViewClient = new BrowserWebViewClient(BrowserActivity.this) {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
                updateCurrentPage(url, false);
                resetSavedVideos();
                invalidateOptionsMenu();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                invalidateOptionsMenu();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                progressBar.setVisibility(View.GONE);
                invalidateOptionsMenu();
            }
        };

        downloadListener = new BrowserDownloadListener(webViewClient);

        webView.setWebViewClient(webViewClient);
        webView.setDownloadListener(downloadListener);

        WebSettings webSettings = webView.getSettings();
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(true);
        webSettings.setUseWideViewPort(false);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setUserAgentString(
            getResources().getString(R.string.user_agent)
        );
        if (Build.VERSION.SDK_INT >= 17) {
            webSettings.setMediaPlaybackRequiresUserGesture(false);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        webView.setInitialScale(0);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setVerticalScrollBarEnabled(false);
        webView.clearCache(true);
        webView.clearHistory();
    }

    private void updateCurrentPage(String uri, boolean loadUrl) {
        if (uri == null) return;

        uri = uri.trim();
        if (uri.isEmpty()) return;

        current_page_url = uri;
        search.setQueryHint(current_page_url);
        search.setQuery(current_page_url, false);

        if (loadUrl) {
            webView.loadUrl(current_page_url);
        }
    }

    private void clearWebView() {
        webView.clearCache(true);
        webView.clearHistory();
        webView.clearFormData();
        webView.clearSslPreferences();
        WebStorage.getInstance().deleteAllData();
        if (Build.VERSION.SDK_INT >= 21) {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
        }
        else {
            CookieManager.getInstance().removeAllCookie();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Intent:
    // ---------------------------------------------------------------------------------------------

    private void openVideos(ArrayList<String> arrSources) {
        String jsonSources = new Gson().toJson(arrSources);
        Intent in = new Intent(BrowserActivity.this, com.github.warren_bank.webcast.exoplayer2.VideoActivity.class);

        in.putExtra("video_sources", jsonSources);
        startActivity(in);
    }

    private void openVideoInWebcastReloadedSPA(DrawerListItem item, Class WebcastReloadedSPA) {
        openVideoInWebcastReloadedSPA(item, WebcastReloadedSPA, /* force_internal= */ false);
    }

    private void openVideoInWebcastReloadedSPA(DrawerListItem item, Class WebcastReloadedSPA, boolean force_internal) {
        Intent internal_intent = new Intent(BrowserActivity.this, WebcastReloadedSPA);
        Uri    internal_data   = Uri.parse(item.uri);
        String internal_type   = Intent.normalizeMimeType(item.mimeType);

        internal_intent.setAction(Intent.ACTION_VIEW);
        internal_intent.setDataAndType(internal_data, internal_type);

        // reuse names of ExoAirPlayer extras
        internal_intent.putExtra("referUrl", item.referer);

        try {
            if (force_internal) {
                throw new Exception("always open in internal WebView");
            }

            Method method_get_page_url_base = WebcastReloadedSPA.getMethod("get_page_url_base", new Class[]{Context.class});
            Method method_get_page_url_href = WebcastReloadedSPA.getMethod("get_page_url_href", new Class[]{String.class, Intent.class});

            Intent external_intent   = new Intent();
            String external_url_base = (String) method_get_page_url_base.invoke(null, BrowserActivity.this);
            String external_url_href = (String) method_get_page_url_href.invoke(null, external_url_base, internal_intent);
            Uri    external_data     = Uri.parse(external_url_href);
            String external_type     = "text/html";

            external_intent.setAction(Intent.ACTION_VIEW);
            external_intent.setDataAndType(external_data, external_type);

            if (external_intent.resolveActivity(getPackageManager()) != null) {
                // present a chooser to open WebcastReloadedSPA in another app
                //
                // for example:
                // * HLS-Proxy configuration
                //   - Android-WebMonkey includes a WebView with matching Intent filter,
                //     and a standard userscript to present a chooser to open the proxied HLS manifest in another app
                //   - this would allow the proxied HLS manifest to be transferred BACK to Android-WebCast,
                //     and viewed in the internal video player, which could then be "cast" to a Chromecast device
            
                // add title to chooser dialog
                external_intent = Intent.createChooser(external_intent, getString(R.string.title_intent_chooser));

                startActivity(external_intent);
            }
            else {
                throw new Exception("fallback to internal WebView");
            }
        }
        catch(Exception e) {
            // open WebcastReloadedSPA in internal WebView

            startActivity(internal_intent);
            shouldClearWebView = false;
        }
    }

    private void openVideo(DrawerListItem item) {
        if (item == null) return;

        int videoPlayerPreferenceIndex = BrowserUtils.getVideoPlayerPreferenceIndex(BrowserActivity.this);

        // only send HLS manifests to HLS-Proxy configuration
        if (
            (videoPlayerPreferenceIndex == 3)
         && (
                (item.mimeType == null)
             || (!item.mimeType.equals("application/x-mpegURL"))
            )
        ) {
            // fallback to internal video player w/ Chromecast sender
            videoPlayerPreferenceIndex = 0;
        }

        switch(videoPlayerPreferenceIndex) {

            case 3: {
                // HLS-Proxy configuration
                openVideoInWebcastReloadedSPA(item, HlsProxyConfigurationActivity.class);
                return;
            }

            case 2: {
                // ExoAirPlayer sender
                openVideoInWebcastReloadedSPA(item, ExoAirPlayerSenderActivity.class, /* force_internal= */ true);
                return;
            }

            case 1: {
                // external video player
                Intent in   = new Intent();
                Uri    data = Uri.parse(item.uri);
                String type = Intent.normalizeMimeType(item.mimeType);

                in.setAction(Intent.ACTION_VIEW);
                in.setDataAndType(data, type);

                // when a local instance of ExoAirPlayer is started by implicit Intent, pass the 'referer' URL
                in.putExtra("referUrl", item.referer);

                if (in.resolveActivity(getPackageManager()) != null) {
                    // add title to chooser dialog
                    in = Intent.createChooser(in, getString(R.string.title_intent_chooser));

                    startActivity(in);
                    return;
                }
                // else: fall through to default case
            }

            case 0:
            default: {
                // internal video player w/ Chromecast sender
                ArrayList<String> arrSources = new ArrayList<String>(3);
                arrSources.add(item.uri);
                arrSources.add(item.mimeType);
                arrSources.add(item.referer);
                openVideos(arrSources);
                return;
            }
        }
    }

    private void openAllVideos() {
        int len = 3 * drawer_right_videos_arrayList.size();
        if (len == 0) return;

        ArrayList<String> arrSources = new ArrayList<String>(len);
        int i;
        DrawerListItem item;
        for (i=0; i < drawer_right_videos_arrayList.size(); i++) {
            item = drawer_right_videos_arrayList.get(i);
            arrSources.add(item.uri);
            arrSources.add(item.mimeType);
            arrSources.add(item.referer);
        }
        openVideos(arrSources);
    }

    // ---------------------------------------------------------------------------------------------
    // ActionBar:
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.browser, menu);

        if (drawer_left_bookmarks_arrayList.size() > 0) {
            if (DrawerListItem.contains(drawer_left_bookmarks_arrayList, current_page_url)) {
                menu.getItem(0).setIcon(R.drawable.ic_bookmark_saved);
            } else {
                menu.getItem(0).setIcon(R.drawable.ic_bookmark_unsaved);
            }
        }
        else {
            menu.getItem(0).setIcon(R.drawable.ic_bookmark_unsaved);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {

        switch(menuItem.getItemId()) {

            case R.id.action_bookmark: {
                DrawerListItem item = new DrawerListItem(
                    /* uri=      */ current_page_url,
                    /* title=    */ webView.getTitle().trim(),
                    /* mimeType= */ null,
                    /* referer=  */ webView.getUrl()
                );
                toggleSavedBookmark(item);
                return true;
            }

            case R.id.action_bookmarks: {
                toggleDrawerBookmarks();
                return true;
            }

            case R.id.action_videos: {
                toggleDrawerVideos();
                return true;
            }

            case R.id.action_settings: {
                Intent in = new Intent(BrowserActivity.this, SettingsActivity.class);
                startActivity(in);
                shouldUpdateWebViewDebugConfigs = true;
                return true;
            }

            case R.id.action_exit: {
                ExitActivity.exit(BrowserActivity.this);
                return true;
            }

            default: {
                return super.onOptionsItemSelected(menuItem);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
}
