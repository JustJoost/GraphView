/**
 * GraphView
 * Copyright 2016 Jonas Gehring
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jjoe64.graphview.series;

import android.graphics.Canvas;

import com.jjoe64.graphview.GraphView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import kotlin.Pair;

/**
 * Basis implementation for series.
 * Used for series that are plotted on
 * a default x/y 2d viewport.
 * <p>
 * Extend this class to implement your own custom
 * graph type.
 * <p>
 * This implementation uses a internal Array to store
 * the data. If you want to implement a custom data provider
 * you may want to implement {@link com.jjoe64.graphview.series.Series}.
 *
 * @author jjoe64
 */
public abstract class BaseSeries<E extends DataPointInterface> implements Series<E> {
    // region Fields and Initialization
    /**
     * holds the first data point
     */
    private E mDataHead;
    private E mDataTail;

    /**
     * title for this series that can be displayed
     * in the legend.
     */
    private String mTitle;

    /**
     * base color for this series. will be used also in
     * the legend
     */
    private int mColor = 0xff0077cc;

    /**
     * cache for lowest y value
     */
    private double mLowestYCache = Double.NaN;

    /**
     * cache for highest y value
     */
    private double mHighestYCache = Double.NaN;

    private int mSizeCache = 0;

    /**
     * listener to handle tap events on a data point
     */
    protected OnDataPointTapListener mOnDataPointTapListener;

    /**
     * stores the graphviews where this series is used.
     * Can be more than one.
     */
    private final List<WeakReference<GraphView>> mGraphViews;
    private Boolean mIsCursorModeCache;
    protected boolean mEditable = false;
    protected double mEditIncrementX = 0f;
    protected double mEditIncrementY = 0f;

    private boolean mSortNewDatapoints = false;

    /**
     * creates series without data
     */
    public BaseSeries() {
        mGraphViews = new ArrayList<>();
    }

    /**
     * creates series with data
     *
     * @param data data points
     *             important: array has to be sorted from lowest x-value to the highest
     */
    public BaseSeries(E[] data) {
        mGraphViews = new ArrayList<>();
        addAll(data);
    }

    public BaseSeries(E[] data, boolean sort) {
        mGraphViews = new ArrayList<>();
        mSortNewDatapoints = sort;
        addAll(data);
    }
    // endregion

    // region Getters and setters
    public DataPointInterface getDataHead() {
        return mDataHead;
    }

    public DataPointInterface getDataTail() {
        return mDataTail;
    }

    /**
     * @return the lowest x value, or 0 if there is no data
     */
    public double getLowestValueX() {
        if (mDataHead == null) return 0d;
        return mDataHead.getX();
    }

    /**
     * @return the highest x value, or 0 if there is no data
     */
    public double getHighestValueX() {
        if (mDataTail == null) return 0d;
        return mDataTail.getX();
    }

    /**
     * @return the lowest y value, or 0 if there is no data
     */
    public double getLowestValueY() {
        if (mDataHead == null) return 0d;
        if (!Double.isNaN(mLowestYCache)) {
            return mLowestYCache;
        }
        double l = mDataHead.getY();
        DataPointInterface current = mDataHead;
        while (current.hasNext()) {
            double c = current.getY();
            if (l > c) {
                l = c;
            }
        }
        return mLowestYCache = l;
    }

    /**
     * @return the highest y value, or 0 if there is no data
     */
    public double getHighestValueY() {
        if (mDataHead == null) return 0d;
        if (!Double.isNaN(mHighestYCache)) {
            return mHighestYCache;
        }
        double h = mDataHead.getY();
        DataPointInterface current = mDataHead;
        while (current.hasNext()) {
            double c = current.getY();
            if (h < c) {
                h = c;
            }
        }
        return mHighestYCache = h;
    }

    /**
     * get the values for a given x range. if from and until are bigger or equal than
     * all the data, the original data is returned.
     * If it is only a part of the data, the range is returned plus one datapoint
     * before and after to get a nice scrolling.
     *
     * @param from  minimal x-value
     * @param until maximal x-value
     * @return data for the range +/- 1 datapoint
     */
    @Override
    public Iterator<E> getValues(final double from, final double until) {
        if (mDataHead == null) {
            // No data yet, 'fool' requester with empty iterator
            return new Iterator<E>() {
                @Override
                public boolean hasNext() {
                    return false;
                }

                @Override
                public E next() {
                    return null;
                }
            };
        } else {
            return (Iterator<E>) mDataHead.rangedIterator(from, until);
        }
    }

