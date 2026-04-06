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

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.Date;
import java.util.Iterator;

/**
 * default data point implementation.
 * This stores the x and y values.
 *
 * @author jjoe64
 */
public class DataPoint implements DataPointInterface, Serializable {
    class RangedIterator implements Iterator<DataPointInterface> {
        DataPointInterface mStart;
        DataPointInterface mNext;
        double mStartValue;
        double mEndValue;

        RangedIterator(DataPointInterface dp, double start, double end) {
            // TODO: return with one point extra before and after to be equal to original
            mStartValue = start;
            mEndValue = end;
            DataPointInterface temp = dp;
            while (temp.hasPrevious() && temp.getX() >= start) {
                temp = temp.getPrevious();
            }

            // Set current the first point or the first point smaller than start
            mNext = mStart = temp;
        }

        @Override
        public boolean hasNext() {
            boolean toReturn;
            if (mNext != null && mNext.hasPrevious()) {
                toReturn = mNext.getPrevious().getX() <= mEndValue;
            } else {
                toReturn = mNext != null;
            }
            return toReturn;
        }

        @Override
        public DataPointInterface next() {
            if (hasNext()) {
                DataPointInterface temp = mNext;
                mNext = mNext.getNext();
                return temp;
            } else {
                return null;
            }
        }
    }

    private static final long serialVersionUID = 1428263322645L;
    private double x;
    private double y;
    private DataPointInterface next = null;
    private DataPointInterface previous = null;
    private Series mSeries;

    public DataPoint(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public DataPoint(Date x, double y) {
        this.x = x.getTime();
        this.y = y;
    }

    @NonNull
    @Override
    public DataPointInterface clone() {
        DataPoint toReturn = new DataPoint(this.x, this.y);
        toReturn.next = this.next;
        toReturn.previous = this.previous;
        toReturn.mSeries = this.mSeries;
        return toReturn;
    }


    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y;
    }

    @Override
    public void setX(double x) {
        this.x = x;
    }

    @Override
    public void setY(double y) {
        this.y = y;
    }

    @Override
    public DataPointInterface getNext() {
        return next;
    }

    @Override
    public DataPointInterface getPrevious() {
        return previous;
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public boolean hasPrevious() {
        return previous != null;
    }

    @Override
    public void setNext(DataPointInterface dataPoint) {
        dataPoint.setSeries(mSeries);
        next = dataPoint;
        if (next.getPrevious() == null || !next.getPrevious().equals(this)) next.setPrevious(this);
    }

    @Override
    public void setPrevious(DataPointInterface dataPoint) {
        dataPoint.setSeries(mSeries);
        previous = dataPoint;
        if (previous.getNext() == null || !previous.getNext().equals(this)) previous.setNext(this);
    }

    // TODO: think about whether this can lead to an infinite loop
    @Override
    public void insertAfter(DataPointInterface dataPoint) {
        if (next != null) {
            dataPoint.insertAfter(next);
        }
        next = dataPoint;
        next.setSeries(mSeries);
    }

    @Override
    public void insertBefore(DataPointInterface dataPoint) {
        if (previous != null) {
            dataPoint.insertBefore(previous);
        }
        previous = dataPoint;
        previous.setSeries(mSeries);
    }

    @Override
    public void remove() {
        if (previous != null && next != null) {
            next.setPrevious(previous);
        } else if (previous != null) {
            previous.setNext(null);
        } else if (next != null) {
            next.setPrevious(null);
        }
    }

    @Override
    public Iterator<DataPointInterface> rangedIterator(double start, double end) {
        return new RangedIterator(this, start, end);
    }

    @Override
    public void setSeries(Series series) {
        if (series != null && previous != null && !previous.getSeries().equals(series)) {
            previous.setSeries(series);
        }
        if (series != null && next != null && !next.getSeries().equals(series)) {
            next.setSeries(series);
        }
        mSeries = series;
    }

    @Override
    public Series getSeries() {
        return mSeries;
    }

    @Override
    public int size() {
        int toReturn = 1;
        DataPointInterface current = this;
        while (current.hasNext()) {
            current = current.getNext();
            toReturn++;
        }
        current = this;
        while (current.hasPrevious()) {
            current = current.getPrevious();
            toReturn++;
        }

        return toReturn;
    }

    @Override
    public String toString() {
        return "[" + x + "/" + y + "]";
    }
}
