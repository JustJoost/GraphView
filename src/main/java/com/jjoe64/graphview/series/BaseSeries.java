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
import com.josoft.collections.CircBuffer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

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
    private class IndexGuesser {
        double constantFac = 0d;
        double linearFac = 0d;

        IndexGuesser() {
            update();
        }

        int GuessIndex(double x) {
            if (mData != null) {
                int index = (int) (linearFac * (x + constantFac) + 0.5);
                if (index < 0) {
                    return 0;
                } else if (index > mData.size() - 1) {
                    return mData.size() - 1;
                }
                return index;
            } else {
                return 0;
            }
        }

        void update() {
            if (mData != null && mData.getFromOldest(0) != null) {
                double xMin = mData.getFromOldest(0).getX();
                double xMax = mData.getFromNewest(0).getX();

                if (xMin != xMax) {
                    constantFac = -xMin;
                    linearFac = (mData.size() - 1) / (xMax - xMin);
                }
            }
        }
    }

    private IndexGuesser indexGuesser = new IndexGuesser();

    CircBuffer<E> mData = new CircBuffer<>(0);

    /**
     * /**
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
        mData = new CircBuffer<>(data.length);
        addAll(data);
    }

    public BaseSeries(E[] data, boolean sort) {
        mGraphViews = new ArrayList<>();
        mSortNewDatapoints = sort;
        addAll(data);
    }
    // endregion

    // region Getters and setters

    /**
     * @return the lowest x value, or 0 if there is no data
     */
    public double getLowestValueX() {
        if (!mData.isEmpty()) {
            return mData.getFromOldest(0).getX();
        } else {
            return 0d;
        }
    }

    /**
     * @return the highest x value, or 0 if there is no data
     */
    public double getHighestValueX() {
        if (!mData.isEmpty()) {
            return mData.getFromNewest(0).getX();
        } else {
            return 0d;
        }
    }

    /**
     * @return the lowest y value, or 0 if there is no data
     */
    public double getLowestValueY() {
        if (mData.isEmpty()) return 0d;
        if (!Double.isNaN(mLowestYCache)) {
            return mLowestYCache;
        }
        ListIterator<E> iter = mData.iterator();
        double l = Double.MAX_VALUE;
        while (iter.hasNext()) {
            double c = iter.next().getY();
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
        if (mData.isEmpty()) return 0d;
        if (!Double.isNaN(mHighestYCache)) {
            return mHighestYCache;
        }
        ListIterator<E> iter = mData.iterator();
        double h = Double.MIN_VALUE;
        while (iter.hasNext()) {
            double c = iter.next().getY();
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
        if (mData.isEmpty()) {
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
            return mData.new CircBufferIterator(mData) {
                @Override
                public boolean hasNext() {
                    if (peek(2) != null && peek(2).getX() < from) {
                        while (super.hasNext()) {
                            if (super.next().getX() >= from) {
                                break;
                            }
                        }
                        if (super.hasNext()) {
                            for (int i = 0; i < 2; i++) {
                                super.previous();
                            }
                        }
                    }
                    if (peek(0) == null || peek(0).getX() <= until) {
                        return super.hasNext();
                    } else {
                        return false;
                    }
                }
            };
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
        CircBuffer<E>.CircBufferIterator it = findDataPoint(x, y, gv).getFirst();
        if (it != null) {
            E p = findDataPoint(x, y, gv).getFirst().previous();
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
    public Pair<CircBuffer<E>.CircBufferIterator, Float> findDataPoint(float x, float y, GraphView gv) {
        float shortestSqDist = Float.NaN;
        int indexClosest = -1;
        CircBuffer<E>.CircBufferIterator iter = mData.iterator();

        while (iter.hasNext()) {
            E current = iter.next();
            float xDiff = gv.getPointXInView(current) - x;
            float yDiff = gv.getPointYInView(current) - y;

            float distance = xDiff * xDiff + yDiff * yDiff;
            if (indexClosest == -1 || distance < shortestSqDist) {
                shortestSqDist = distance;
                indexClosest = iter.previousIndex();
            }
        }
        if (indexClosest != -1 && shortestSqDist < 120 * 120) {
            return new Pair<>(mData.iterator(indexClosest), shortestSqDist);
        } else {
            return new Pair<>(null, Float.NaN);
        }
    }

    public CircBuffer<E>.CircBufferIterator getNearestPointSmallerThanX(double x) {
        CircBuffer<E>.CircBufferIterator it = mData.iterator(indexGuesser.GuessIndex(x));

        if (!it.hasPrevious() && it.hasPrevious()) it.next();

        if (it.peek(0).getX() < x) {
            while (it.hasNext()) {
                if (it.next().getX() >= x) {
                    break;
                }
            }
            if (it.peek(0).getX() >= x) {
                it.previous();
            }
        } else if (it.peek(0).getX() >= x) {
            while (it.hasPrevious()) {
                if (it.previous().getX() < x) {
                    break;
                }
            }
        }

        return it;
    }

    public CircBuffer<E>.CircBufferIterator getPointClosestToX(float x, GraphView gv) {
        float shortestDistance = Float.MAX_VALUE;
        CircBuffer<E>.CircBufferIterator searchIter = getNearestPointSmallerThanX(gv.getViewXinPointUnits(x));
        int indexShortest = searchIter.previousIndex();

        for (int i = 0; i < 2; i++) {
            if (!searchIter.hasPrevious()) continue;
            float xDiff = gv.getPointXInView(searchIter.peek(0)) - x;

            float distance = Math.abs(xDiff);
            if (indexShortest == -1 || distance < shortestDistance) {
                shortestDistance = distance;
                indexShortest = searchIter.previousIndex();
            }
            if (searchIter.hasNext()) {
                searchIter.next();
            } else {
                break;
            }
        }

        if (indexShortest != -1) {
            if (shortestDistance < 200) {
                return mData.iterator(indexShortest);
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
        mData.clear();
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
        if (!mData.isEmpty()) {
            if (dataPoint.getX() < mData.getFromNewest(0).getX() && !mSortNewDatapoints) {
                throw new IllegalArgumentException("new x-value must be greater then the last value. x-values has to be ordered in ASC.");
            } else if (dataPoint.getX() < mData.getFromNewest(0).getX()) {
                addSorted(dataPoint);
            }
        } else {
            mData = new CircBuffer<>(maxDataPoints);
        }
        dataPoint.setSeries(this);
        mData.add(dataPoint);

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
            boolean keepLabels = mData.size() != 1;

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
        for (E dp : data) {
            if (Double.isNaN(mLowestYCache) || dp.getY() < mLowestYCache) {
                mLowestYCache = dp.getY();
            }
            if (Double.isNaN(mHighestYCache) || dp.getY() > mHighestYCache) {
                mHighestYCache = dp.getY();
            }

            if (dp.getX() < mData.getFromNewest(0).getX() && !mSortNewDatapoints) {
                throw new IllegalArgumentException("Data not sorted on x-value, automatic sort not enabled");
            } else if (mSortNewDatapoints) {
                addSorted(dp);
            } else {
                dp.setSeries(this);
                mData.add(dp);
            }
        }
    }

    private void addSorted(E dataPointToAdd) {
        dataPointToAdd.setSeries(this);
        CircBuffer<E>.CircBufferIterator iter = mData.iterator(mData.size() - 1);
        while (iter.hasPrevious()) {
            E current = (E) iter.previous();
            if (current.getX() < dataPointToAdd.getX()) {
                break;
            }
        }
        iter.add(dataPointToAdd);
    }

    /**
     * @return whether there are data points
     */
    @Override
    public boolean isEmpty() {
        return mData.isEmpty();
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
