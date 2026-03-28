package com.jjoe64.graphview;

import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Pair;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.widget.Toast;

import com.jjoe64.graphview.series.BaseSeries;
import com.jjoe64.graphview.series.DataPointInterface;
import com.jjoe64.graphview.series.Series;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by jonas on 22/02/2017.
 */

public class CursorMode {

    private float mControlsCenterX;
    private float mControlsCenterY;

    // region Initialization and variables
    private final static class Styles {
        public float textSize;
        public int spacing;
        public int padding;
        public int width;
        public int backgroundColor;
        public int margin;
        public int textColor;
        public CoordinatesDisplayType coordinatesDisplayType;
        public float controlBoxHeight = 200.f;
        public float controlBoxWidth = 200.f;
        public float arrowSize;
    }

    public enum CoordinatesDisplayType {
        LEGEND, AXIS_LABELS
    }

    public enum State {
        IDLE, SELECT, SELECT_EDIT_ARMED, EDIT
    }

    protected final Paint mPaintLine;
    protected final GraphView mGraphView;
    protected float mPosX;
    protected float mPosY;
    protected final Map<BaseSeries, DataPointInterface> mCurrentSelection;
    protected final Paint mRectPaint;
    protected final Paint mTextPaint;
    protected double mCurrentSelectionX;
    protected double mCurrentSelectionY;
    protected Styles mStyles;
    protected int cachedLegendWidth;
    protected Pair<DataPointInterface, Series> mPointBeingEdited = null;
    private State mState = State.IDLE;

    public CursorMode(GraphView graphView) {
        mStyles = new Styles();
        mGraphView = graphView;
        mPaintLine = new Paint();
        mPaintLine.setColor(Color.argb(128, 180, 180, 180));
        mPaintLine.setStrokeWidth(10f);
        mCurrentSelection = new HashMap<>();
        mRectPaint = new Paint();
        mTextPaint = new Paint();
        resetStyles();
    }

    /**
     * resets the styles to the defaults
     * and clears the legend width cache
     */
    public void resetStyles() {
        mStyles.textSize = mGraphView.getGridLabelRenderer().getTextSize();
        mStyles.spacing = (int) (mStyles.textSize / 5);
        mStyles.padding = (int) (mStyles.textSize / 2);
        mStyles.width = 0;
        mStyles.backgroundColor = Color.argb(180, 100, 100, 100);
        mStyles.margin = (int) (mStyles.textSize);
        mStyles.coordinatesDisplayType = CoordinatesDisplayType.LEGEND;
        mStyles.arrowSize = (int) (mStyles.textSize / 2);

        // get matching styles from theme
        TypedValue typedValue = new TypedValue();
        mGraphView.getContext().getTheme().resolveAttribute(android.R.attr.textAppearanceSmall, typedValue, true);

        int color1;

        try {
            TypedArray array = mGraphView.getContext().obtainStyledAttributes(typedValue.data, new int[]{android.R.attr.textColorPrimary});
            color1 = array.getColor(0, Color.BLACK);
            array.recycle();
        } catch (Exception e) {
            color1 = Color.BLACK;
        }

        mStyles.textColor = color1;

        cachedLegendWidth = 0;
    }
    // endregion

    // region Touch events
    public void onDown(MotionEvent e) {
        mPosX = Math.max(e.getX(), mGraphView.getGraphContentLeft());
        mPosX = Math.min(mPosX, mGraphView.getGraphContentLeft() + mGraphView.getGraphContentWidth());
        mPosY = e.getY();
        switch (mState) {
            case IDLE:
            case SELECT:
                idleOrSelectOnDown();
                break;
            case EDIT:
                editOnDown();
                break;
        }
        mGraphView.invalidate();
    }

    void idleOrSelectOnDown() {
        Map<BaseSeries, DataPointInterface> newCurrentSelection = findCurrentDataPointsAtX();
        if (!newCurrentSelection.equals(mCurrentSelection)) {
            mCurrentSelection.clear();
            mCurrentSelection.putAll(newCurrentSelection);
            stopEdit();
        } else {
            mState = State.SELECT_EDIT_ARMED;
        }
    }

