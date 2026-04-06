package com.jjoe64.graphview;

import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
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
    }

    private static final class EditParameters {
        public float controlBoxHeight = 200.f;
        public float controlBoxWidth = 200.f;
        public float arrowSize;
        public int controlsColor = Color.RED;
    }

    public enum CoordinatesDisplayType {
        LEGEND, AXIS_LABELS
    }

    public enum State {
        IDLE, SELECT, EDIT, DRAG
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
    protected DataPointInterface mPointBeingEdited = null;
    private final PointF mPointBeingEditedCoords = new PointF();
    private State mState = State.IDLE;
    private PointF editDelta = new PointF();
    private float mControlsCenterX;
    private float mControlsCenterY;
    private final EditParameters mEditParameters = new EditParameters();

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
        mEditParameters.arrowSize = (int) (mStyles.textSize / 2);

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
            mState = State.SELECT;
        } else {
            DataPointInterface dpTapped = mGraphView.findDataPoint(mPosX, mPosY, true);
            // Point found can differ from the one that was selected before, because new data point
            // was selected based on x AND y, as opposed to just x. In this case, it was not a 'second'
            // tap, do not start editing
            if (dpTapped != null && dpTapped.equals(mCurrentSelection.get((BaseSeries<DataPointInterface>) dpTapped.getSeries()))) {
                mPointBeingEdited = dpTapped;
                mPointBeingEditedCoords.set(mGraphView.getDataPointXInView(dpTapped, dpTapped.getSeries(), false),
                        mGraphView.getDataPointYInView(dpTapped, dpTapped.getSeries(), false));
                mState = State.EDIT;
                updateControlsCenter(mEditParameters.arrowSize);
            } else {
                mState = State.IDLE;
            }
        }
    }

    void editOnDown() {
        float deltaX = mPosX - mControlsCenterX;
        float deltaY = mPosY - mControlsCenterY;

        if (Math.abs(deltaX) > 2.f / 3.f * mEditParameters.controlBoxWidth || Math.abs(deltaY) > 2.f / 3.f * mEditParameters.controlBoxHeight) {
            idleOrSelectOnDown();
            return;
        }

        float col = (int) ((deltaX + 2.f / 3.f * mEditParameters.controlBoxWidth) / (4.f / 9.f * mEditParameters.controlBoxWidth));
        float row = (int) ((deltaY + 2.f / 3.f * mEditParameters.controlBoxHeight) / (4.f / 9.f * mEditParameters.controlBoxHeight));

        if (row == 1 && col == 1) {
            mState = State.DRAG;
        }
    }

    public void onMove(MotionEvent e) {
        mPosX = Math.max(e.getX(), mGraphView.getGraphContentLeft());
        mPosX = Math.min(mPosX, mGraphView.getGraphContentLeft() + mGraphView.getGraphContentWidth());
        mPosY = e.getY();
        switch (mState) {
            case SELECT:
                Map<BaseSeries, DataPointInterface> newCurrentSelection = findCurrentDataPointsAtX();
                if (!newCurrentSelection.equals(mCurrentSelection)) {
                    mCurrentSelection.clear();
                    mCurrentSelection.putAll(newCurrentSelection);
                }
                break;
            case DRAG:
                dragOnMove();
                break;
        }
        mGraphView.invalidate();
    }

    void dragOnMove() {
        // Limit drag arrows, y to graph area and x to between previous and next point
        DataPointInterface prev = mPointBeingEdited.getPrevious();
        DataPointInterface next = mPointBeingEdited.getNext();
        float xIncrement = (float) mPointBeingEdited.getSeries().getEditIncrementX() * mGraphView.getViewport().getDataToViewFactorX();
        float minX;
        boolean outOfBounds = false;
        if (prev != null) {
            minX = mGraphView.getDataPointXInView(prev, mPointBeingEdited.getSeries(), false) +
                    xIncrement;
        } else {
            minX = mGraphView.getGraphContentLeft();
        }
        float maxX;
        if (next != null) {
            maxX = mGraphView.getDataPointXInView(next, mPointBeingEdited.getSeries(), false) -
                    xIncrement;
        } else {
            maxX = mGraphView.getGraphContentLeft() + mGraphView.getGraphContentWidth();
        }
        if (mPosX < minX && mPosX > 0f) {
            editDelta.x = minX - mPointBeingEditedCoords.x;
        } else if (mPosX <= 0f) {
            outOfBounds = true;
        } else if (mPosX > maxX && mPosX < mGraphView.getWidth()) {
            editDelta.x = maxX - mPointBeingEditedCoords.x;
        } else if (mPosX >= mGraphView.getWidth()) {
            outOfBounds = true;
        } else {
            editDelta.x = mPosX - mPointBeingEditedCoords.x;
        }
        if (mPosY > mGraphView.getGraphContentHeight() + mStyles.padding && mPosY < mGraphView.getHeight()) {
            editDelta.y = mGraphView.getGraphContentHeight() + mStyles.padding - mPointBeingEditedCoords.y;
        } else if (mPosY >= mGraphView.getHeight()) {
            outOfBounds = true;
        } else if (mPosY < mStyles.padding && mPosY > 0f) {
            editDelta.y = mStyles.padding - mPointBeingEditedCoords.y;
        } else if (mPosY <= 0f) {
            outOfBounds = true;
        } else {
            editDelta.y = mPosY - mPointBeingEditedCoords.y;
        }
        if (outOfBounds) {
            editDelta.x = 0f;
            editDelta.y = 0f;
        }

        // Pick x or y drag based on largest distance
        double shownArrowSize;
        double increment;
        if (Math.abs(editDelta.x) > Math.abs(editDelta.y)) {
            editDelta.y = 0;
            shownArrowSize = Math.abs(editDelta.x);
            increment = xIncrement;
        } else {
            editDelta.x = 0;
            shownArrowSize = Math.abs(editDelta.y);
            increment = mPointBeingEdited.getSeries().getEditIncrementY() * mGraphView.getViewport().getDataToViewFactorY();
        }

        // Let arrows change in steps of 'increment'
        int nrOfIncrements = (int) (shownArrowSize / increment + 0.5);
        if (increment > 0) {
            // Set size to whole number of increments
            shownArrowSize = nrOfIncrements * increment;
        }

        if (editDelta.y == 0) {
            editDelta.x = Math.signum(editDelta.x) * (float) shownArrowSize;
        } else {
            editDelta.y = Math.signum(editDelta.y) * (float) shownArrowSize;
        }
    }

    public boolean onUp(MotionEvent e) {
        switch (mState) {
            case SELECT:
                mState = State.IDLE;
                break;
            case DRAG:
                if (editDelta.x != 0f) {
                    mPointBeingEdited.setX(mPointBeingEdited.getX() + editDelta.x);
                }
                mState = State.EDIT;
                break;
        }

        mGraphView.invalidate();
        return true;
    }
    // endregion

    // region Drawing and helper methods
    public void draw(Canvas canvas) {
        if (mState == State.SELECT) {
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

        if (mState == State.EDIT || mState == State.DRAG) {
            drawControls(canvas);
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

        mControlsCenterX = mGraphView.getDataPointXInView(mPointBeingEdited, null, false);
        mControlsCenterY = mGraphView.getDataPointYInView(mPointBeingEdited, null, false);

        // Ensure controls remain in graph area
        if (mControlsCenterX > graphLeft + graphWidth - mEditParameters.controlBoxWidth / 2 - padding) {
            mControlsCenterX = graphLeft + graphWidth - mEditParameters.controlBoxWidth / 2 - padding;
        } else if (mControlsCenterX < graphLeft + mEditParameters.controlBoxWidth / 2 + padding) {
            mControlsCenterX = graphLeft + mEditParameters.controlBoxWidth / 2 + padding;
        }
        if (mControlsCenterY > graphHeight - mEditParameters.controlBoxHeight / 2) {
            mControlsCenterY = graphHeight - mEditParameters.controlBoxHeight / 2;
        } else if (mControlsCenterY < mEditParameters.controlBoxHeight / 2 + padding) {
            mControlsCenterY = mEditParameters.controlBoxHeight / 2 + padding;
        }
    }

    protected void drawControls(Canvas canvas) {
        Paint arrowPaint = new Paint(mPaintLine);
        float arrowSize = mStyles.textSize / 2;
        float arrowDist = 75.f;
        float dpX = mGraphView.getDataPointXInView(mPointBeingEdited, null, false);
        float dpY = mGraphView.getDataPointYInView(mPointBeingEdited, null, false);
        float graphHeight = mGraphView.getGraphContentHeight();
        float graphLeft = mGraphView.getGraphContentLeft();

        updateControlsCenter(arrowSize);

//        float right = mControlsCenterX + mStyles.controlBoxWidth / 2;
//        float bottom = mControlsCenterY + mStyles.controlBoxHeight / 2;
//        float left = right - mStyles.controlBoxWidth;
//        float top = bottom - mStyles.controlBoxHeight;

        Path baseArrow = new Path();
        baseArrow.moveTo(-arrowSize, arrowSize);
        baseArrow.lineTo(0, 0);
        baseArrow.lineTo(arrowSize, arrowSize);
        baseArrow.close();

        Path arrowUp = rotAndTranslate(baseArrow, 0, dpX, dpY - arrowDist);
        Path arrowDown = rotAndTranslate(baseArrow, 180, dpX, dpY + arrowDist);
        Path arrowLeft = rotAndTranslate(baseArrow, 270, dpX - arrowDist, dpY);
        Path arrowRight = rotAndTranslate(baseArrow, 90, dpX + arrowDist, dpY);

        Path arrowDelta;
        float xLabel;
        float yLabel;
        String newValText;
        Paint.FontMetrics fm = mTextPaint.getFontMetrics();
        float textHeight = fm.descent - fm.ascent;
        if (editDelta.x != 0) {
            float angle = 90;
            if (editDelta.x < 0) {
                angle = 270;
            }
            float arrowY = arrowSize + arrowPaint.getStrokeWidth();
            arrowDelta = rotAndTranslate(baseArrow, angle, mPointBeingEditedCoords.x + editDelta.x,
                    arrowY);
            arrowDelta.moveTo(mPointBeingEditedCoords.x, arrowY);
            arrowDelta.lineTo(mPointBeingEditedCoords.x + editDelta.x, arrowY);
            xLabel = mPointBeingEditedCoords.x + editDelta.x + mStyles.textSize / 2;
            yLabel = arrowY + mStyles.textSize;
            newValText = mGraphView.getGridLabelRenderer().getLabelFormatter().formatLabel(
                    mPointBeingEdited.getX() + editDelta.x / mGraphView.getViewport().getDataToViewFactorX(), true);
            float textWidth = mTextPaint.measureText(newValText);
            if (editDelta.x > 0) {
                xLabel -= textWidth + 3 * arrowSize;
            } else {
                xLabel += arrowSize;
            }
        } else {
            float angle = 0;
            if (editDelta.y > 0) {
                angle = 180;
            }
            arrowDelta = rotAndTranslate(baseArrow, angle, graphLeft,
                    mPointBeingEditedCoords.y + editDelta.y);
            arrowDelta.moveTo(graphLeft, mPointBeingEditedCoords.y);
            arrowDelta.lineTo(graphLeft, mPointBeingEditedCoords.y + editDelta.y);
            xLabel = graphLeft + mStyles.textSize / 2;
            if (editDelta.y > 0) {
                yLabel = mPointBeingEditedCoords.y + editDelta.y - textHeight;
            } else {
                yLabel = mPointBeingEditedCoords.y + editDelta.y + textHeight + arrowSize;
            }
            newValText = mGraphView.getGridLabelRenderer().getLabelFormatter().formatLabel(
                    mPointBeingEdited.getY() - editDelta.y / mGraphView.getViewport().getDataToViewFactorY(), true);
        }

        canvas.drawPath(arrowUp, arrowPaint);
        canvas.drawPath(arrowDown, arrowPaint);
        canvas.drawPath(arrowLeft, arrowPaint);
        canvas.drawPath(arrowRight, arrowPaint);
        canvas.drawLine(dpX, dpY, dpX, canvas.getHeight(), mPaintLine);
        canvas.drawLine(0, dpY, dpX, dpY, mPaintLine);
        if (mState == State.DRAG) {
            arrowPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            arrowPaint.setColor(mEditParameters.controlsColor);
            mTextPaint.setColor(mEditParameters.controlsColor);
            canvas.drawPath(arrowDelta, arrowPaint);
            canvas.drawText(newValText, xLabel, yLabel, mTextPaint);
        }
    }

    private Path rotAndTranslate(Path path, float angle, float dx, float dy) {
        Matrix rotMat = new Matrix();
        rotMat.postRotate(angle);
        Matrix moveMat = new Matrix();
        moveMat.setTranslate(dx, dy);

        Path toReturn = new Path();
        toReturn.addPath(path, rotMat);
        toReturn.transform(moveMat);

        return toReturn;
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
