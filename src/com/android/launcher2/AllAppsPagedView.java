/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher2;

import java.util.ArrayList;
import java.util.Collections;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import com.android.launcher.R;

/**
 * An implementation of PagedView that populates the pages of the workspace
 * with all of the user's applications.
 */
public class AllAppsPagedView extends PagedView
        implements AllAppsView, View.OnClickListener, View.OnLongClickListener, DragSource {

    private static final String TAG = "AllAppsPagedView";
    private static final boolean DEBUG = false;

    private Launcher mLauncher;
    private DragController mDragController;

    // preserve compatibility with 3D all apps:
    //    0.0 -> hidden
    //    1.0 -> shown and opaque
    //    intermediate values -> partially shown & partially opaque
    private float mZoom;

    // set of all applications
    private ArrayList<ApplicationInfo> mApps;
    private ArrayList<ApplicationInfo> mFilteredApps;

    // the types of applications to filter
    static final int ALL_APPS_FLAG = -1;
    private int mAppFilter = ALL_APPS_FLAG;

    private int mCellCountX;
    private int mCellCountY;

    private final LayoutInflater mInflater;

    public AllAppsPagedView(Context context) {
        this(context, null);
    }

    public AllAppsPagedView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AllAppsPagedView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PagedView, defStyle, 0);
        mCellCountX = a.getInt(R.styleable.PagedView_cellCountX, 6);
        mCellCountY = a.getInt(R.styleable.PagedView_cellCountY, 4);
        mInflater = LayoutInflater.from(context);
        a.recycle();
        setSoundEffectsEnabled(false);
    }

    @Override
    public void setLauncher(Launcher launcher) {
        mLauncher = launcher;
    }

    @Override
    public void setDragController(DragController dragger) {
        mDragController = dragger;
    }

    public void setAppFilter(int filterType) {
        mAppFilter = filterType;
        if (mApps != null) {
            mFilteredApps = rebuildFilteredApps(mApps);
            setCurrentPage(0);
            invalidatePageData();
        }
    }

    @Override
    public void zoom(float zoom, boolean animate) {
        mZoom = zoom;
        cancelLongPress();

        if (isVisible()) {
            getParent().bringChildToFront(this);
            setVisibility(View.VISIBLE);
            if (animate) {
                startAnimation(AnimationUtils.loadAnimation(getContext(),
                        R.anim.all_apps_2d_fade_in));
            } else {
                onAnimationEnd();
            }
        } else {
            if (animate) {
                startAnimation(AnimationUtils.loadAnimation(getContext(),
                        R.anim.all_apps_2d_fade_out));
            } else {
                onAnimationEnd();
            }
        }
    }

    protected void onAnimationEnd() {
        if (!isVisible()) {
            setVisibility(View.GONE);
            mZoom = 0.0f;
        } else {
            mZoom = 1.0f;
        }

        if (mLauncher != null)
            mLauncher.zoomed(mZoom);
    }

    private int getChildIndexForGrandChild(View v) {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; ++i) {
            PagedViewCellLayout layout = (PagedViewCellLayout) getChildAt(i);
            if (layout.indexOfChild(v) > -1) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onClick(View v) {
        int childIndex = getChildIndexForGrandChild(v);
        if (childIndex == getCurrentPage()) {
            final ApplicationInfo app = (ApplicationInfo) v.getTag();

            // animate some feedback to the click
            animateClickFeedback(v, new Runnable() {
                @Override
                public void run() {
                    mLauncher.startActivitySafely(app.intent, app);
                }
            });
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (!v.isInTouchMode()) {
            return false;
        }

        ApplicationInfo app = (ApplicationInfo) v.getTag();
        app = new ApplicationInfo(app);

        mDragController.startDrag(v, this, app, DragController.DRAG_ACTION_COPY);
        return true;
    }

    @Override
    public void onDropCompleted(View target, boolean success) {
        // do nothing
    }

    @Override
    public boolean isVisible() {
        return mZoom > 0.001f;
    }

    @Override
    public boolean isAnimating() {
        return (getAnimation() != null);
    }

    private ArrayList<ApplicationInfo> rebuildFilteredApps(ArrayList<ApplicationInfo> apps) {
        ArrayList<ApplicationInfo> filteredApps = new ArrayList<ApplicationInfo>();
        if (mAppFilter == ALL_APPS_FLAG) {
            return apps;
        } else {
            final int length = apps.size();
            for (int i = 0; i < length; ++i) {
                ApplicationInfo info = apps.get(i);
                if ((info.flags & mAppFilter) > 0) {
                    filteredApps.add(info);
                }
            }
        }
        return filteredApps;
    }

    @Override
    public void setApps(ArrayList<ApplicationInfo> list) {
        mApps = list;
        Collections.sort(mApps, LauncherModel.APP_NAME_COMPARATOR);
        mFilteredApps = rebuildFilteredApps(mApps);
        mPageViewIconCache.clear();
        invalidatePageData();
    }

    private void addAppsWithoutInvalidate(ArrayList<ApplicationInfo> list) {
        // we add it in place, in alphabetical order
        final int count = list.size();
        for (int i = 0; i < count; ++i) {
            final ApplicationInfo info = list.get(i);
            final int index = Collections.binarySearch(mApps, info, LauncherModel.APP_NAME_COMPARATOR);
            if (index < 0) {
                mApps.add(-(index + 1), info);
            }
        }
        mFilteredApps = rebuildFilteredApps(mApps);
    }
    @Override
    public void addApps(ArrayList<ApplicationInfo> list) {
        addAppsWithoutInvalidate(list);
        invalidatePageData();
    }

    private void removeAppsWithoutInvalidate(ArrayList<ApplicationInfo> list) {
        // loop through all the apps and remove apps that have the same component
        final int length = list.size();
        for (int i = 0; i < length; ++i) {
            final ApplicationInfo info = list.get(i);
            int removeIndex = findAppByComponent(mApps, info);
            if (removeIndex > -1) {
                mApps.remove(removeIndex);
                mPageViewIconCache.removeOutline(info);
            }
        }
        mFilteredApps = rebuildFilteredApps(mApps);
    }
    @Override
    public void removeApps(ArrayList<ApplicationInfo> list) {
        removeAppsWithoutInvalidate(list);
        invalidatePageData();
    }

    @Override
    public void updateApps(ArrayList<ApplicationInfo> list) {
        removeAppsWithoutInvalidate(list);
        addAppsWithoutInvalidate(list);
        invalidatePageData();
    }

    private int findAppByComponent(ArrayList<ApplicationInfo> list, ApplicationInfo item) {
        ComponentName removeComponent = item.intent.getComponent();
        final int length = list.size();
        for (int i = 0; i < length; ++i) {
            ApplicationInfo info = list.get(i);
            if (info.intent.getComponent().equals(removeComponent)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void dumpState() {
        ApplicationInfo.dumpApplicationInfoList(TAG, "mApps", mApps);
    }

    @Override
    public void surrender() {
        // do nothing?
    }

    @Override
    public void syncPages() {
        // ensure that we have the right number of pages
        int numPages = (int) Math.ceil((float) mFilteredApps.size() / (mCellCountX * mCellCountY));
        int curNumPages = getChildCount();
        // remove any extra pages after the "last" page
        int extraPageDiff = curNumPages - numPages;
        for (int i = 0; i < extraPageDiff; ++i) {
            removeViewAt(numPages);
        }
        // add any necessary pages
        for (int i = curNumPages; i < numPages; ++i) {
            PagedViewCellLayout layout = new PagedViewCellLayout(getContext());
            layout.setCellCount(mCellCountX, mCellCountY);
            addView(layout);
        }

        // bound the current page
        setCurrentPage(Math.max(0, Math.min(numPages - 1, getCurrentPage())));
    }

    @Override
    public void syncPageItems(int page) {
        // ensure that we have the right number of items on the pages
        final int cellsPerPage = mCellCountX * mCellCountY;
        final int startIndex = page * cellsPerPage;
        final int endIndex = Math.min(startIndex + cellsPerPage, mFilteredApps.size());
        PagedViewCellLayout layout = (PagedViewCellLayout) getChildAt(page);

        final int curNumPageItems = layout.getChildCount();
        final int numPageItems = endIndex - startIndex;

        // remove any extra items
        int extraPageItemsDiff = curNumPageItems - numPageItems;
        for (int i = 0; i < extraPageItemsDiff; ++i) {
            layout.removeViewAt(numPageItems);
        }
        // add any necessary items
        for (int i = curNumPageItems; i < numPageItems; ++i) {
            TextView text = (TextView) mInflater.inflate(R.layout.all_apps_paged_view_application, layout, false);
            text.setOnClickListener(this);
            text.setOnLongClickListener(this);

            layout.addViewToCellLayout(text, -1, i,
                new PagedViewCellLayout.LayoutParams(0, 0, 1, 1));
        }

        // actually reapply to the existing text views
        for (int i = startIndex; i < endIndex; ++i) {
            final int index = i - startIndex;
            final ApplicationInfo info = mFilteredApps.get(i);
            PagedViewIcon icon = (PagedViewIcon) layout.getChildAt(index);
            icon.applyFromApplicationInfo(info, mPageViewIconCache);

            PagedViewCellLayout.LayoutParams params = 
                (PagedViewCellLayout.LayoutParams) icon.getLayoutParams();
            params.cellX = index % mCellCountX;
            params.cellY = index / mCellCountX;
        }
    }
}