    void editOnDown() {
        float deltaX = mPosX - mControlsCenterX;
        float deltaY = mPosY - mControlsCenterY;

        if (Math.abs(deltaX) > 2.f / 3.f * mStyles.controlBoxWidth || Math.abs(deltaY) > 2.f / 3.f * mStyles.controlBoxHeight) {
            idleOrSelectOnDown();
            return;
        }

        float col = (int) ((deltaX + 2.f / 3.f * mStyles.controlBoxWidth) / (4.f / 9.f * mStyles.controlBoxWidth));
        float row = (int) ((deltaY + 2.f / 3.f * mStyles.controlBoxHeight) / (4.f / 9.f * mStyles.controlBoxHeight));

        Toast.makeText(mGraphView.getContext(), "row: " + row + " col: " + col, Toast.LENGTH_SHORT).show();
    }

    public void onMove(MotionEvent e) {
        if (mState == State.SELECT || mState == State.SELECT_EDIT_ARMED) {
            mPosX = Math.max(e.getX(), mGraphView.getGraphContentLeft());
            mPosX = Math.min(mPosX, mGraphView.getGraphContentLeft() + mGraphView.getGraphContentWidth());
            mPosY = e.getY();
            Map<BaseSeries, DataPointInterface> newCurrentSelection = findCurrentDataPointsAtX();
            if (!newCurrentSelection.equals(mCurrentSelection)) {
                mCurrentSelection.clear();
                mCurrentSelection.putAll(newCurrentSelection);
                mPointBeingEdited = null;
                mState = State.SELECT;
            }
            mGraphView.invalidate();
        }
    }

    public boolean onUp(MotionEvent e) {
        switch (mState) {
            case SELECT_EDIT_ARMED:
                Pair<DataPointInterface, Series> dp = mGraphView.findDataPoint(mPosX, mPosY, true);
                // Point found can differ from the one that was selected before, because new data point
                // was selected based on x AND y, as opposed to just x. In this case, it was not a 'second'
                // tap, do not start editing
                if (dp != null && dp.first.equals(mCurrentSelection.get(dp.second))) {
                    mPointBeingEdited = dp;
                    mState = State.EDIT;
                    updateControlsCenter(mStyles.arrowSize);
                } else {
                    mState = State.IDLE;
                }
                break;
            case SELECT:
                mState = State.IDLE;
                break;
        }

        mGraphView.invalidate();
        return true;
    }

    // endregion

    // region Drawing and helper methods
    public void draw(Canvas canvas) {
        if (mState == State.SELECT || mState == State.SELECT_EDIT_ARMED) {
            canvas.drawLine(mPosX, 0, mPosX, canvas.getHeight(), mPaintLine);
        }

        // selection
        for (Map.Entry<BaseSeries, DataPointInterface> entry : mCurrentSelection.entrySet()) {
            entry.getKey().drawSelection(mGraphView, canvas, false, entry.getValue());
        }

        if (!mCurrentSelection.isEmpty()) {
            switch (mStyles.coordinatesDisplayType) {
                case LEGEND:
                    drawLegend(canvas);
                    break;
                case AXIS_LABELS:
                    drawLabels(canvas);
                    break;
            }
        }

        if (mPointBeingEdited != null) {
            drawControlBox(canvas);
        }
    }

    protected String getTextForSeries(Series s, DataPointInterface value) {
        StringBuilder txt = new StringBuilder();
        if (s.getTitle() != null) {
            txt.append(s.getTitle());
            txt.append(": ");
        }
        txt.append(mGraphView.getGridLabelRenderer().getLabelFormatter().formatLabel(value.getY(), false));
        return txt.toString();
    }

