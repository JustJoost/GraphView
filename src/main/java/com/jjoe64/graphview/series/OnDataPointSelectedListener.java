package com.jjoe64.graphview.series;

/**
 * Listener for the datapoint selected event which gets triggered when
 * a new datapoint is selected
 *
 * @author JustJoost
 */
public interface OnDataPointSelectedListener {
    /**
     * gets called when the user selects a datapoint.
     *
     * @param series the corresponding series
     * @param dataPoint the data point that was selected
     */
    void onSelection(Series series, DataPointInterface dataPoint);
}
