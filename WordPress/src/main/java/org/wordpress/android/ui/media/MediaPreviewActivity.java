package org.wordpress.android.ui.media;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;

import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.fluxc.model.MediaModel;
import org.wordpress.android.fluxc.model.SiteModel;
import org.wordpress.android.fluxc.store.MediaStore;
import org.wordpress.android.fluxc.tools.FluxCImageLoader;
import org.wordpress.android.util.AniUtils;
import org.wordpress.android.util.ToastUtils;
import org.wordpress.android.widgets.WPViewPagerTransformer;
import org.wordpress.android.widgets.WPViewPagerTransformer.TransformType;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

public class MediaPreviewActivity extends AppCompatActivity implements MediaPreviewFragment.OnMediaTappedListener {

    private int mMediaId;
    private String mContentUri;

    private SiteModel mSite;

    private Toolbar mToolbar;
    private ViewPager mViewPager;

    private static final long FADE_DELAY_MS = 3000;
    private final Handler mFadeHandler = new Handler();

    @Inject MediaStore mMediaStore;
    @Inject FluxCImageLoader mImageLoader;

    /**
     * @param context     self explanatory
     * @param site        optional site this media is associated with
     * @param contentUri  URI of media - can be local or remote
     * @param isVideo     whether the passed media is a video - assumed to be an image otherwise
     */
    public static void showPreview(Context context,
                                   SiteModel site,
                                   String contentUri) {
        Intent intent = new Intent(context, MediaPreviewActivity.class);
        intent.putExtra(MediaPreviewFragment.ARG_MEDIA_CONTENT_URI, contentUri);
        if (site != null) {
            intent.putExtra(WordPress.SITE, site);
        }

        startIntent(context, intent);
    }

    /**
     * @param context     self explanatory
     * @param site        optional site this media is associated with
     * @param media       media model
     */
    public static void showPreview(Context context,
                                   SiteModel site,
                                   MediaModel media) {
        Intent intent = new Intent(context, MediaPreviewActivity.class);
        intent.putExtra(MediaPreviewFragment.ARG_MEDIA_ID, media.getId());
        intent.putExtra(MediaPreviewFragment.ARG_MEDIA_CONTENT_URI, media.getUrl());

        if (site != null) {
            intent.putExtra(WordPress.SITE, site);
        }

        startIntent(context, intent);
    }

    private static void startIntent(Context context, Intent intent) {
        ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(
                context,
                R.anim.fade_in,
                R.anim.fade_out);
        ActivityCompat.startActivity(context, intent, options.toBundle());
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((WordPress) getApplication()).component().inject(this);

        setContentView(R.layout.media_preview_activity);

        if (savedInstanceState != null) {
            mSite = (SiteModel) savedInstanceState.getSerializable(WordPress.SITE);
            mMediaId = savedInstanceState.getInt(MediaPreviewFragment.ARG_MEDIA_ID);
            mContentUri = savedInstanceState.getString(MediaPreviewFragment.ARG_MEDIA_CONTENT_URI);
        } else {
            mSite = (SiteModel) getIntent().getSerializableExtra(WordPress.SITE);
            mMediaId = getIntent().getIntExtra(MediaPreviewFragment.ARG_MEDIA_ID, 0);
            mContentUri = getIntent().getStringExtra(MediaPreviewFragment.ARG_MEDIA_CONTENT_URI);
        }

        if (TextUtils.isEmpty(mContentUri)) {
            delayedFinish();
            return;
        }

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        int toolbarColor = ContextCompat.getColor(this, R.color.transparent);
        mToolbar.setBackgroundDrawable(new ColorDrawable(toolbarColor));
        setSupportActionBar(mToolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        View fragmentContainer = findViewById(R.id.fragment_container);
        mViewPager = (ViewPager) findViewById(R.id.viewpager);

        // when we have a site and a media object use a ViewPager so the user can swipe through the library, otherwise
        // we're previewing a local file so use a single fragment rather than a ViewPager
        if (mSite != null && mMediaId != 0) {
            fragmentContainer.setVisibility(View.GONE);
            mViewPager.setVisibility(View.VISIBLE);
            mViewPager.setPageTransformer(false, new WPViewPagerTransformer(TransformType.SLIDE_OVER));
            mViewPager.setAdapter(getPagerAdapter());
        } else {
            fragmentContainer.setVisibility(View.VISIBLE);
            mViewPager.setVisibility(View.GONE);
            showPreviewFragment();
        }

        mFadeHandler.postDelayed(fadeOutRunnable, FADE_DELAY_MS);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(MediaPreviewFragment.ARG_MEDIA_CONTENT_URI, mContentUri);
    }

    private void delayedFinish() {
        ToastUtils.showToast(this, R.string.error_media_not_found);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, 1500);
    }