    protected void drawLegend(Canvas canvas) {
        mTextPaint.setTextSize(mStyles.textSize);
        mTextPaint.setColor(mStyles.textColor);

        int shapeSize = (int) (mStyles.textSize * 0.8d);

        // width
        int legendWidth = mStyles.width;
        if (legendWidth == 0) {
            // auto
            legendWidth = cachedLegendWidth;

            if (legendWidth == 0) {
                Rect textBounds = new Rect();
                for (Map.Entry<BaseSeries, DataPointInterface> entry : mCurrentSelection.entrySet()) {
                    String txt = getTextForSeries(entry.getKey(), entry.getValue());
                    mTextPaint.getTextBounds(txt, 0, txt.length(), textBounds);
                    legendWidth = Math.max(legendWidth, textBounds.width());
                }
                if (legendWidth == 0) legendWidth = 1;

                // add shape size
                legendWidth += shapeSize + mStyles.padding * 2 + mStyles.spacing;
                cachedLegendWidth = legendWidth;
            }
        }

        float legendPosX = mPosX - mStyles.margin - legendWidth;
        if (legendPosX < 0) {
            legendPosX = 0;
        }

        // rect
        float legendHeight = (mStyles.textSize + mStyles.spacing) * (mCurrentSelection.size() + 1) - mStyles.spacing;

        float legendPosY = mPosY - legendHeight - 4.5f * mStyles.textSize;
        if (legendPosY < 0) {
            legendPosY = 0;
        }

        float lLeft;
        float lTop;
        lLeft = legendPosX;
        lTop = legendPosY;

        float lRight = lLeft + legendWidth;
        float lBottom = lTop + legendHeight + 2 * mStyles.padding;
        mRectPaint.setColor(mStyles.backgroundColor);
        canvas.drawRoundRect(new RectF(lLeft, lTop, lRight, lBottom), 8, 8, mRectPaint);

        mTextPaint.setFakeBoldText(true);
        canvas.drawText(mGraphView.getGridLabelRenderer().getLabelFormatter().formatLabel(mCurrentSelectionX, true), lLeft + mStyles.padding, lTop + mStyles.padding / 2 + mStyles.textSize, mTextPaint);

        mTextPaint.setFakeBoldText(false);

        int i = 1;
        for (Map.Entry<BaseSeries, DataPointInterface> entry : mCurrentSelection.entrySet()) {
            mRectPaint.setColor(entry.getKey().getColor());
            canvas.drawRect(new RectF(lLeft + mStyles.padding, lTop + mStyles.padding + (i * (mStyles.textSize + mStyles.spacing)), lLeft + mStyles.padding + shapeSize, lTop + mStyles.padding + (i * (mStyles.textSize + mStyles.spacing)) + shapeSize), mRectPaint);
            canvas.drawText(getTextForSeries(entry.getKey(), entry.getValue()), lLeft + mStyles.padding + shapeSize + mStyles.spacing, lTop + mStyles.padding / 2 + mStyles.textSize + (i * (mStyles.textSize + mStyles.spacing)), mTextPaint);
            i++;
        }
    }

    protected void drawLabels(Canvas canvas) {
        mTextPaint.setTextSize(mStyles.textSize);
        float graphLeft = mGraphView.getGraphContentLeft();
        float graphHeight = mGraphView.getGraphContentHeight();
        float graphWidth = mGraphView.getGraphContentWidth();
        Paint.FontMetrics fm = mTextPaint.getFontMetrics();
        float textHeight = fm.descent - fm.ascent;

        for (Map.Entry<BaseSeries, DataPointInterface> entry : mCurrentSelection.entrySet()) {
            mTextPaint.setColor(entry.getKey().getColor());

            float xInView = mGraphView.getDataPointXInView(entry.getValue(), entry.getKey(), false);
            float yInView = mGraphView.getDataPointYInView(entry.getValue(), entry.getKey(), false);

            // Keep text inside graph at edges
            String text = mGraphView.getGridLabelRenderer().getLabelFormatter().formatLabel(mCurrentSelectionX, true);
            float textWidth = mTextPaint.measureText(text);
            if (textWidth + xInView > graphLeft + graphWidth) {
                xInView = graphLeft + graphWidth - textWidth + mStyles.padding;
            }
            if (yInView < textHeight) {
                yInView = textHeight;
            } else if (yInView > graphHeight) {
                yInView = graphHeight + mStyles.padding;
            }

            canvas.drawText(mGraphView.getGridLabelRenderer().getLabelFormatter().formatLabel(mCurrentSelectionX, true), xInView, graphHeight, mTextPaint);
            canvas.drawText(mGraphView.getGridLabelRenderer().getLabelFormatter().formatLabel(mCurrentSelectionY, false), graphLeft + mStyles.padding, yInView, mTextPaint);
        }
    }