    /**
     * @return the title of the series
     */
    public String getTitle() {
        return mTitle;
    }

    /**
     * set the title of the series. This will be used in
     * the legend.
     *
     * @param mTitle title of the series
     */
    public void setTitle(String mTitle) {
        this.mTitle = mTitle;
    }

    /**
     * @return color of the series
     */
    public int getColor() {
        return mColor;
    }

    /**
     * set the color of the series. This will be used in
     * plotting (depends on the series implementation) and
     * is used in the legend.
     *
     * @param mColor color of the series
     */
    public void setColor(int mColor) {
        this.mColor = mColor;
    }

    /**
     * set a listener for tap on a data point.
     *
     * @param l listener
     */
    @Override
    public void setOnDataPointTapListener(OnDataPointTapListener l) {
        this.mOnDataPointTapListener = l;
    }

    @Override
    public double getEditIncrementX() {
        return mEditIncrementX;
    }

    @Override
    public void setEditIncrementX(double mEditIncrementX) {
        this.mEditIncrementX = mEditIncrementX;
    }

    @Override
    public double getEditIncrementY() {
        return mEditIncrementY;
    }

    @Override
    public void setEditIncrementY(double mEditIncrementY) {
        this.mEditIncrementY = mEditIncrementY;
    }
    // endregion

    /**
     * called by the tap detector in order to trigger
     * the on tap on datapoint event.
     *
     * @param x pixel
     * @param y pixel
     */
    @Override
    public void onTap(float x, float y, GraphView gv) {
        E p = findDataPoint(x, y, gv).getFirst();
        if (p != null) {
            if (mOnDataPointTapListener != null) mOnDataPointTapListener.onTap(this, p);
        }
    }

    /**
     * find the data point which is next to the
     * coordinates
     *
     * @param x pixel
     * @param y pixel
     * @return Triple of the data point or null if nothing was found, the index of the datapoint,
     * and the squared distance to the coordinates of this data point
     */
    public Pair<E, Float> findDataPoint(float x, float y, GraphView gv) {
        float shortestSqDist = Float.NaN;
        E closestDataPoint = null;
        E current = mDataHead;
        while (current != null) {
            float xDiff = gv.getPointXInView(current) - x;
            float yDiff = gv.getPointYInView(current) - y;

            float distance = xDiff * xDiff + yDiff * yDiff;
            if (closestDataPoint == null || distance < shortestSqDist) {
                shortestSqDist = distance;
                closestDataPoint = current;
            }
            current = (E) current.getNext();
        }
        if (closestDataPoint != null && shortestSqDist < 120 * 120) {
            return new Pair<>(closestDataPoint, shortestSqDist);
        } else {
            return new Pair<>(null, Float.NaN);
        }
    }

    public E findDataPointAtX(float x, GraphView gv) {
        float shortestDistance = Float.NaN;
        E shortest = null;
        E current = mDataHead;
        while (current != null) {
            float xDiff = gv.getPointXInView(current) - x;

            float distance = Math.abs(xDiff);
            if (shortest == null || distance < shortestDistance) {
                shortestDistance = distance;
                shortest = current;
            }
            current = (E) current.getNext();
        }
        if (shortest != null) {
            if (shortestDistance < 200) {
                return shortest;
            }
        }
        return null;
    }

    private boolean isCursorMode() {
        if (mIsCursorModeCache != null) {
            return mIsCursorModeCache;
        }
        for (WeakReference<GraphView> graphView : mGraphViews) {
            if (graphView != null && graphView.get() != null && graphView.get().isCursorMode()) {
                return mIsCursorModeCache = true;
            }
        }
        return mIsCursorModeCache = false;
    }

    /**
     * stores the reference of the used graph
     *
     * @param graphView graphview
     */
    @Override
    public void onGraphViewAttached(GraphView graphView) {
        mGraphViews.add(new WeakReference<>(graphView));
    }

    // region Data manipulation

    /**
     * clears the data of this series and sets new.
     * will redraw the graph
     *
     * @param data the values must be in the correct order!
     *             x-value has to be ASC. First the lowest x value and at least the highest x value.
     */
    public void resetData(E[] data) {
        mDataHead = null;
        addAll(data);

        // update graphview
        for (WeakReference<GraphView> gv : mGraphViews) {
            if (gv != null && gv.get() != null) {
                gv.get().onDataChanged(true, false);
            }
        }
    }

