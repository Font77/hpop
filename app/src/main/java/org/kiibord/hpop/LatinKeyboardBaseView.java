package org.kiibord.hpop;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Paint.Align;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import org.kiibord.hpop.Keyboard.Key;

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.WeakHashMap;

import static org.kiibord.hpop.aski_hksu.aski_dijit_kya;

public class LatinKeyboardBaseView extends View implements PointerTracker.UIProxy {
    private static final String TAG = "hpop_tag";
    private static final boolean DEBUG = false;
    public static final int NOT_A_TOUCH_COORDINATE = -1;
    public interface OnKeyboardActionListener {
        void onPress(int primaryCode); void onRelease(int primaryCode); void onKey(int primaryCode, int[] keyCodes, int x, int y);
        void onText(CharSequence text); void onCancel(); boolean swipeLeft(); boolean swipeRight(); boolean swipeDown(); boolean swipeUp();
    }
    private final int mKeyRepeatInterval;
    /* package */ static final int NOT_A_KEY = -1;
    private static final int NUMBER_HINT_VERTICAL_ADJUSTMENT_PIXEL = -1;
    // XML attribute
    private Typeface mKeyTextStyle = Typeface.DEFAULT;

    private float mKeyTextSize; private float mLabelTextSize; private float mLabelScale = 1.0f;
    private int mKeyTextColor; private int mKeyHintColor; private int mKeyCursorColor; private int mBackgroundAlpha;
    private boolean mInvertSymbols; private boolean mRecolorSymbols;
    private int mSymbolColorScheme = 0;private int mShadowColor;
    private float mShadowRadius; private Drawable mKeyBackground; private float mBackgroundDimAmount;
    private float mKeyHysteresisDistance; private float mVerticalCorrection; protected int mPreviewOffset;
    protected int mPreviewHeight; protected int mPopupLayout;

    private Keyboard mKeyboard; private Key[] mKeys; // Main keyboard
    private int mKeyboardVerticalGap; // TODO this attribute should be gotten from Keyboard. 
    // Key preview popup
    protected TextView mPreviewText;
    protected PopupWindow mPreviewPopup;
    protected int mPreviewTextSizeLarge;
    protected int[] mOffsetInWindow;
    protected int mOldPreviewKeyIndex = NOT_A_KEY;
    protected boolean mShowPreview = true;
    protected boolean mShowTouchPoints = true;
    protected int mPopupPreviewOffsetX; protected int mPopupPreviewOffsetY; protected int mWindowY; protected int mPopupPreviewDisplayedY;
    protected final int mDelayBeforePreview;
    protected final int mDelayBeforeSpacePreview;
    protected final int mDelayAfterPreview;