    private void updateControlsCenter(float padding) {
        float graphLeft = mGraphView.getGraphContentLeft();
        float graphHeight = mGraphView.getGraphContentHeight();
        float graphWidth = mGraphView.getGraphContentWidth();

        mControlsCenterX = mGraphView.getDataPointXInView(mPointBeingEdited.first, null, false);
        mControlsCenterY = mGraphView.getDataPointYInView(mPointBeingEdited.first, null, false);

        // Ensure controls remain in graph area
        if (mControlsCenterX > graphLeft + graphWidth - mStyles.controlBoxWidth / 2 - padding) {
            mControlsCenterX = graphLeft + graphWidth - mStyles.controlBoxWidth / 2 - padding;
        } else if (mControlsCenterX < graphLeft + mStyles.controlBoxWidth / 2 + padding) {
            mControlsCenterX = graphLeft + mStyles.controlBoxWidth / 2 + padding;
        }
        if (mControlsCenterY > graphHeight - mStyles.controlBoxHeight / 2) {
            mControlsCenterY = graphHeight - mStyles.controlBoxHeight / 2;
        } else if (mControlsCenterY < mStyles.controlBoxHeight / 2 + padding) {
            mControlsCenterY = mStyles.controlBoxHeight / 2 + padding;
        }
    }

    protected void drawControlBox(Canvas canvas) {
        Paint arrowPaint = new Paint(mPaintLine);
        float arrowSize = mStyles.textSize / 2;

        updateControlsCenter(arrowSize);

        float right = mControlsCenterX + mStyles.controlBoxWidth / 2;
        float bottom = mControlsCenterY + mStyles.controlBoxHeight / 2;
        float left = right - mStyles.controlBoxWidth;
        float top = bottom - mStyles.controlBoxHeight;

        Path arrowUp = new Path();
        arrowUp.moveTo(mControlsCenterX - arrowSize, top);
        arrowUp.lineTo(mControlsCenterX, top - arrowSize);
        arrowUp.lineTo(mControlsCenterX + arrowSize, top);
        arrowUp.close();

        Path arrowDown = new Path();
        arrowDown.moveTo(mControlsCenterX - arrowSize, bottom);
        arrowDown.lineTo(mControlsCenterX, bottom + arrowSize);
        arrowDown.lineTo(mControlsCenterX + arrowSize, bottom);
        arrowDown.close();

        Path arrowLeft = new Path();
        arrowLeft.moveTo(left, mControlsCenterY - arrowSize);
        arrowLeft.lineTo(left - arrowSize, mControlsCenterY);
        arrowLeft.lineTo(left, mControlsCenterY + arrowSize);
        arrowLeft.close();

        Path arrowRight = new Path();
        arrowRight.moveTo(right, mControlsCenterY - arrowSize);
        arrowRight.lineTo(right + arrowSize, mControlsCenterY);
        arrowRight.lineTo(right, mControlsCenterY + arrowSize);
        arrowRight.close();

        canvas.drawPath(arrowUp, arrowPaint);
        canvas.drawPath(arrowDown, arrowPaint);
        canvas.drawPath(arrowLeft, arrowPaint);
        canvas.drawPath(arrowRight, arrowPaint);
    }
    // endregion

    private Map<BaseSeries, DataPointInterface> findCurrentDataPointsAtX() {
        Map<BaseSeries, DataPointInterface> toReturn = new HashMap<>();
        for (Series series : mGraphView.getSeries()) {
            if (series instanceof BaseSeries) {
                DataPointInterface p = ((BaseSeries) series).findDataPointAtX(mPosX);
                if (p != null) {
                    mCurrentSelectionX = p.getX();
                    mCurrentSelectionY = p.getY();
                    toReturn.put((BaseSeries) series, p);
                }
            }
        }

        return toReturn;
    }

    private void startEdit(Pair<DataPointInterface, Series> closest) {
        mPointBeingEdited = closest;
        mState = State.EDIT;
    }

    private void stopEdit() {
        mPointBeingEdited = null;
        mState = State.IDLE;
    }

    // region Style setters
    public void setTextSize(float t) {
        mStyles.textSize = t;
    }

    public void setTextColor(int color) {
        mStyles.textColor = color;
    }

    public void setBackgroundColor(int color) {
        mStyles.backgroundColor = color;
    }

    public void setSpacing(int s) {
        mStyles.spacing = s;
    }

    public void setPadding(int s) {
        mStyles.padding = s;
    }

    public void setMargin(int s) {
        mStyles.margin = s;
    }

    public void setWidth(int s) {
        mStyles.width = s;
    }

    public void setCoordinatesDisplayType(CoordinatesDisplayType type) {
        mStyles.coordinatesDisplayType = type;
    }

    // endregion
}
