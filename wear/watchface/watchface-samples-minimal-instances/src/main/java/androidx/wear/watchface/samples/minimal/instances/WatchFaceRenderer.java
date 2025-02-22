/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.wear.watchface.samples.minimal.instances;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.annotation.Px;
import androidx.annotation.UiThread;
import androidx.lifecycle.FlowLiveDataConversions;
import androidx.wear.watchface.CanvasType;
import androidx.wear.watchface.DrawMode;
import androidx.wear.watchface.ListenableCanvasRenderer;
import androidx.wear.watchface.RenderParameters;
import androidx.wear.watchface.WatchState;
import androidx.wear.watchface.style.CurrentUserStyleRepository;
import androidx.wear.watchface.style.UserStyle;
import androidx.wear.watchface.style.UserStyleSetting;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import kotlin.Unit;

import org.jetbrains.annotations.NotNull;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal rendered for the watch face, using canvas to render hours, minutes, and a blinking
 * separator.
 */
public class WatchFaceRenderer extends ListenableCanvasRenderer {

    private static final long UPDATE_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(1);
    private static final char[] DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};

    private final InstanceIdRenderer mInstanceIdRenderer;
    private final TimeRenderer mMinimalRenderer;
    private final TimeRenderer mSecondsRenderer;
    private final Paint mHighlightPaint;
    private final CurrentUserStyleRepository mCurrentUserStyleRepository;
    private final UserStyleSetting.Id mTimeStyleId;
    private final Resources mResources;

    private TimeRenderer mTimeRenderer;

    public WatchFaceRenderer(
            @NotNull SurfaceHolder surfaceHolder,
            @NotNull CurrentUserStyleRepository currentUserStyleRepository,
            @NotNull WatchState watchState,
            @NotNull Resources resources) {
        super(
                surfaceHolder,
                currentUserStyleRepository,
                watchState,
                CanvasType.HARDWARE,
                UPDATE_DELAY_MILLIS);
        mInstanceIdRenderer = new InstanceIdRenderer(watchState);
        mMinimalRenderer = new MinimalRenderer(watchState, mInstanceIdRenderer);
        mSecondsRenderer = new SecondsRenderer(watchState, mInstanceIdRenderer);
        mHighlightPaint = new Paint();
        mCurrentUserStyleRepository = currentUserStyleRepository;
        mTimeStyleId = new UserStyleSetting.Id(resources.getString(R.string.setting_id_time_style));
        mResources = resources;
    }

    @UiThread
    @NonNull
    @Override
    public ListenableFuture<Unit> initFuture() {
        // observeForever has to be called from the UI thread but the WatchFaceRenderer is called
        // from a background thread.
        FlowLiveDataConversions.asLiveData(mCurrentUserStyleRepository.getUserStyle())
                .observeForever(this::updateTimeStyle);
        mInstanceIdRenderer.registerObservers();
        return Futures.immediateFuture(Unit.INSTANCE);
    }

    @Override
    public void render(
            @NotNull Canvas canvas, @NotNull Rect rect, @NotNull ZonedDateTime zonedDateTime) {
        mTimeRenderer.render(canvas, rect, zonedDateTime, getRenderParameters());
    }

    @Override
    public void renderHighlightLayer(
            @NonNull Canvas canvas, @NonNull Rect bounds, @NonNull ZonedDateTime zonedDateTime) {
        RenderParameters.HighlightLayer highlightLayer = getRenderParameters().getHighlightLayer();
        canvas.drawColor(highlightLayer.getBackgroundTint());
        mHighlightPaint.setColor(highlightLayer.getHighlightTint());
        mHighlightPaint.setStrokeWidth(2f);
        canvas.drawCircle(
                bounds.centerX(), bounds.centerY(), bounds.width() / 2 - 2, mHighlightPaint);
    }

    private void updateTimeStyle(UserStyle userStyle) {
        String option = userStyle.get(mTimeStyleId).getId().toString();
        if (option.equals(mResources.getString(R.string.option_id_time_style_minimal))) {
            mTimeRenderer = mMinimalRenderer;
        } else if (option.equals(mResources.getString(R.string.option_id_time_style_seconds))) {
            mTimeRenderer = mSecondsRenderer;
        }
    }

    private interface TimeRenderer {
        void render(
                @NotNull Canvas canvas,
                @NotNull Rect rect,
                @NotNull ZonedDateTime zonedDateTime,
                RenderParameters renderParameters);
    }

    private static class InstanceIdRenderer implements TimeRenderer {
        private final WatchState mWatchState;
        private final Path mInstanceIdPath;
        private final RectF mInstanceIdPathRect;
        private final Paint mInstanceIdPaint;
        private final AtomicReference<String> mInstanceId;

        private InstanceIdRenderer(WatchState watchState) {
            mWatchState = watchState;
            mInstanceIdPath = new Path();
            mInstanceIdPathRect = new RectF();
            mInstanceIdPaint = new Paint();
            mInstanceIdPaint.setTextAlign(Align.CENTER);
            mInstanceIdPaint.setTextSize(20f);
            mInstanceIdPaint.setColor(Color.WHITE);
            mInstanceId =
                    new AtomicReference<>(
                            Objects.toString(watchState.getWatchFaceInstanceId().getValue()));
        }

        @UiThread
        public void registerObservers() {
            FlowLiveDataConversions.asLiveData(mWatchState.getWatchFaceInstanceId())
                    .observeForever(mInstanceId::set);
        }

        @Override
        public void render(
                @NonNull Canvas canvas,
                @NonNull Rect rect,
                @NonNull ZonedDateTime zonedDateTime,
                RenderParameters renderParameters) {
            canvas.save();
            canvas.scale(0.9f, 0.9f);
            canvas.translate(0.144f * rect.width() / 2, 0.1f * rect.height() / 2);
            mInstanceIdPath.reset();
            mInstanceIdPathRect.set(rect);
            mInstanceIdPath.addArc(mInstanceIdPathRect, -180, 180);
            canvas.drawTextOnPath(mInstanceId.get(), mInstanceIdPath, 0f, 0f, mInstanceIdPaint);
            canvas.restore();
        }
    }

    private static class MinimalRenderer implements TimeRenderer {
        private final WatchState mWatchState;
        private final InstanceIdRenderer mInstanceIdRenderer;
        private final Paint mPaint;
        private final char[] mTimeText = new char[5];

        private MinimalRenderer(WatchState watchState, InstanceIdRenderer instanceIdRenderer) {
            mWatchState = watchState;
            mInstanceIdRenderer = instanceIdRenderer;
            mPaint = new Paint();
            mPaint.setTextAlign(Align.CENTER);
            mPaint.setTextSize(64f);
        }

        @Override
        public void render(
                @NotNull Canvas canvas,
                @NotNull Rect rect,
                @NotNull ZonedDateTime zonedDateTime,
                RenderParameters renderParameters) {
            mPaint.setColor(Color.BLACK);
            canvas.drawRect(rect, mPaint);
            mPaint.setColor(Color.WHITE);
            int hour = zonedDateTime.getHour();
            if (hour != 12) {
                hour %= 12;
            }
            int minute = zonedDateTime.getMinute();
            int second = zonedDateTime.getSecond();
            mTimeText[0] = DIGITS[hour / 10];
            mTimeText[1] = DIGITS[hour % 10];
            mTimeText[2] = second % 2 == 0 ? ':' : ' ';
            mTimeText[3] = DIGITS[minute / 10];
            mTimeText[4] = DIGITS[minute % 10];
            canvas.drawText(
                    mTimeText,
                    0,
                    5,
                    rect.centerX(),
                    rect.centerY() - mWatchState.getChinHeight(),
                    mPaint);

            mInstanceIdRenderer.render(canvas, rect, zonedDateTime, renderParameters);
        }
    }

    private static class SecondsRenderer implements TimeRenderer {
        @Px public static final float SECONDS_TEXT_HEIGHT = 256f;
        @Px public static final float TIME_TEXT_ACTIVE_HEIGHT = 64f;
        @Px public static final float TIME_TEXT_AMBIENT_HEIGHT = 96f;
        @Px private static final int TEXT_PADDING = 12;

        private final WatchState mWatchState;
        private final InstanceIdRenderer mInstanceIdRenderer;
        private final Paint mPaint;
        private final char[] mTimeText = new char[] {'1', '0', ':', '0', '9'};
        private final char[] mSecondsText = new char[] {'3', '0'};
        @Px private final int mTimeActiveOffset;
        @Px private final int mTimeAmbientOffset;
        @Px private final int mSecondsActiveOffset;

        private SecondsRenderer(WatchState watchState, InstanceIdRenderer instanceIdRenderer) {
            mWatchState = watchState;
            mInstanceIdRenderer = instanceIdRenderer;
            mPaint = new Paint();
            mPaint.setTextAlign(Align.CENTER);

            // Compute location of text.
            Rect textBounds = new Rect();

            mPaint.setTextSize(TIME_TEXT_ACTIVE_HEIGHT);
            mPaint.getTextBounds(mTimeText, 0, mTimeText.length, textBounds);
            @Px int timeActiveHeight = textBounds.height();

            mPaint.setTextSize(TIME_TEXT_AMBIENT_HEIGHT);
            mPaint.getTextBounds(mTimeText, 0, mTimeText.length, textBounds);
            @Px int timeAmbientHeight = textBounds.height();

            mPaint.setTextSize(SECONDS_TEXT_HEIGHT);
            mPaint.getTextBounds(mSecondsText, 0, mSecondsText.length, textBounds);
            @Px int secondsHeight = textBounds.height();

            mTimeActiveOffset =
                    (timeActiveHeight + secondsHeight + TEXT_PADDING) / 2 - timeActiveHeight;
            mTimeAmbientOffset = timeAmbientHeight / 2 - timeAmbientHeight;
            mSecondsActiveOffset = mTimeActiveOffset - secondsHeight - TEXT_PADDING;
        }

        @Override
        public void render(
                @NotNull Canvas canvas,
                @NotNull Rect rect,
                @NotNull ZonedDateTime zonedDateTime,
                RenderParameters renderParameters) {
            boolean isActive = renderParameters.getDrawMode() != DrawMode.AMBIENT;
            int hour = zonedDateTime.getHour();
            if (hour != 12) {
                hour %= 12;
            }
            int minute = zonedDateTime.getMinute();
            int second = zonedDateTime.getSecond();

            if (isActive) {
                mPaint.setColor(Color.rgb(64 + 192 * second / 60, 0, 0));
            } else {
                mPaint.setColor(Color.BLACK);
            }
            canvas.drawRect(rect, mPaint);
            mPaint.setColor(Color.WHITE);
            mTimeText[0] = DIGITS[hour / 10];
            mTimeText[1] = DIGITS[hour % 10];
            mTimeText[2] = second % 2 == 0 ? ':' : ' ';
            mTimeText[3] = DIGITS[minute / 10];
            mTimeText[4] = DIGITS[minute % 10];
            mPaint.setTextSize(isActive ? TIME_TEXT_ACTIVE_HEIGHT : TIME_TEXT_AMBIENT_HEIGHT);
            @Px int timeOffset = isActive ? mTimeActiveOffset : mTimeAmbientOffset;
            canvas.drawText(
                    mTimeText,
                    0,
                    mTimeText.length,
                    rect.centerX(),
                    rect.centerY() - mWatchState.getChinHeight() - timeOffset,
                    mPaint);
            mPaint.setTextSize(SECONDS_TEXT_HEIGHT);
            if (isActive) {
                mSecondsText[0] = DIGITS[second / 10];
                mSecondsText[1] = DIGITS[second % 10];
                canvas.drawText(
                        mSecondsText,
                        0,
                        mSecondsText.length,
                        rect.centerX(),
                        rect.centerY() - mWatchState.getChinHeight() - mSecondsActiveOffset,
                        mPaint);
            }

            mInstanceIdRenderer.render(canvas, rect, zonedDateTime, renderParameters);
        }
    }
}