    /**
     *
     * @param dataPoint     values the values must be in the correct order!
     *                      x-value has to be ASC. First the lowest x value and at least the highest x value.
     * @param scrollToEnd   true => graphview will scroll to the end (maxX)
     * @param maxDataPoints if max data count is reached, the oldest data
     *                      value will be lost to avoid memory leaks
     * @param silent        set true to avoid rerender the graph
     */
    public void appendData(E dataPoint, boolean scrollToEnd, int maxDataPoints, boolean silent) {
        if (mDataHead != null) {
            if (dataPoint.getX() < mDataTail.getX() && !mSortNewDatapoints) {
                throw new IllegalArgumentException("new x-value must be greater then the last value. x-values has to be ordered in ASC.");
            } else if (dataPoint.getX() < mDataTail.getX()) {
                addSorted(dataPoint, (E) mDataTail.clone());
            } else {
                mDataTail.setNext(dataPoint);
                mDataTail = dataPoint;
                mSizeCache++;
            }
        } else {
            mDataHead = dataPoint;
            mDataHead.setSeries(this);
            mDataTail = mDataHead;
            mSizeCache++;
        }

        if (mSizeCache > maxDataPoints) {
            // we have to trim one data
            mDataHead = (E) mDataHead.getNext();
        }

        // update lowest/highest cache
        double dataPointY = dataPoint.getY();
        if (!Double.isNaN(mHighestYCache)) {
            if (dataPointY > mHighestYCache) {
                mHighestYCache = dataPointY;
            }
        }
        if (!Double.isNaN(mLowestYCache)) {
            if (dataPointY < mLowestYCache) {
                mLowestYCache = dataPointY;
            }
        }

        if (!silent) {
            // recalc the labels when it was the first data
            boolean keepLabels = mDataHead.size() != 1;

            // update linked graph views
            // update graphview
            for (WeakReference<GraphView> gv : mGraphViews) {
                if (gv != null && gv.get() != null) {
                    if (scrollToEnd) {
                        gv.get().getViewport().scrollToEnd();
                    } else {
                        gv.get().onDataChanged(keepLabels, scrollToEnd);
                    }
                }
            }
        }
    }

    /**
     *
     * @param dataPoint     values the values must be in the correct order!
     *                      x-value has to be ASC. First the lowest x value and at least the highest x value.
     * @param scrollToEnd   true => graphview will scroll to the end (maxX)
     * @param maxDataPoints if max data count is reached, the oldest data
     *                      value will be lost to avoid memory leaks
     */
    public void appendData(E dataPoint, boolean scrollToEnd, int maxDataPoints) {
        appendData(dataPoint, scrollToEnd, maxDataPoints, false);
    }

    private void addAll(E[] data) {
        E current = null;
        for (E dp : data) {
            if (Double.isNaN(mLowestYCache) || dp.getY() < mLowestYCache) {
                mLowestYCache = dp.getY();
            }
            if (Double.isNaN(mHighestYCache) || dp.getY() > mHighestYCache) {
                mHighestYCache = dp.getY();
            }

            if (dp.getX() < mDataTail.getX() && !mSortNewDatapoints) {
                throw new IllegalArgumentException("Data not sorted on x-value, automatic sort not enabled");
            } else if (mSortNewDatapoints) {
                addSorted(dp, current);
            } else if (current == null) {
                mDataHead = dp;
                mDataHead.setSeries(this);
                mDataTail = mDataHead;
            } else {
                current.setNext(dp);
                mDataTail = dp;
            }
            current = mDataTail;
            mSizeCache++;
        }
    }

    private void addSorted(E dataPointToAdd, E current) {
        while (current.hasPrevious()) {
            current = (E) current.getPrevious();
            if (current.getX() < dataPointToAdd.getX()) {
                break;
            }
        }
        current.insertAfter(dataPointToAdd);
        mSizeCache++;
    }

    /**
     * @return whether there are data points
     */
    @Override
    public boolean isEmpty() {
        return mDataHead == null;
    }

    /**
     * @return whether the series is editable
     */
    @Override
    public boolean isEditable() {
        return mEditable;
    }

    /**
     * set whether the series is editable
     *
     * @param editable
     */
    @Override
    public void setEditable(boolean editable) {
        mEditable = editable;
    }
    // endregion

    public abstract void drawSelection(GraphView mGraphView, Canvas canvas,
                                       boolean b, DataPointInterface value);

    public void clearCursorModeCache() {
        mIsCursorModeCache = null;
    }

    @Override
    public void clearReference(GraphView graphView) {
        // find and remove
        for (WeakReference<GraphView> view : mGraphViews) {
            if (view != null && view.get() != null && view.get() == graphView) {
                mGraphViews.remove(view);
                break;
            }
        }
    }
}