    /*
     * shows a single preview fragment within this activity - called when we can't use a ViewPager to swipe
     * between media (ie: we're previewing a local file)
     */
    private void showPreviewFragment() {
        MediaPreviewFragment fragment;
        MediaModel media = mMediaStore.getMediaWithLocalId(mMediaId);
        if (media != null) {
            fragment = MediaPreviewFragment.newInstance(mSite, media);
        } else {
            fragment = MediaPreviewFragment.newInstance(mSite, mContentUri);
        }
        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment, MediaPreviewFragment.TAG)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commit();

        fragment.setOnMediaTappedListener(this);
    }

    private final Runnable fadeOutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing() && mToolbar.getVisibility() == View.VISIBLE) {
                AniUtils.startAnimation(mToolbar, R.anim.toolbar_fade_out_and_up, new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) { }
                    @Override
                    public void onAnimationEnd(Animation animation) {
                        mToolbar.setVisibility(View.GONE);
                    }
                    @Override
                    public void onAnimationRepeat(Animation animation) { }
                });
            }
        }
    };

    private void showToolbar() {
        if (!isFinishing()) {
            mFadeHandler.removeCallbacks(fadeOutRunnable);
            mFadeHandler.postDelayed(fadeOutRunnable, FADE_DELAY_MS);
            if (mToolbar.getVisibility() != View.VISIBLE) {
                AniUtils.startAnimation(mToolbar, R.anim.toolbar_fade_in_and_down, new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                        mToolbar.setVisibility(View.VISIBLE);
                    }
                    @Override
                    public void onAnimationEnd(Animation animation) { }
                    @Override
                    public void onAnimationRepeat(Animation animation) { }
                });
            }
        }
    }

    private MediaPagerAdapter mPagerAdapter;
    private MediaPagerAdapter getPagerAdapter() {
        if (mPagerAdapter == null) {
            final List<MediaModel> mediaList = mMediaStore.getAllSiteMedia(mSite);
            mPagerAdapter = new MediaPagerAdapter(getFragmentManager());
            mPagerAdapter.setMediaList(mediaList);

            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    int initialPos = 0;
                    for (int i = 0; i < mediaList.size(); i++) {
                        if (mediaList.get(i).getId() == mMediaId) {
                            initialPos = i;
                            break;
                        }
                    }
                    mViewPager.setCurrentItem(initialPos);
                }
            });
        }
        return mPagerAdapter;
    }

    @Override
    public void onMediaTapped() {
        showToolbar();
    }

    private class MediaPagerAdapter extends FragmentStatePagerAdapter {
        private final List<MediaModel> mMediaList = new ArrayList<>();

        public MediaPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        void setMediaList(@NonNull List<MediaModel> mediaList) {
            mMediaList.clear();
            mMediaList.addAll(mediaList);
            notifyDataSetChanged();
        }

        @Override
        public Fragment getItem(int position) {
            MediaPreviewFragment fragment = MediaPreviewFragment.newInstance(mSite, mMediaList.get(position));
            fragment.setOnMediaTappedListener(MediaPreviewActivity.this);
            return fragment;
        }

        @Override
        public int getCount() {
            return mMediaList.size();
        }
    }
}