    // Popup mini keyboard
    protected PopupWindow mMiniKeyboardPopup;
    protected LatinKeyboardBaseView mMiniKeyboard;
    protected View mMiniKeyboardContainer;
    protected View mMiniKeyboardParent;
    protected boolean mMiniKeyboardVisible;
    protected final WeakHashMap<Key, Keyboard> mMiniKeyboardCacheMain = new WeakHashMap<Key, Keyboard>();
    protected final WeakHashMap<Key, Keyboard> mMiniKeyboardCacheShift = new WeakHashMap<Key, Keyboard>();
    protected final WeakHashMap<Key, Keyboard> mMiniKeyboardCacheCaps = new WeakHashMap<Key, Keyboard>();
    protected int mMiniKeyboardOriginX; protected int mMiniKeyboardOriginY; protected int[] mWindowOffset;
    protected final float mMiniKeyboardSlideAllowance; protected int mMiniKeyboardTrackerId; protected long mMiniKeyboardPopupTime;
    private OnKeyboardActionListener mKeyboardActionListener;
    private final ArrayList<PointerTracker> mPointerTrackers = new ArrayList<PointerTracker>();
    private boolean mIgnoreMove = false;
    private final PointerQueue mPointerQueue = new PointerQueue();
    private final boolean mHasDistinctMultitouch; private int mOldPointerCount = 1; 
    protected KeyDetector mKeyDetector = new ProximityKeyDetector();
    private GestureDetector mGestureDetector;
    private final SwipeTracker mSwipeTracker = new SwipeTracker(); private final int mSwipeThreshold;
    private final boolean mDisambiguateSwipe;
    private boolean mDrawPending; private final Rect mDirtyRect = new Rect(); private Bitmap mBuffer;
    private boolean mKeyboardChanged; private Key mInvalidatedKey;
    private Canvas mCanvas; private final Paint mPaint; private final Paint mPaintHint;
    private final Rect mPadding; private final Rect mClipRegion = new Rect(0, 0, 0, 0); private int mViewWidth;
    private final HashMap<Integer, Integer> mTextHeightCache = new HashMap<Integer, Integer>();
    private final float KEY_LABEL_VERTICAL_ADJUSTMENT_FACTOR = 1.55f; // pij
//    private final float KEY_LABEL_VERTICAL_ADJUSTMENT_FACTOR = 0.55f; // orijnql
    private final String KEY_LABEL_HEIGHT_REFERENCE_CHAR = "H";
    /* package */ static Method sSetRenderMode;
    private static int sPrevRenderMode = -1;
    private static final float[] INVERTING_MATRIX = {
        -1.f, 0, 0, 0, 255, // Red
        0, -1.f, 0, 0, 255, // Green
        0, 0, -1.f, 0, 255, // Blue
        0, 0, 0, 1.f, 0, // Alpha
    };
    private final ColorMatrixColorFilter mInvertingColorFilter = new ColorMatrixColorFilter(INVERTING_MATRIX);
    private final UIHandler mHandler = new UIHandler();
    class UIHandler extends Handler {
        private static final int MSG_POPUP_PREVIEW = 1; private static final int MSG_DISMISS_PREVIEW = 2;
        private static final int MSG_REPEAT_KEY = 3; private static final int MSG_LONGPRESS_KEY = 4;
        private boolean mInKeyRepeat;
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_POPUP_PREVIEW: showKey(msg.arg1, (PointerTracker)msg.obj);break;
                case MSG_DISMISS_PREVIEW: mPreviewPopup.dismiss();break;
                case MSG_REPEAT_KEY: {
                    final PointerTracker tracker = (PointerTracker)msg.obj;
                    tracker.repeatKey(msg.arg1);
                    startKeyRepeatTimer(mKeyRepeatInterval, msg.arg1, tracker);
                    break;
                }
                case MSG_LONGPRESS_KEY: {
                    final PointerTracker tracker = (PointerTracker)msg.obj;
                    openPopupIfRequired(msg.arg1, tracker);
                    break;
                }
            }
        }
        public void popupPreview(long delay, int keyIndex, PointerTracker tracker) {
            removeMessages(MSG_POPUP_PREVIEW);
            if (mPreviewPopup.isShowing() && mPreviewText.getVisibility() == VISIBLE) {
                showKey(keyIndex, tracker);
                sendMessageDelayed(obtainMessage(MSG_POPUP_PREVIEW, keyIndex, 0, tracker), delay);
            } else { }
        }
        public void cancelPopupPreview() { removeMessages(MSG_POPUP_PREVIEW); } 
        public void dismissPreview(long delay) {
            if (mPreviewPopup.isShowing()) {sendMessageDelayed(obtainMessage(MSG_DISMISS_PREVIEW), delay);}
        }
        public void cancelDismissPreview() { removeMessages(MSG_DISMISS_PREVIEW); } 
        public void startKeyRepeatTimer(long delay, int keyIndex, PointerTracker tracker) {
            mInKeyRepeat = true;
            sendMessageDelayed(obtainMessage(MSG_REPEAT_KEY, keyIndex, 0, tracker), delay);
        }
        public void cancelKeyRepeatTimer() { mInKeyRepeat = false; removeMessages(MSG_REPEAT_KEY); } 
        public boolean isInKeyRepeat() { return mInKeyRepeat; } 
        public void startLongPressTimer(long delay, int keyIndex, PointerTracker tracker) {
            removeMessages(MSG_LONGPRESS_KEY);
            sendMessageDelayed(obtainMessage(MSG_LONGPRESS_KEY, keyIndex, 0, tracker), delay);
        }
        public void cancelLongPressTimer() {removeMessages(MSG_LONGPRESS_KEY);} 
        public void cancelKeyTimers() {cancelKeyRepeatTimer();cancelLongPressTimer();} 
        public void cancelAllMessages() {cancelKeyTimers();cancelPopupPreview();cancelDismissPreview();}
    }
    static class PointerQueue {
        private LinkedList<PointerTracker> mQueue = new LinkedList<PointerTracker>();
        public void add(PointerTracker tracker) {mQueue.add(tracker);} 
        public int lastIndexOf(PointerTracker tracker) { LinkedList<PointerTracker> queue = mQueue;
            for (int index = queue.size() - 1; index >= 0; index--)
                {PointerTracker t = queue.get(index);if (t == tracker) return index;}
            return -1;
        }

        public void releaseAllPointersOlderThan(PointerTracker tracker, long eventTime) {
            LinkedList<PointerTracker> queue = mQueue;
            int oldestPos = 0;
            for (PointerTracker t = queue.get(oldestPos); t != tracker; t = queue.get(oldestPos)) {
                if (t.isModifier()) { oldestPos++; } else { 
                    t.onUpEvent(t.getLastX(), t.getLastY(), eventTime); t.setAlreadyProcessed(); queue.remove(oldestPos); }
                if (queue.isEmpty()) return;
            }
        }
        public void releaseAllPointersExcept(PointerTracker tracker, long eventTime) {
            for (PointerTracker t : mQueue) {
                if (t == tracker) continue;
                t.onUpEvent(t.getLastX(), t.getLastY(), eventTime);
                t.setAlreadyProcessed();
            }
            mQueue.clear();
            if (tracker != null) mQueue.add(tracker);
        }
        public void remove(PointerTracker tracker) {mQueue.remove(tracker);} 
        public boolean isInSlidingKeyInput() {
            for (final PointerTracker tracker : mQueue) { if (tracker.isInSlidingKeyInput()) return true; }
            return false;
        }
    }
    static { initCompatibility(); }
    static void initCompatibility() {
        try {
            sSetRenderMode = View.class.getMethod("setLayerType", int.class, Paint.class);
            Log.i(TAG, "setRenderMode is supported");
        } catch (SecurityException e) { Log.w(TAG, "unexpected SecurityException", e); }
        catch (NoSuchMethodException e) { Log.i(TAG, "ignoring render mode, not supported"); }
    }    
    private void setRenderModeIfPossible(int mode) {
        if (sSetRenderMode != null && mode != sPrevRenderMode) {
            try {
                sSetRenderMode.invoke(this, mode, null);
                sPrevRenderMode = mode;
                Log.i(TAG, "render mode set to " + LatinIME.sKeyboardSettings.renderMode);
            }
            catch (IllegalArgumentException e) { e.printStackTrace(); }
            catch (IllegalAccessException e) { e.printStackTrace(); }
            catch (InvocationTargetException e) { e.printStackTrace(); }
        }
    }
    
    public LatinKeyboardBaseView(Context context, AttributeSet attrs) { this(context, attrs, R.attr.keyboardViewStyle); }
    public LatinKeyboardBaseView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle);
        fontsovArride.setDefaultFont(context,"DEFAULT",R.font.roboto_heks_jk);
        fontsovArride.setDefaultFont(context,"DEFAULT_BOLD",R.font.roboto_heks_jk);
        if (!isInEditMode()) Log.i(TAG, "kriqtifg niyu lqtinkibordbesviyu " + this);
        setRenderModeIfPossible(LatinIME.sKeyboardSettings.renderMode);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.LatinKeyboardBaseView, defStyle, R.style.LatinKeyboardBaseView);
        int n = a.getIndexCount();for (int i = 0; i < n; i++) { int attr = a.getIndex(i);switch (attr) {
            case R.styleable.LatinKeyboardBaseView_keyBackground: mKeyBackground = a.getDrawable(attr);break;
            case R.styleable.LatinKeyboardBaseView_keyHysteresisDistance: mKeyHysteresisDistance = a.getDimensionPixelOffset(attr, 0);break;
            case R.styleable.LatinKeyboardBaseView_verticalCorrection: mVerticalCorrection = a.getDimensionPixelOffset(attr, 0);break;
            case R.styleable.LatinKeyboardBaseView_keyTextSize: mKeyTextSize = a.getDimensionPixelSize(attr, 18);break;
            case R.styleable.LatinKeyboardBaseView_keyTextColor: mKeyTextColor = a.getColor(attr, 0xFF0000FF);break; // 0xFF000000 klqr
            case R.styleable.LatinKeyboardBaseView_keyHintColor: mKeyHintColor = a.getColor(attr, 0xFFBBBBBB);break; // 0xFFBBBBBB klqr
            case R.styleable.LatinKeyboardBaseView_keyCursorColor: mKeyCursorColor = a.getColor(attr, 0xFF000000);break;
            case R.styleable.LatinKeyboardBaseView_invertSymbols: mInvertSymbols = a.getBoolean(attr, false);break;
            case R.styleable.LatinKeyboardBaseView_recolorSymbols: mRecolorSymbols = a.getBoolean(attr, false);break;
            case R.styleable.LatinKeyboardBaseView_labelTextSize: mLabelTextSize = a.getDimensionPixelSize(attr, 14);break;
            case R.styleable.LatinKeyboardBaseView_shadowColor: mShadowColor = a.getColor(attr, 0);break;
            case R.styleable.LatinKeyboardBaseView_shadowRadius: mShadowRadius = a.getFloat(attr, 0f);break;
            // TODO: Use Theme (android.R.styleable.Theme_backgroundDimAmount)
            case R.styleable.LatinKeyboardBaseView_backgroundDimAmount: mBackgroundDimAmount = a.getFloat(attr, 0.5f);break;
            case R.styleable.LatinKeyboardBaseView_backgroundAlpha: mBackgroundAlpha = a.getInteger(attr, 255);break;
            //case android.R.styleable.
            case R.styleable.LatinKeyboardBaseView_keyTextStyle:
                int textStyle = a.getInt(attr, 0);
                switch (textStyle) {
                    case 0: mKeyTextStyle = Typeface.DEFAULT;break;
                    case 1: mKeyTextStyle = Typeface.DEFAULT_BOLD;break;
                    default: mKeyTextStyle = Typeface.defaultFromStyle(textStyle);break;
                }
                break;
            case R.styleable.LatinKeyboardBaseView_symbolColorScheme: mSymbolColorScheme = a.getInt(attr, 0);break;
            }
        }
        final Resources res = getResources();
        mShowPreview = false;
        mDelayBeforePreview = res.getInteger(R.integer.config_delay_before_preview);
        mDelayBeforeSpacePreview = res.getInteger(R.integer.config_delay_before_space_preview);
        mDelayAfterPreview = res.getInteger(R.integer.config_delay_after_preview);

        mPopupLayout = 0;

        mPaint = new Paint(); mPaint.setAntiAlias(true); mPaint.setTextAlign(Align.CENTER);mPaint.setAlpha(255);
        mPaintHint = new Paint();mPaintHint.setAntiAlias(true);mPaintHint.setTextAlign(Align.LEFT);mPaintHint.setAlpha(255);
        mPaintHint.setTypeface(Typeface.DEFAULT_BOLD);

        mPadding = new Rect(0, 0, 0, 0);
        mKeyBackground.getPadding(mPadding);

        mSwipeThreshold = (int) (300 * res.getDisplayMetrics().density);
        // TODO: Refer frameworks/base/core/res/res/values/config.xml
        // TODO(klausw): turn off mDisambiguateSwipe if no swipe actions are set?
        mDisambiguateSwipe = res.getBoolean(R.bool.config_swipeDisambiguation);
        mMiniKeyboardSlideAllowance = res.getDimension(R.dimen.mini_keyboard_slide_allowance);

        GestureDetector.SimpleOnGestureListener listener = new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {
                final float absX = Math.abs(velocityX);final float absY = Math.abs(velocityY);
                float deltaX = me2.getX() - me1.getX();float deltaY = me2.getY() - me1.getY();
                mSwipeTracker.computeCurrentVelocity(1000);
                final float endingVelocityX = mSwipeTracker.getXVelocity();final float endingVelocityY = mSwipeTracker.getYVelocity();
                int travelX = getWidth() / 3;int travelY = getHeight() / 3;int travelMin = Math.min(travelX, travelY);
                if (velocityX > mSwipeThreshold && absY < absX && deltaX > travelMin) {
                    if (mDisambiguateSwipe && endingVelocityX >= velocityX / 4 && swipeRight()) return true;
                } else if (velocityX < -mSwipeThreshold && absY < absX && deltaX < -travelMin) {
                    if (mDisambiguateSwipe && endingVelocityX <= velocityX / 4 && swipeLeft()) return true;
                } else if (velocityY < -mSwipeThreshold && absX < absY && deltaY < -travelMin) {
                    if (mDisambiguateSwipe && endingVelocityY <= velocityY / 4 && swipeUp()) return true;
                } else if (velocityY > mSwipeThreshold && absX < absY / 2 && deltaY > travelMin) {
                    if (mDisambiguateSwipe && endingVelocityY >= velocityY / 4 && swipeDown()) return true;
                }
                return false;
            }
        };

        final boolean ignoreMultitouch = true;
        mGestureDetector = new GestureDetector(getContext(), listener, null, ignoreMultitouch);
        mGestureDetector.setIsLongpressEnabled(false);

        mHasDistinctMultitouch = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT);
        mKeyRepeatInterval = res.getInteger(R.integer.config_key_repeat_interval);
    }

    private boolean showHints7Bit() { return LatinIME.sKeyboardSettings.hintMode >= 1; } 
    private boolean showHintsAll() { return LatinIME.sKeyboardSettings.hintMode >= 2; } 
    public void setOnKeyboardActionListener(OnKeyboardActionListener listener) { mKeyboardActionListener = listener;
        for (PointerTracker tracker : mPointerTrackers) {tracker.setOnKeyboardActionListener(listener);}
    }
    protected OnKeyboardActionListener getOnKeyboardActionListener() { return mKeyboardActionListener; } 
    public void setKeyboard(Keyboard keyboard) {
        if (mKeyboard != null) { dismissKeyPreview(); }
        mHandler.cancelKeyTimers(); mHandler.cancelPopupPreview(); mKeyboard = keyboard;
        mKeys = mKeyDetector.setKeyboard(keyboard, 0, 0);
        mKeyboardVerticalGap = (int)getResources().getDimension(R.dimen.key_bottom_gap);
        for (PointerTracker tracker : mPointerTrackers) { tracker.setKeyboard(mKeys, mKeyHysteresisDistance); }
        mLabelScale = LatinIME.sKeyboardSettings.labelScalePref;
        requestLayout();
        mKeyboardChanged = true;
        invalidateAllKeys();
        computeProximityThreshold(keyboard);
        mMiniKeyboardCacheMain.clear(); mMiniKeyboardCacheShift.clear(); mMiniKeyboardCacheCaps.clear();
        setRenderModeIfPossible(LatinIME.sKeyboardSettings.renderMode);
        mIgnoreMove = true;
    }    
    public Keyboard getKeyboard() { return mKeyboard; }
    public boolean hasDistinctMultitouch() { return mHasDistinctMultitouch; }
    public boolean setShiftState(int shiftState) {
        if (mKeyboard != null) { if (mKeyboard.setShiftState(shiftState)) { invalidateAllKeys(); return true; } }
        return false;
    }
    public void setCtrlIndicator(boolean active) { if (mKeyboard != null) { invalidateKey(mKeyboard.setCtrlIndicator(active)); } }
    public void setAltIndicator(boolean active) { if (mKeyboard != null) { invalidateKey(mKeyboard.setAltIndicator(active)); } }
    public void setMetaIndicator(boolean active) { if (mKeyboard != null) { invalidateKey(mKeyboard.setMetaIndicator(active)); } } 
    public int getShiftState() { if (mKeyboard != null) { return mKeyboard.getShiftState(); } return Keyboard.SHIFT_OFF; }     
    public boolean isShiftCaps() { return getShiftState() != Keyboard.SHIFT_OFF; } 
    public boolean isShiftAll() {
        int state = getShiftState();
        if (LatinIME.sKeyboardSettings.shiftLockModifiers) { return state == Keyboard.SHIFT_ON || state == Keyboard.SHIFT_LOCKED; }
        else { return state == Keyboard.SHIFT_ON; }
    }
    public void setPreviewEnabled(boolean previewEnabled) { mShowPreview = previewEnabled; } 
    public boolean isPreviewEnabled() { return mShowPreview; } 
    private boolean isBlackSym() { return mSymbolColorScheme == 1; } 
    public void setPopupParent(View v) { mMiniKeyboardParent = v; } 
    public void setPopupOffset(int x, int y) {
        mPopupPreviewOffsetX = x; mPopupPreviewOffsetY = y; if (mPreviewPopup != null) mPreviewPopup.dismiss();
    }
    public void setProximityCorrectionEnabled(boolean enabled) { mKeyDetector.setProximityCorrectionEnabled(enabled); } 
    public boolean isProximityCorrectionEnabled() { return mKeyDetector.isProximityCorrectionEnabled(); } 
    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mKeyboard == null) {
            setMeasuredDimension(getPaddingLeft() + getPaddingRight(), getPaddingTop() + getPaddingBottom());
        } else {
            int width = mKeyboard.getMinWidth() + getPaddingLeft() + getPaddingRight();
            if (MeasureSpec.getSize(widthMeasureSpec) < width + 10) {
                int badWidth = MeasureSpec.getSize(widthMeasureSpec);
                if (badWidth != width) Log.i(TAG, "ignoring unexpected width=" + badWidth);
            }
            Log.i(TAG, "onmeasure vidth=" + width);
            setMeasuredDimension(width, mKeyboard.getHeight() + getPaddingTop() + getPaddingBottom());
        }
    }
    private void computeProximityThreshold(Keyboard keyboard) { if (keyboard == null) return;
        final Key[] keys = mKeys;if (keys == null) return;int length = keys.length;
        int dimensionSum = 0;
        for (int i = 0; i < length; i++) {
            Key key = keys[i];
            dimensionSum += Math.min(key.width, key.height + mKeyboardVerticalGap) + key.gap;
        }
        if (dimensionSum < 0 || length == 0) return;
        mKeyDetector.setProximityThreshold((int) (dimensionSum * 1.4f / length));
    }
    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w, h, oldw, oldh);Log.i(TAG, "onSizeChanged, w=" + w + ", h=" + h);
        mViewWidth = w;mBuffer = null;
    }
    @Override
    public void onDraw(Canvas canvas) { super.onDraw(canvas);mCanvas = canvas;
        if (mDrawPending || mBuffer == null || mKeyboardChanged) { onBufferDraw(canvas); }
        if (mBuffer != null) canvas.drawBitmap(mBuffer, 0, 0, null);
    }
    private void drawDeadKeyLabel(Canvas canvas, String hint, int x, float baseline, Paint paint) {
        char c = hint.charAt(0);String accent = DeadAccentSequence.getSpacing(c);
        canvas.drawText(Keyboard.DEAD_KEY_PLACEHOLDER_STRING, x, baseline, paint);
        canvas.drawText(accent, x, baseline, paint);
    }
    private int getLabelHeight(Paint paint, int labelSize) { Integer labelHeightValue = mTextHeightCache.get(labelSize);
        if (labelHeightValue != null) return labelHeightValue;
        else { Rect textBounds = new Rect();
            paint.getTextBounds(KEY_LABEL_HEIGHT_REFERENCE_CHAR, 0, 1, textBounds);
            int labelHeight = textBounds.height();mTextHeightCache.put(labelSize, labelHeight);return labelHeight;
        }
    }
    private void onBufferDraw(Canvas canvas) {
        if (/*mBuffer == null ||*/ mKeyboardChanged) {mKeyboard.setKeyboardWidth(mViewWidth);
        invalidateAllKeys(); mKeyboardChanged = false;}
        canvas.getClipBounds(mDirtyRect);if (mKeyboard == null) return;final Paint paint = mPaint;
        final Paint paintHint = mPaintHint;
        paintHint.setColor(mKeyHintColor);
        final Drawable keyBackground = mKeyBackground;final Rect clipRegion = mClipRegion;
        final Rect padding = mPadding;final int kbdPaddingLeft = getPaddingLeft();
        final int kbdPaddingTop = getPaddingTop();
        final Key[] keys = mKeys;final Key invalidKey = mInvalidatedKey;
        ColorFilter iconColorFilter = null;ColorFilter shadowColorFilter = null;
        if (mInvertSymbols) iconColorFilter = mInvertingColorFilter;
        else if (mRecolorSymbols) {
            iconColorFilter = new PorterDuffColorFilter(mKeyTextColor, PorterDuff.Mode.SRC_ATOP);
            shadowColorFilter = new PorterDuffColorFilter(mShadowColor, PorterDuff.Mode.SRC_ATOP);
        }
        boolean drawSingleKey = false;
        if (invalidKey != null && canvas.getClipBounds(clipRegion)) {
            if (invalidKey.x + kbdPaddingLeft - 1 <= clipRegion.left && invalidKey.y + kbdPaddingTop - 1 <= clipRegion.top &&
                invalidKey.x + invalidKey.width + kbdPaddingLeft + 1 >= clipRegion.right &&
                invalidKey.y + invalidKey.height + kbdPaddingTop + 1 >= clipRegion.bottom) {
                drawSingleKey = true;
            }
        }
        final int keyCount = keys.length; List<Integer> keyWidths = new ArrayList<Integer>(); List<Integer> keyHeights = new ArrayList<Integer>();
        for (int i = 0; i < keyCount; i++) { final Key key = keys[i]; keyWidths.add(key.width); keyHeights.add(key.height); }
        Collections.sort(keyWidths);Collections.sort(keyHeights);
        int medianKeyWidth = keyWidths.get(keyCount / 2);int medianKeyHeight = keyHeights.get(keyCount / 2);
        mKeyTextSize = Math.min(medianKeyHeight * 6 / 10, medianKeyWidth * 6 / 10);
        mLabelTextSize = mKeyTextSize * 3 / 4;
        int keysDrawn = 0;
        for (int i = 0; i < keyCount; i++) {
            final Key key = keys[i];
            if (drawSingleKey && invalidKey != key) continue;
            if (!mDirtyRect.intersects(
                key.x + kbdPaddingLeft, key.y + kbdPaddingTop,
                key.x + key.width + kbdPaddingLeft, key.y + key.height + kbdPaddingTop
            )) { continue; }
            keysDrawn++;
            paint.setColor(key.isCursor ? mKeyCursorColor : mKeyTextColor);
            int[] drawableState = key.getCurrentDrawableState(); keyBackground.setState(drawableState);
            String label = key.getCaseLabel(); float yscale = 1.0f;
            final Rect bounds = keyBackground.getBounds();
            if (key.width != bounds.right || key.height != bounds.bottom) {
                int minHeight = keyBackground.getMinimumHeight();
                if (minHeight > key.height) {
                    yscale = (float) key.height / minHeight;
                    keyBackground.setBounds(0, 0, key.width, minHeight);
                }
                else keyBackground.setBounds(0, 0, key.width, key.height);
            }
            canvas.translate(key.x + kbdPaddingLeft, key.y + kbdPaddingTop);
            if (yscale != 1.0f) { canvas.save();canvas.scale(1.0f, yscale); }
            if (mBackgroundAlpha != 255) keyBackground.setAlpha(mBackgroundAlpha);
            keyBackground.draw(canvas);
            if (yscale != 1.0f)  canvas.restore();
            boolean shouldDrawIcon = true;
            if (label != null) { final int labelSize;
                final int centerx = (key.width + padding.left - padding.right) / 2; // orijnl
                final int centerxhint = padding.left ; // (key.width + padding.left - padding.right) / 2; // orijnl
                final int centerx_alt_hint = padding.left ; // (key.width + padding.left - padding.right) / 2; // orijnl
                final int lqpht_qks = padding.left * 2; // hksubord
                if (label.length() > 1 && key.codes.length < 2)
                    {labelSize = (int)(mLabelTextSize * mLabelScale);paint.setTypeface(Typeface.DEFAULT);}
                else {labelSize = (int)(mKeyTextSize * mLabelScale);paint.setTypeface(mKeyTextStyle);}
                paint.setFakeBoldText(key.isCursor);
                paint.setTextSize(labelSize);
                final int labelHeight = getLabelHeight(paint, labelSize);
                final int top_y =  padding.top ; // hksubord
                final float beslain_lebql = top_y + labelHeight * KEY_LABEL_VERTICAL_ADJUSTMENT_FACTOR; // hksubord
                paint.setShadowLayer(mShadowRadius, 0, 0, mShadowColor);
                String hint = key.getHintLabel(showHints7Bit(), showHintsAll());
                if (!hint.equals("") && !(key.isShifted() && key.shiftLabel != null && hint.charAt(0) == key.shiftLabel.charAt(0))) {
                    // int hintTextSize = (int)(mKeyTextSize * 0.6 * mLabelScale); // orijnl
                    int hintTextSize = (int)(mKeyTextSize * mLabelScale);
                    paintHint.setTextSize(hintTextSize);
                    final int hintLabelHeight = getLabelHeight(paintHint, hintTextSize);
                    int x = key.width - padding.right; // orijnl
//                    int x = padding.left; // hksubord
                    x = (key.width + padding.left - padding.right) / 2;// hksubord
//                    int baseline = padding.top + hintLabelHeight * 12/10; // orijnl
                    x = centerx; x = lqpht_qks ;
//                    int baseline = key.height - hintLabelHeight * 12/10;//hksubord
                    float beslain = key.height - hintLabelHeight * 12/10;//hksubord
                    final float beslain_hint = beslain_lebql + hintLabelHeight * (hint.equals("") ? 14 : 26)/10;//hksubord
                    beslain = beslain_hint ; 
                    beslain = key.height - labelHeight;
                    Log.i(TAG, "centerxhint = " + centerxhint);
                    Log.i(TAG, "hint = " + hint);
                    Log.i(TAG, "beslain = " + beslain);
                    Log.i(TAG, "key.height = " + key.height);
                    Log.i(TAG, "mKeyTextSize = " + mKeyTextSize);
                    if (Character.getType(hint.charAt(0)) == Character.NON_SPACING_MARK) {
                        drawDeadKeyLabel(canvas, hint, centerxhint, beslain, paintHint);
                    }
                    else canvas.drawText(hint, centerxhint, beslain, paintHint);
                }
                String altHint = key.getAltHintLabel(showHints7Bit(), showHintsAll());
                if (!altHint.equals("")) {
//                    int hintTextSize = (int)(mKeyTextSize * 0.6 * mLabelScale); // orijnl
                    int hintTextSize = (int)(mKeyTextSize  * mLabelScale); // hksubord
                    paintHint.setTextSize(hintTextSize);
                    final int hintLabelHeight = getLabelHeight(paintHint, hintTextSize);
                    int x = key.width - padding.right;
                    x = centerx; x = lqpht_qks ;
//                    int baseline = padding.top + hintLabelHeight * (hint.equals("") ? 12 : 26)/10; // orijnl
                    float beslain = key.height - hintLabelHeight * (hint.equals("") ? 12 : 26)/10; // hksubord
                    final float beslain_hint = beslain_lebql + hintLabelHeight * (hint.equals("") ? 12 : 26)/10;//hksubord
                    beslain = beslain_hint;  beslain = key.height - labelHeight;
                    Log.i(TAG, "centerx_alt_hint = " + centerx_alt_hint);
                    Log.i(TAG, "altHint = " + altHint);
                    Log.i(TAG, "beslain = " + beslain);
                    Log.i(TAG, "key.height = " + key.height);
                    Log.i(TAG, "mKeyTextSize = " + mKeyTextSize);
                    if (Character.getType(altHint.charAt(0)) == Character.NON_SPACING_MARK) {
                        drawDeadKeyLabel(canvas, altHint, centerx_alt_hint, beslain, paintHint);
                    } else { canvas.drawText(altHint, centerx_alt_hint, beslain, paintHint); }
                }

                // Draw main key label
//                final int centerx = padding.left ;
//                 final int centerY = (key.height + padding.top - padding.bottom) / 2; // orijnl
//                final float baseline = centerY + labelHeight * KEY_LABEL_VERTICAL_ADJUSTMENT_FACTOR; // orijnl
                Log.i(TAG, "centerx = " + centerx);
                Log.i(TAG, "label = " + label);
                Log.i(TAG, "beslain_lebql = " + beslain_lebql);
                Log.i(TAG, "key.height = " + key.height);
                Log.i(TAG, "mKeyTextSize = " + mKeyTextSize);
                if(i>9 && i<20)
                    paint.setColor(Color.RED);
                if (key.isDeadKey()) drawDeadKeyLabel(canvas, label, centerx, beslain_lebql, paint);
                else canvas.drawText(label, centerx, beslain_lebql, paint);
                if (key.isCursor) {
                    paint.setShadowLayer(0, 0, 0, 0);
                    canvas.drawText(label, centerx+0.5f, beslain_lebql, paint); canvas.drawText(label, centerx-0.5f, beslain_lebql, paint);
                    canvas.drawText(label, centerx, beslain_lebql+0.5f, paint); canvas.drawText(label, centerx, beslain_lebql-0.5f, paint);
                }
                paint.setShadowLayer(0, 0, 0, 0);
                shouldDrawIcon = shouldDrawLabelAndIcon(key);
            }
            Drawable icon = key.icon;
            if (icon != null && shouldDrawIcon) {
                final int drawableWidth;final int drawableHeight;final int drawableX;final int drawableY;
                if (shouldDrawIconFully(key)) {
                    drawableWidth = key.width;drawableHeight = key.height;drawableX = 0;
                    drawableY = NUMBER_HINT_VERTICAL_ADJUSTMENT_PIXEL;
                } else {
                    drawableWidth = icon.getIntrinsicWidth();
                    drawableHeight = icon.getIntrinsicHeight();
                    drawableX = (key.width + padding.left - padding.right - drawableWidth) / 2;
                    drawableY = (key.height + padding.top - padding.bottom - drawableHeight) / 2;
                }
                canvas.translate(drawableX, drawableY);
                icon.setBounds(0, 0, drawableWidth, drawableHeight);
                if (iconColorFilter != null) {
                    if (shadowColorFilter != null && mShadowRadius > 0) {
                        BlurMaskFilter shadowBlur = new BlurMaskFilter(mShadowRadius, BlurMaskFilter.Blur.OUTER);
                        Paint blurPaint = new Paint();
                        blurPaint.setMaskFilter(shadowBlur);
                        Bitmap tmpIcon = Bitmap.createBitmap(key.width, key.height, Bitmap.Config.ARGB_8888);
                        Canvas tmpCanvas = new Canvas(tmpIcon);
                        icon.draw(tmpCanvas);
                        int[] offsets = new int[2];
                        Bitmap shadowBitmap = tmpIcon.extractAlpha(blurPaint, offsets);
                        Paint shadowPaint = new Paint();
                        shadowPaint.setColorFilter(shadowColorFilter);
                        canvas.drawBitmap(shadowBitmap, offsets[0], offsets[1], shadowPaint);
                    }
                    icon.setColorFilter(iconColorFilter);
                    icon.draw(canvas);
                    icon.setColorFilter(null);
                } else {
                    icon.draw(canvas);
                }
                canvas.translate(-drawableX, -drawableY);
            }
            canvas.translate(-key.x - kbdPaddingLeft, -key.y - kbdPaddingTop);
        }
        mInvalidatedKey = null;
        if (mMiniKeyboardVisible) {
            paint.setColor((int) (mBackgroundDimAmount * 0xFF) << 24);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }
        if (LatinIME.sKeyboardSettings.showTouchPos || DEBUG) {
            if (LatinIME.sKeyboardSettings.showTouchPos || mShowTouchPoints) {
                for (PointerTracker tracker : mPointerTrackers) {
                    int startX = tracker.getStartX();
                    int startY = tracker.getStartY();
                    int lastX = tracker.getLastX();
                    int lastY = tracker.getLastY();
                    paint.setAlpha(128);
                    paint.setColor(0xFF00FF00);
                    canvas.drawCircle(startX, startY, 3, paint);
                    canvas.drawLine(startX, startY, lastX, lastY, paint);
                    paint.setColor(0xFF0000FF);
                    canvas.drawCircle(lastX, lastY, 3, paint);
                    paint.setColor(0xFF00FF00);
                    canvas.drawCircle((startX + lastX) / 2, (startY + lastY) / 2, 2, paint);
                }
            }
        }
        mDrawPending = false;
        mDirtyRect.setEmpty();
    }
    private void dismissKeyPreview() {
        for (PointerTracker tracker : mPointerTrackers) tracker.updateKey(NOT_A_KEY);
        showPreview(NOT_A_KEY, null);
    }
    public void showPreview(int keyIndex, PointerTracker tracker) {
        int oldKeyIndex = mOldPreviewKeyIndex; mOldPreviewKeyIndex = keyIndex;
        final boolean isLanguageSwitchEnabled = (mKeyboard instanceof LatinKeyboard)
                && ((LatinKeyboard)mKeyboard).isLanguageSwitchEnabled();
        // We should re-draw popup preview when 1) we need to hide the preview, 2) we will show
        // the space key preview and 3) pointer moves off the space key to other letter key, we
        // should hide the preview of the previous key.
        final boolean hidePreviewOrShowSpaceKeyPreview = (tracker == null)
                || tracker.isSpaceKey(keyIndex) || tracker.isSpaceKey(oldKeyIndex);
        // If key changed and preview is on or the key is space (language switch is enabled)
        if (oldKeyIndex != keyIndex
                && (mShowPreview
                        || (hidePreviewOrShowSpaceKeyPreview && isLanguageSwitchEnabled))) {
            if (keyIndex == NOT_A_KEY) {
                mHandler.cancelPopupPreview();
                mHandler.dismissPreview(mDelayAfterPreview);
            } else if (tracker != null) {
                int delay = mShowPreview ? mDelayBeforePreview : mDelayBeforeSpacePreview;
                mHandler.popupPreview(delay, keyIndex, tracker);
            }
        }
    }

    private void showKey(final int keyIndex, PointerTracker tracker) {
        Key key = tracker.getKey(keyIndex);
        if (key == null) return; //Log.i(TAG, "showKey() for " + this); // Should not draw hint icon in key preview
        Drawable icon = key.icon;
        if (icon != null && !shouldDrawLabelAndIcon(key)) {
            mPreviewText.setCompoundDrawables(null, null, null, key.iconPreview != null ? key.iconPreview : icon);
            mPreviewText.setText(null);
        }
        else {
            mPreviewText.setCompoundDrawables(null, null, null, null);
            mPreviewText.setText(key.getCaseLabel());
            if (key.label.length() > 1 && key.codes.length < 2) {
                mPreviewText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mKeyTextSize);
                mPreviewText.setTypeface(Typeface.DEFAULT_BOLD);
//        mPreviewText.setTypeface(ResourcesCompat.getFont(this, R.font.roboto_heks_jk));
//        fontsovArride.setDefaultFont(mPreviewText,"DEFAULT",R.font.roboto_heks_jk);
            } else {
                mPreviewText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mPreviewTextSizeLarge);
                mPreviewText.setTypeface(mKeyTextStyle);
            }
        }
        mPreviewText.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int popupWidth = Math.max(mPreviewText.getMeasuredWidth(), key.width + mPreviewText.getPaddingLeft() + mPreviewText.getPaddingRight());
        final int popupHeight = mPreviewHeight;
        LayoutParams lp = mPreviewText.getLayoutParams();
        if (lp != null) { lp.width = popupWidth; lp.height = popupHeight; }
        int popupPreviewX = key.x - (popupWidth - key.width) / 2;
        int popupPreviewY = key.y - popupHeight + mPreviewOffset;
        mHandler.cancelDismissPreview();
        if (mOffsetInWindow == null) {
            mOffsetInWindow = new int[2];
            getLocationInWindow(mOffsetInWindow);
            mOffsetInWindow[0] += mPopupPreviewOffsetX; mOffsetInWindow[1] += mPopupPreviewOffsetY; // Offset may be zero
            int[] windowLocation = new int[2];
            getLocationOnScreen(windowLocation);
            mWindowY = windowLocation[1];
        }
        boolean hasPopup = (getLongPressKeyboard(key) != null);
        mPreviewText.setBackgroundDrawable(getResources().getDrawable(
            hasPopup ? R.drawable.keyboard_key_feedback_more_background : R.drawable.keyboard_key_feedback_background
        ));
        popupPreviewX += mOffsetInWindow[0]; popupPreviewY += mOffsetInWindow[1];
        if (popupPreviewY + mWindowY < 0) {
            if (key.x + key.width <= getWidth() / 2) { popupPreviewX += (int) (key.width * 2.5); }
            else { popupPreviewX -= (int) (key.width * 2.5); }
            popupPreviewY += popupHeight;
        }
        if (mPreviewPopup.isShowing()) { mPreviewPopup.update(popupPreviewX, popupPreviewY, popupWidth, popupHeight); }
        else {
            mPreviewPopup.setWidth(popupWidth); mPreviewPopup.setHeight(popupHeight);
            mPreviewPopup.showAtLocation(mMiniKeyboardParent, Gravity.NO_GRAVITY, popupPreviewX, popupPreviewY);
        }
        mPopupPreviewDisplayedY = popupPreviewY;
        mPreviewText.setVisibility(VISIBLE);
    }
    public void invalidateAllKeys() {mDirtyRect.union(0, 0, getWidth(), getHeight());mDrawPending = true;invalidate();}
    public void invalidateKey(Key key) {
        if (key == null) return;
        mInvalidatedKey = key;
        mDirtyRect.union(key.x + getPaddingLeft(), key.y + getPaddingTop(),
                key.x + key.width + getPaddingLeft(), key.y + key.height + getPaddingTop());
        invalidate(key.x + getPaddingLeft(), key.y + getPaddingTop(),
                key.x + key.width + getPaddingLeft(), key.y + key.height + getPaddingTop());
    }
    private boolean openPopupIfRequired(int keyIndex, PointerTracker tracker) {
        if (mPopupLayout == 0) { return false; }
        Key popupKey = tracker.getKey(keyIndex);
        if (popupKey == null) return false;
        if (tracker.isInSlidingKeyInput()) return false;
        boolean result = onLongPress(popupKey);
        if (result) { dismissKeyPreview(); mMiniKeyboardTrackerId = tracker.mPointerId;
            tracker.setAlreadyProcessed(); mPointerQueue.remove(tracker);
        }
        return result;
    }
    private void inflateMiniKeyboardContainer() {
        LayoutInflater inflater = (LayoutInflater)getContext().getSystemService( Context.LAYOUT_INFLATER_SERVICE);
        View container = inflater.inflate(mPopupLayout, null);
        mMiniKeyboard = (LatinKeyboardBaseView)container.findViewById(R.id.LatinKeyboardBaseView);
        mMiniKeyboard.setOnKeyboardActionListener(new OnKeyboardActionListener() {
            public void onKey(int primaryCode, int[] keyCodes, int x, int y) {
                mKeyboardActionListener.onKey(primaryCode, keyCodes, x, y);
                dismissPopupKeyboard();
            }
            public void onText(CharSequence text) { mKeyboardActionListener.onText(text); dismissPopupKeyboard(); }
            public void onCancel() { mKeyboardActionListener.onCancel(); dismissPopupKeyboard(); }
            public boolean swipeLeft() { return false; }
            public boolean swipeRight() { return false; }
            public boolean swipeUp() { return false; }
            public boolean swipeDown() { return false; }
            public void onPress(int primaryCode) { mKeyboardActionListener.onPress(primaryCode); }
            public void onRelease(int primaryCode) { mKeyboardActionListener.onRelease(primaryCode); }
        });
        mMiniKeyboard.mKeyDetector = new MiniKeyboardKeyDetector(mMiniKeyboardSlideAllowance);
        mMiniKeyboard.mGestureDetector = null; mMiniKeyboard.setPopupParent(this); mMiniKeyboardContainer = container;
    }

    private static boolean isOneRowKeys(List<Key> keys) {
        if (keys.size() == 0) return false;
        final int edgeFlags = keys.get(0).edgeFlags;
        return (edgeFlags & Keyboard.EDGE_TOP) != 0 && (edgeFlags & Keyboard.EDGE_BOTTOM) != 0;
    }
    private Keyboard getLongPressKeyboard(Key popupKey) {
        final WeakHashMap<Key, Keyboard> cache;
        if (popupKey.isDistinctCaps()) { cache = mMiniKeyboardCacheCaps; }
        else if (popupKey.isShifted()) { cache = mMiniKeyboardCacheShift; }
        else { cache = mMiniKeyboardCacheMain; }
        Keyboard kbd = cache.get(popupKey);
        if (kbd == null) {
            kbd = popupKey.getPopupKeyboard(getContext(), getPaddingLeft() + getPaddingRight());
            if (kbd != null) cache.put(popupKey, kbd);
        }
        return kbd;
    }
    protected boolean onLongPress(Key popupKey) {
        if (mPopupLayout == 0) return false; // No popups wanted
        Keyboard kbd = getLongPressKeyboard(popupKey);
        if (kbd == null) return false;
        if (mMiniKeyboardContainer == null) {inflateMiniKeyboardContainer();}
        if (mMiniKeyboard == null) return false;
        mMiniKeyboard.setKeyboard(kbd);
        mMiniKeyboardContainer.measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.AT_MOST));
        if (mWindowOffset == null) { mWindowOffset = new int[2]; getLocationInWindow(mWindowOffset); }
        final List<Key> miniKeys = mMiniKeyboard.getKeyboard().getKeys();
        final int miniKeyWidth = miniKeys.size() > 0 ? miniKeys.get(0).width : 0;

        int popupX = popupKey.x + mWindowOffset[0];
        popupX += getPaddingLeft();
        if (shouldAlignLeftmost(popupKey)) {
            popupX += popupKey.width - miniKeyWidth;  // adjustment for a) described above
            popupX -= mMiniKeyboardContainer.getPaddingLeft();
        } else {
            popupX += miniKeyWidth;  // adjustment for b) described above
            popupX -= mMiniKeyboardContainer.getMeasuredWidth();
            popupX += mMiniKeyboardContainer.getPaddingRight();
        }
        int popupY = popupKey.y + mWindowOffset[1];
        popupY += getPaddingTop();
        popupY -= mMiniKeyboardContainer.getMeasuredHeight();
        popupY += mMiniKeyboardContainer.getPaddingBottom();
        final int x = popupX;
        final int y = mShowPreview && isOneRowKeys(miniKeys) ? mPopupPreviewDisplayedY : popupY;

        int adjustedX = x;
        if (x < 0) {
            adjustedX = 0;
        } else if (x > (getMeasuredWidth() - mMiniKeyboardContainer.getMeasuredWidth())) {
            adjustedX = getMeasuredWidth() - mMiniKeyboardContainer.getMeasuredWidth();
        }
        // Log.i(TAG, "x=" + x + " y=" + y + " adjustedX=" + adjustedX + " getMeasuredWidth()=" + getMeasuredWidth());
        mMiniKeyboardOriginX = adjustedX + mMiniKeyboardContainer.getPaddingLeft() - mWindowOffset[0];
        mMiniKeyboardOriginY = y + mMiniKeyboardContainer.getPaddingTop() - mWindowOffset[1];
        mMiniKeyboard.setPopupOffset(adjustedX, y);
        mMiniKeyboard.setShiftState(getShiftState());
        // Mini keyboard needs no pop-up key preview displayed.
        mMiniKeyboard.setPreviewEnabled(false);
        mMiniKeyboardPopup.setContentView(mMiniKeyboardContainer);
        mMiniKeyboardPopup.setWidth(mMiniKeyboardContainer.getMeasuredWidth());
        mMiniKeyboardPopup.setHeight(mMiniKeyboardContainer.getMeasuredHeight());
        //Log.i(TAG, "About to show popup " + mMiniKeyboardPopup + " from " + this);
        mMiniKeyboardPopup.showAtLocation(this, Gravity.NO_GRAVITY, adjustedX, y);
        mMiniKeyboardVisible = true;

        // Inject down event on the key to mini keyboard.
        long eventTime = SystemClock.uptimeMillis();
        mMiniKeyboardPopupTime = eventTime;
        MotionEvent downEvent = generateMiniKeyboardMotionEvent(MotionEvent.ACTION_DOWN, popupKey.x
                + popupKey.width / 2, popupKey.y + popupKey.height / 2, eventTime);
        mMiniKeyboard.onTouchEvent(downEvent);
        downEvent.recycle();

        invalidateAllKeys();
        return true;
    }

    private boolean shouldDrawIconFully(Key key) {
       return isNumberAtEdgeOfPopupChars(key) || isLatinF1Key(key)|| LatinKeyboard.hasPuncOrSmileysPopup(key);
    } 
    private boolean shouldDrawLabelAndIcon(Key key) { return isNonMicLatinF1Key(key) || LatinKeyboard.hasPuncOrSmileysPopup(key); } 
    private boolean shouldAlignLeftmost(Key key) { return !key.popupReversed; } 
    private boolean isLatinF1Key(Key key) {return (mKeyboard instanceof LatinKeyboard) && ((LatinKeyboard)mKeyboard).isF1Key(key);} 
    private boolean isNonMicLatinF1Key(Key key) {return isLatinF1Key(key) && key.label != null;} 
    private static boolean isNumberAtEdgeOfPopupChars(Key key) {return isNumberAtLeftmostPopupChar(key) || isNumberAtRightmostPopupChar(key);     } 
    /* package */ static boolean isNumberAtLeftmostPopupChar(Key key) {
        if (key.popupCharacters != null && key.popupCharacters.length() > 0 && aski_dijit_kya(key.popupCharacters.charAt(0))) {
            return true;
        }
        return false;
    }
    /* package */ static boolean isNumberAtRightmostPopupChar(Key key) {
        if (key.popupCharacters != null && key.popupCharacters.length() > 0
                && aski_dijit_kya(key.popupCharacters.charAt(key.popupCharacters.length() - 1))) {
            return true;
        }
        return false;
    }
    private MotionEvent generateMiniKeyboardMotionEvent(int action, int x, int y, long eventTime) {
        return MotionEvent.obtain(mMiniKeyboardPopupTime, eventTime, action, x - mMiniKeyboardOriginX, y - mMiniKeyboardOriginY, 0);
    }
    /*package*/ boolean enableSlideKeyHack() { return false; }     
    private PointerTracker getPointerTracker(final int id) {
        final ArrayList<PointerTracker> pointers = mPointerTrackers; final Key[] keys = mKeys;
        final OnKeyboardActionListener listener = mKeyboardActionListener;
        for (int i = pointers.size(); i <= id; i++) {
            final PointerTracker tracker = new PointerTracker(
               i, mHandler, mKeyDetector, this, getResources(), enableSlideKeyHack());
            if (keys != null) tracker.setKeyboard(keys, mKeyHysteresisDistance);
            if (listener != null) tracker.setOnKeyboardActionListener(listener);
            pointers.add(tracker);
        }
        return pointers.get(id);
    }
    public boolean isInSlidingKeyInput() {
        if (mMiniKeyboardVisible) {return mMiniKeyboard.isInSlidingKeyInput();} else {return mPointerQueue.isInSlidingKeyInput();}
    }
    public int getPointerCount() { return mOldPointerCount; } 
    @Override
    public boolean onTouchEvent(MotionEvent me) { return onTouchEvent(me, false); } 
    public boolean onTouchEvent(MotionEvent me, boolean continuing) {
        final int action = me.getActionMasked();
        final int pointerCount = me.getPointerCount();
        final int oldPointerCount = mOldPointerCount;
        mOldPointerCount = pointerCount;        
        if (!mHasDistinctMultitouch && pointerCount > 1 && oldPointerCount > 1) { return true; } 
        mSwipeTracker.addMovement(me);
        if (!mMiniKeyboardVisible && mGestureDetector != null && mGestureDetector.onTouchEvent(me)) {
            dismissKeyPreview(); mHandler.cancelKeyTimers(); return true;
        }
        final long eventTime = me.getEventTime();
        final int index = me.getActionIndex();final int id = me.getPointerId(index);
        final int x = (int)me.getX(index);final int y = (int)me.getY(index);
        if (mMiniKeyboardVisible) {
            final int miniKeyboardPointerIndex = me.findPointerIndex(mMiniKeyboardTrackerId);
            if (miniKeyboardPointerIndex >= 0 && miniKeyboardPointerIndex < pointerCount) {
                final int miniKeyboardX = (int)me.getX(miniKeyboardPointerIndex);
                final int miniKeyboardY = (int)me.getY(miniKeyboardPointerIndex);
                MotionEvent translated = generateMiniKeyboardMotionEvent(action, miniKeyboardX, miniKeyboardY, eventTime);
                mMiniKeyboard.onTouchEvent(translated);
                translated.recycle();
            }
            return true;
        }
        if (mHandler.isInKeyRepeat()) {
            if (action == MotionEvent.ACTION_MOVE) { return true; }
            final PointerTracker tracker = getPointerTracker(id);
            if (pointerCount > 1 && !tracker.isModifier()) { mHandler.cancelKeyRepeatTimer(); }
        }
        if (!mHasDistinctMultitouch) { PointerTracker tracker = getPointerTracker(0);
            if (pointerCount == 1 && oldPointerCount == 2) {                 tracker.onDownEvent(x, y, eventTime);             }
            else if (pointerCount == 2 && oldPointerCount == 1) {                 tracker.onUpEvent(tracker.getLastX(), tracker.getLastY(), eventTime);             }
            else if (pointerCount == 1 && oldPointerCount == 1) {                 tracker.onTouchEvent(action, x, y, eventTime);             }
            else {Log.w(TAG, "Unknown touch panel behavior: pointer count is " + pointerCount+ " (old " + oldPointerCount + ")");}
            if (continuing) tracker.setSlidingKeyInputState(true);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) { if (!mIgnoreMove) {
                for (int i = 0; i < pointerCount; i++) {
                    PointerTracker tracker = getPointerTracker(me.getPointerId(i));
                    tracker.onMoveEvent((int)me.getX(i), (int)me.getY(i), eventTime);
                }
        } } else {
            PointerTracker tracker = getPointerTracker(id);
            switch (action) {
                case MotionEvent.ACTION_DOWN: case MotionEvent.ACTION_POINTER_DOWN:
                    mIgnoreMove = false; onDownEvent(tracker, x, y, eventTime); break;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_POINTER_UP:
                    mIgnoreMove = false; onUpEvent(tracker, x, y, eventTime); break;
                case MotionEvent.ACTION_CANCEL: onCancelEvent(tracker, x, y, eventTime); break;
            }
            if (continuing) tracker.setSlidingKeyInputState(true);
        }
        return true;
    }
    private void onDownEvent(PointerTracker tracker, int x, int y, long eventTime) {
        if (tracker.isOnModifierKey(x, y)) { mPointerQueue.releaseAllPointersExcept(null, eventTime); }
        tracker.onDownEvent(x, y, eventTime); mPointerQueue.add(tracker);
    }
    private void onUpEvent(PointerTracker tracker, int x, int y, long eventTime) {
        if (tracker.isModifier()) { mPointerQueue.releaseAllPointersExcept(tracker, eventTime); } else {
            int index = mPointerQueue.lastIndexOf(tracker);
            if (index >= 0) { mPointerQueue.releaseAllPointersOlderThan(tracker, eventTime);
            } else { Log.w(TAG, "onUpEvent: corresponding down event not found for pointer " + tracker.mPointerId); }
        }
        tracker.onUpEvent(x, y, eventTime); mPointerQueue.remove(tracker);
    }
    private void onCancelEvent(PointerTracker tracker, int x, int y, long eventTime) {
        tracker.onCancelEvent(x, y, eventTime); mPointerQueue.remove(tracker);
    }
    protected boolean swipeRight() { return mKeyboardActionListener.swipeRight(); } 
    protected boolean swipeLeft() { return mKeyboardActionListener.swipeLeft(); } 
    /*package*/ boolean swipeUp() { return mKeyboardActionListener.swipeUp(); } 
    protected boolean swipeDown() { return mKeyboardActionListener.swipeDown(); } 
    public void closing() { Log.i(TAG, "closing " + this);
        if (mPreviewPopup != null) mPreviewPopup.dismiss(); mHandler.cancelAllMessages(); dismissPopupKeyboard();
        mMiniKeyboardCacheMain.clear(); mMiniKeyboardCacheShift.clear(); mMiniKeyboardCacheCaps.clear();
    }
    @Override
    public void onDetachedFromWindow() { super.onDetachedFromWindow(); closing(); }
    protected boolean popupKeyboardIsShowing() { return mMiniKeyboardPopup != null && mMiniKeyboardPopup.isShowing(); }
    protected void dismissPopupKeyboard() {
        if (mMiniKeyboardPopup != null) {
            //Log.i(TAG, "dismissPopupKeyboard() " + mMiniKeyboardPopup + " showing=" + mMiniKeyboardPopup.isShowing());
            if (mMiniKeyboardPopup.isShowing()) { mMiniKeyboardPopup.dismiss(); }
            mMiniKeyboardVisible = false;
            mPointerQueue.releaseAllPointersExcept(null, 0); // https://github.com/klausw/hackerskeyboard/issues/477
            invalidateAllKeys();
        }
    }
    public boolean handleBack() {
        if (mMiniKeyboardPopup != null && mMiniKeyboardPopup.isShowing()) { dismissPopupKeyboard(); return true; }
        return false;
    }
}