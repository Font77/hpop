package org.kiibord.hpop;
import org.xmlpull.v1.XmlPullParserException;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.util.Xml;
import android.util.DisplayMetrics;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringTokenizer;

import static org.kiibord.hpop.aski_hksu.is7BitAscii;

public class Keyboard {
    static final String TAG = "Keyboard";
    public final static char DEAD_KEY_PLACEHOLDER = 0x25cc; // dotted small circle
    public final static String DEAD_KEY_PLACEHOLDER_STRING = Character.toString(DEAD_KEY_PLACEHOLDER);
    private static final String TAG_KEYBOARD = "Keyboard";
    private static final String TAG_ROW = "Row";
    private static final String TAG_KEY = "Key";
    public static final int EDGE_LEFT = 0x01;
    public static final int EDGE_RIGHT = 0x02;
    public static final int EDGE_TOP = 0x04;
    public static final int EDGE_BOTTOM = 0x08;
    public static final int KEYCODE_SHIFT = -1;
    public static final int KEYCODE_MODE_CHANGE = -2;
    public static final int KEYCODE_CANCEL = -3;
    public static final int KEYCODE_DONE = -4;
    public static final int KEYCODE_DELETE = -5;
    public static final int KEYCODE_ALT_SYM = -6;
    public static final int DEFAULT_LAYOUT_ROWS = 4;
    public static final int DEFAULT_LAYOUT_COLUMNS = 10;
    public static final int POPUP_ADD_SHIFT = 1; 
    public static final int POPUP_ADD_CASE = 2; 
    public static final int POPUP_ADD_SELF = 4; 
    public static final int POPUP_DISABLE = 256; 
    public static final int POPUP_AUTOREPEAT = 512; 
    private float mDefaultHorizontalGap;
    private float mHorizontalPad;
    private float mVerticalPad;
    private float mDefaultWidth;
    private int mDefaultHeight;
    private int mDefaultVerticalGap;
    public static final int SHIFT_OFF = 0;
    public static final int SHIFT_ON = 1;
    public static final int SHIFT_LOCKED = 2;
    // public static final int SHIFT_CAPS = 3;
    // public static final int SHIFT_CAPS_LOCKED = 4;
    private int mShiftState = SHIFT_OFF;
    private Key mShiftKey;
    private Key mAltKey;
    private Key mCtrlKey;
    private Key mMetaKey;
    private int mShiftKeyIndex = -1;
    private int mTotalHeight;
    private int mTotalWidth;
    private List<Key> mKeys;
    private List<Key> mModifierKeys;
    private int mDisplayWidth;
    private int mDisplayHeight;
    private int mKeyboardHeight;
    private int mKeyboardMode;
    private boolean mUseExtension;
    public int mLayoutRows;
    public int mLayoutColumns;
    public int mRowCount = 1;
    public int mExtensionRowCount = 0;
    private int mCellWidth;
    private int mCellHeight;
    private int[][] mGridNeighbors;
    private int mProximityThreshold;
    private static float SEARCH_DISTANCE = 1.8f;
    public static class Row {
        public float defaultWidth;
        public int defaultHeight;
        public float defaultHorizontalGap;
        public int verticalGap;
        public int mode;
        public boolean extension;
        private Keyboard parent;
        public Row(Keyboard parent) {
            this.parent = parent;
        }
        public Row(Resources res, Keyboard parent, XmlResourceParser parser) {
            this.parent = parent;
            TypedArray a = res.obtainAttributes(Xml.asAttributeSet(parser),
                    R.styleable.Keyboard);
            defaultWidth = getDimensionOrFraction(a,
                    R.styleable.Keyboard_keyWidth,
                    parent.mDisplayWidth, parent.mDefaultWidth);
            defaultHeight = Math.round(getDimensionOrFraction(a,
                    R.styleable.Keyboard_keyHeight,
                    parent.mDisplayHeight, parent.mDefaultHeight));
            defaultHorizontalGap = getDimensionOrFraction(a,
                    R.styleable.Keyboard_horizontalGap,
                    parent.mDisplayWidth, parent.mDefaultHorizontalGap);
            verticalGap = Math.round(getDimensionOrFraction(a,
                    R.styleable.Keyboard_verticalGap,
                    parent.mDisplayHeight, parent.mDefaultVerticalGap));
            a.recycle();
            a = res.obtainAttributes(Xml.asAttributeSet(parser),
                    R.styleable.Keyboard_Row);
            mode = a.getResourceId(R.styleable.Keyboard_Row_keyboardMode,
                    0);
            extension = a.getBoolean(R.styleable.Keyboard_Row_extension, false);
            if (parent.mLayoutRows >= 5 || extension) {
                boolean isTop = (extension || parent.mRowCount - parent.mExtensionRowCount <= 0);
                float topScale = LatinIME.sKeyboardSettings.topRowScale;
                float scale = isTop ?
                        topScale :
                        1.0f + (1.0f - topScale) / (parent.mLayoutRows - 1);
                defaultHeight = Math.round(defaultHeight * scale);
            }
            a.recycle();
        }
    }
    public static class Key {
        public int[] codes;
        public CharSequence label;
        public CharSequence shiftLabel;
//  vimql      public CharSequence capsLabel;
        public Drawable icon;
        public Drawable iconPreview;
        public int width;
        private float realWidth;
        public int height;
        public int gap;
        private float realGap;
        public boolean sticky;
        public int x;
        private float realX;
        public int y;
        public boolean pressed;
        public boolean on;
        public boolean locked;
        public CharSequence text;
        public CharSequence popupCharacters;
        public boolean popupReversed;
        public boolean isCursor;
        public String hint; // Set by LatinKeyboardBaseView
        public String altHint; // Set by LatinKeyboardBaseView
        public int edgeFlags;
        public boolean modifier;
        private Keyboard keyboard;
        public int popupResId;
        public boolean repeatable;
//        private boolean isSimpleUppercase;
//        private boolean isDistinctUppercase;
        private final static int[] KEY_STATE_NORMAL_ON = {
            android.R.attr.state_checkable,
            android.R.attr.state_checked
        };
        private final static int[] KEY_STATE_PRESSED_ON = {
            android.R.attr.state_pressed,
            android.R.attr.state_checkable,
            android.R.attr.state_checked
        };
        private final static int[] KEY_STATE_NORMAL_LOCK = {
            android.R.attr.state_active,
            android.R.attr.state_checkable,
            android.R.attr.state_checked
        };
        private final static int[] KEY_STATE_PRESSED_LOCK = {
            android.R.attr.state_active,
            android.R.attr.state_pressed,
            android.R.attr.state_checkable,
            android.R.attr.state_checked
        };
        private final static int[] KEY_STATE_NORMAL_OFF = {
            android.R.attr.state_checkable
        };
        private final static int[] KEY_STATE_PRESSED_OFF = {
            android.R.attr.state_pressed,
            android.R.attr.state_checkable
        };
        private final static int[] KEY_STATE_NORMAL = {
        };
        private final static int[] KEY_STATE_PRESSED = {
            android.R.attr.state_pressed
        };
        public Key(Row parent) {
            keyboard = parent.parent;
            height = parent.defaultHeight;
            width = Math.round(parent.defaultWidth);
            realWidth = parent.defaultWidth;
            gap = Math.round(parent.defaultHorizontalGap);
            realGap = parent.defaultHorizontalGap;
        }
        public Key(Resources res, Row parent, int x, int y, XmlResourceParser parser) {
            this(parent);this.x = x;this.y = y;
            TypedArray a = res.obtainAttributes(Xml.asAttributeSet(parser), R.styleable.Keyboard);
            realWidth = getDimensionOrFraction(a, R.styleable.Keyboard_keyWidth, keyboard.mDisplayWidth, parent.defaultWidth);
            float realHeight = getDimensionOrFraction(a, R.styleable.Keyboard_keyHeight, keyboard.mDisplayHeight, parent.defaultHeight);
            realHeight -= parent.parent.mVerticalPad;
            height = Math.round(realHeight);
            this.y += parent.parent.mVerticalPad / 2;
            realGap = getDimensionOrFraction(a, R.styleable.Keyboard_horizontalGap, keyboard.mDisplayWidth, parent.defaultHorizontalGap);
            realGap += parent.parent.mHorizontalPad;
            realWidth -= parent.parent.mHorizontalPad;
            width = Math.round(realWidth);
            gap = Math.round(realGap);
            a.recycle();
            a = res.obtainAttributes(Xml.asAttributeSet(parser), R.styleable.Keyboard_Key);
            this.realX = this.x + realGap - parent.parent.mHorizontalPad / 2;
            this.x = Math.round(this.realX);
            TypedValue codesValue = new TypedValue();
            a.getValue(R.styleable.Keyboard_Key_codes, codesValue);
            if (codesValue.type == TypedValue.TYPE_INT_DEC || codesValue.type == TypedValue.TYPE_INT_HEX) codes = new int[] { codesValue.data };
            else if (codesValue.type == TypedValue.TYPE_STRING) codes = parseCSV(codesValue.string.toString());
            iconPreview = a.getDrawable(R.styleable.Keyboard_Key_iconPreview);
            if (iconPreview != null) {
                iconPreview.setBounds(0, 0, iconPreview.getIntrinsicWidth(), iconPreview.getIntrinsicHeight());
            }
            popupCharacters = a.getText(R.styleable.Keyboard_Key_popupCharacters);
            popupResId = a.getResourceId(R.styleable.Keyboard_Key_popupKeyboard, 0);
            repeatable = a.getBoolean(R.styleable.Keyboard_Key_isRepeatable, false);
            modifier = a.getBoolean(R.styleable.Keyboard_Key_isModifier, false);
            sticky = a.getBoolean(R.styleable.Keyboard_Key_isSticky, false);
            isCursor = a.getBoolean(R.styleable.Keyboard_Key_isCursor, false);
            icon = a.getDrawable(R.styleable.Keyboard_Key_keyIcon);
            if (icon != null) icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
            label = a.getText(R.styleable.Keyboard_Key_keyLabel);
            shiftLabel = a.getText(R.styleable.Keyboard_Key_shiftLabel);
            if (shiftLabel != null && shiftLabel.length() == 0) shiftLabel = null;
            text = a.getText(R.styleable.Keyboard_Key_keyOutputText);
            if (codes == null && !TextUtils.isEmpty(label)) {
                codes = getFromString(label);
                if (codes != null && codes.length == 1) {
                    final Locale locale = LatinIME.sKeyboardSettings.inputLocale;
                    String upperLabel = label.toString();//.toUpperCase(locale);
//                    String upperLabel = label.toString().toUpperCase(locale);
                    if (shiftLabel == null) {
                        if (!upperLabel.equals(label.toString()) && upperLabel.length() == 1) shiftLabel = upperLabel;
                    }
                    else if (upperLabel.length() == 1) { }
                }
                if ((LatinIME.sKeyboardSettings.popupKeyboardFlags & POPUP_DISABLE) != 0) {
                    popupCharacters = null;
                    popupResId = 0;
                }
                if ((LatinIME.sKeyboardSettings.popupKeyboardFlags & POPUP_AUTOREPEAT) != 0) repeatable = true;
            }
            a.recycle();
        }
        public boolean isDistinctCaps() {
//            return isDistinctUppercase && keyboard.isShiftCaps();
            return false;
        }
        public boolean isShifted() {
            //boolean shifted = keyboard.isShifted(isSimpleUppercase);
            boolean shifted = keyboard.isShifted();
            return shifted;
        }
//        public int getPrimaryCode(boolean isShiftCaps, boolean isShifted) {
        public int getPrimaryCode(boolean isShifted) {
//            if (isDistinctUppercase && isShiftCaps) {
//                return capsLabel.charAt(0);
//            }
            if (isShifted && shiftLabel != null) {
                if (shiftLabel.charAt(0) == DEAD_KEY_PLACEHOLDER && shiftLabel.length() >= 2) {
                    return shiftLabel.charAt(1);
                } else {
                    return shiftLabel.charAt(0);
                }
            } else {
                return codes[0];
            }
        }
        public int getPrimaryCode() {
            return getPrimaryCode(keyboard.isShifted());
//            return getPrimaryCode(keyboard.isShiftCaps(), keyboard.isShifted(isSimpleUppercase));
        }
        public boolean isDeadKey() {
            if (codes == null || codes.length < 1) return false;
            return Character.getType(codes[0]) == Character.NON_SPACING_MARK;
        }
        public int[] getFromString(CharSequence str) {
            if (str.length() > 1) {
                if (str.charAt(0) == DEAD_KEY_PLACEHOLDER && str.length() >= 2) {
                    return new int[] { str.charAt(1) }; // FIXME: >1 length?
                } else {
                    text = str; // TODO: add space?
                    return new int[] { 0 };
                }
            } else {
                char c = str.charAt(0);
                return new int[] { c };
            }
        }
        public String getCaseLabel() {
//            if (isDistinctUppercase && keyboard.isShiftCaps()) {
//                return capsLabel.toString();
//            }
            boolean isShifted = keyboard.isShifted();
//            boolean isShifted = keyboard.isShifted(isSimpleUppercase);
            if (isShifted && shiftLabel != null) {
                return shiftLabel.toString();
            } else {
                return label != null ? label.toString() : null;
            }
        }
//        private String getPopupKeyboardContent(boolean isShiftCaps, boolean isShifted, boolean addExtra) {
        private String getPopupKeyboardContent(boolean isShifted, boolean addExtra) {
            int mainChar = getPrimaryCode(false);
            int shiftChar = getPrimaryCode(true);
//            int capsChar = getPrimaryCode(true, true);
            if (shiftChar == mainChar) shiftChar = 0;
//            if (capsChar == shiftChar || capsChar == mainChar) capsChar = 0;
            int popupLen = (popupCharacters == null) ? 0 : popupCharacters.length();
            StringBuilder popup = new StringBuilder(popupLen);
            for (int i = 0; i < popupLen; ++i) {
                char c = popupCharacters.charAt(i);
//                if (isShifted || isShiftCaps) {
                if (isShifted) {
//                    String upper = Character.toString(c).toUpperCase(LatinIME.sKeyboardSettings.inputLocale);
                    String upper = Character.toString(c);//.toUpperCase(LatinIME.sKeyboardSettings.inputLocale);
                    if (upper.length() == 1) c = upper.charAt(0);
                }
                if (c == mainChar || c == shiftChar ) continue;
//                if (c == mainChar || c == shiftChar || c == capsChar) continue;
                popup.append(c);
            }
            if (addExtra) {
                StringBuilder extra = new StringBuilder(3 + popup.length());
                int flags = LatinIME.sKeyboardSettings.popupKeyboardFlags;
                if ((flags & POPUP_ADD_SELF) != 0) {
//                    if (isDistinctUppercase && isShiftCaps) {
//                        if (capsChar > 0) { extra.append((char) capsChar); capsChar = 0; }
//                    } else
                    if (isShifted) {
                        if (shiftChar > 0) { extra.append((char) shiftChar); shiftChar = 0; }
                    } else {
                        if (mainChar > 0) { extra.append((char) mainChar); mainChar = 0; }
                    }
                }
                if ((flags & POPUP_ADD_CASE) != 0) {
                    // if shifted, add unshifted key to popup, and vice versa
//                    if (isDistinctUppercase && isShiftCaps) {
//                        if (mainChar > 0) { extra.append((char) mainChar); mainChar = 0; }
//                        if (shiftChar > 0) { extra.append((char) shiftChar); shiftChar = 0; }
//                    } else
                    if (isShifted) {
                        if (mainChar > 0) { extra.append((char) mainChar); mainChar = 0; }
//                        if (capsChar > 0) { extra.append((char) capsChar); capsChar = 0; }
                    } else {
                        if (shiftChar > 0) { extra.append((char) shiftChar); shiftChar = 0; }
//                        if (capsChar > 0) { extra.append((char) capsChar); capsChar = 0; }
                    }
                }
                if (
//                        !isSimpleUppercase &&
                                (flags & POPUP_ADD_SHIFT) != 0) {
                    if (isShifted) {
                        if (mainChar > 0) { extra.append((char) mainChar); mainChar = 0; }
                    } else {
                        if (shiftChar > 0) { extra.append((char) shiftChar); shiftChar = 0; }
                    }
                }
                extra.append(popup);
                return extra.toString();
            }
            return popup.toString();
        }
        public Keyboard getPopupKeyboard(Context context, int padding) {
            if (popupCharacters == null) {
                if (popupResId != 0) {
                    return new Keyboard(context, keyboard.mDefaultHeight, popupResId);
                } else {
                    if (modifier) return null; // Space, Return etc.
                }
            }
            if ((LatinIME.sKeyboardSettings.popupKeyboardFlags & POPUP_DISABLE) != 0) return null;
            String popup = getPopupKeyboardContent(keyboard.isShifted(), true);
//            String popup = getPopupKeyboardContent(keyboard.isShiftCaps(), keyboard.isShifted(isSimpleUppercase), true);
            if (popup.length() > 0) {
                int resId = popupResId;
                if (resId == 0) resId = R.xml.kbd_popup_template;
                return new Keyboard(context, keyboard.mDefaultHeight, resId, popup, popupReversed, -1, padding);
            } else {
                return null;
            }
        }
        public String getHintLabel(boolean wantAscii, boolean wantAll) {
            if (hint == null) {
                hint = "";
                if (shiftLabel != null 
                // && !isSimpleUppercase
                ) {
                    char c = shiftLabel.charAt(0);
                    if (wantAll || wantAscii && is7BitAscii(c)) {
                        hint = Character.toString(c);
                    }
                }
            }
            return hint;
        }
        public String getAltHintLabel(boolean wantAscii, boolean wantAll) {
            if (altHint == null) {
                altHint = "";
                String popup = getPopupKeyboardContent( false, false);
                if (popup.length() > 0) {
                    char c = popup.charAt(0);
                    if (wantAll || wantAscii && is7BitAscii(c)) {
                        altHint = Character.toString(c);
                    }
                }
            }
            return altHint;
        }

        public void onPressed() {
            pressed = !pressed;
        }
        public void onReleased(boolean inside) {
            pressed = !pressed;
        }
        int[] parseCSV(String value) {
            int count = 0;
            int lastIndex = 0;
            if (value.length() > 0) {
                count++;
                while ((lastIndex = value.indexOf(",", lastIndex + 1)) > 0) {
                    count++;
                }
            }
            int[] values = new int[count];
            count = 0;
            StringTokenizer st = new StringTokenizer(value, ",");
            while (st.hasMoreTokens()) {
                try {
                    values[count++] = Integer.parseInt(st.nextToken());
                } catch (NumberFormatException nfe) {
                    Log.e(TAG, "Error parsing keycodes " + value);
                }
            }
            return values;
        }
        public boolean isInside(int x, int y) {
            boolean leftEdge = (edgeFlags & EDGE_LEFT) > 0;
            boolean rightEdge = (edgeFlags & EDGE_RIGHT) > 0;
            boolean topEdge = (edgeFlags & EDGE_TOP) > 0;
            boolean bottomEdge = (edgeFlags & EDGE_BOTTOM) > 0;
            if ((x >= this.x || (leftEdge && x <= this.x + this.width))
                    && (x < this.x + this.width || (rightEdge && x >= this.x))
                    && (y >= this.y || (topEdge && y <= this.y + this.height))
                    && (y < this.y + this.height || (bottomEdge && y >= this.y))) {
                return true;
            } else {
                return false;
            }
        }
        public int squaredDistanceFrom(int x, int y) {
            int xDist = this.x + width / 2 - x;
            int yDist = this.y + height / 2 - y;
            return xDist * xDist + yDist * yDist;
        }
        public int[] getCurrentDrawableState() {
            int[] states = KEY_STATE_NORMAL;
            if (locked) {
                if (pressed) {
                    states = KEY_STATE_PRESSED_LOCK;
                } else {
                    states = KEY_STATE_NORMAL_LOCK;
                }
            } else if (on) {
                if (pressed) {
                    states = KEY_STATE_PRESSED_ON;
                } else {
                    states = KEY_STATE_NORMAL_ON;
                }
            } else {
                if (sticky) {
                    if (pressed) {
                        states = KEY_STATE_PRESSED_OFF;
                    } else {
                        states = KEY_STATE_NORMAL_OFF;
                    }
                } else {
                    if (pressed) {
                        states = KEY_STATE_PRESSED;
                    }
                }
            }
            return states;
        }
        public String toString() {
            int code = (codes != null && codes.length > 0) ? codes[0] : 0;
            String edges = (
                    ((edgeFlags & Keyboard.EDGE_LEFT) != 0 ? "L" : "-") +
                    ((edgeFlags & Keyboard.EDGE_RIGHT) != 0 ? "R" : "-") +
                    ((edgeFlags & Keyboard.EDGE_TOP) != 0 ? "T" : "-") +
                    ((edgeFlags & Keyboard.EDGE_BOTTOM) != 0 ? "B" : "-"));
            return "KeyDebugFIXME(label=" + label +
                (shiftLabel != null ? " shift=" + shiftLabel : "") +
//                (capsLabel != null ? " caps=" + capsLabel : "") +
                (text != null ? " text=" + text : "" ) +
                " code=" + code +
                (code <= 0 || Character.isWhitespace(code) ? "" : ":'" + (char)code + "'" ) +
                " x=" + x + ".." + (x+width) + " y=" + y + ".." + (y+height) +
                " edgeFlags=" + edges +
                (popupCharacters != null ? " pop=" + popupCharacters : "" ) +
                " res=" + popupResId +
                ")";
        }
    }
    public Keyboard(Context context, int defaultHeight, int xmlLayoutResId) {
        this(context, defaultHeight, xmlLayoutResId, 0);
    }
    public Keyboard(Context context, int defaultHeight, int xmlLayoutResId, int modeId) {
        this(context, defaultHeight, xmlLayoutResId, modeId, 0);
    }
    public Keyboard(Context context, int defaultHeight, int xmlLayoutResId, int modeId, float kbHeightPercent) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        mDisplayWidth = dm.widthPixels;
        mDisplayHeight = dm.heightPixels;
        Log.v(TAG, "keyboard's display metrics:" + dm + ", mDisplayWidth=" + mDisplayWidth);
        mDefaultHorizontalGap = 0;
        mDefaultWidth = mDisplayWidth / 10;
        mDefaultVerticalGap = 0;
        mDefaultHeight = defaultHeight; // may be zero, to be adjusted below
        mKeyboardHeight = Math.round(mDisplayHeight * kbHeightPercent / 100); 
        mKeys = new ArrayList<Key>();
        mModifierKeys = new ArrayList<Key>();
        mKeyboardMode = modeId;
        mUseExtension = LatinIME.sKeyboardSettings.useExtension;
        loadKeyboard(context, context.getResources().getXml(xmlLayoutResId));
        setEdgeFlags();
        fixAltChars(LatinIME.sKeyboardSettings.inputLocale);
    }
    private Keyboard(Context context, int defaultHeight, int layoutTemplateResId,
            CharSequence characters, boolean reversed, int columns, int horizontalPadding) {
        this(context, defaultHeight, layoutTemplateResId);
        int x = 0;
        int y = 0;
        int column = 0;
        mTotalWidth = 0;
        Row row = new Row(this);
        row.defaultHeight = mDefaultHeight;
        row.defaultWidth = mDefaultWidth;
        row.defaultHorizontalGap = mDefaultHorizontalGap;
        row.verticalGap = mDefaultVerticalGap;
        final int maxColumns = columns == -1 ? Integer.MAX_VALUE : columns;
        mLayoutRows = 1;
        int start = reversed ? characters.length()-1 : 0;
        int end = reversed ? -1 : characters.length();
        int step = reversed ? -1 : 1;
        for (int i = start; i != end; i+=step) {
            char c = characters.charAt(i);
            if (column >= maxColumns
                    || x + mDefaultWidth + horizontalPadding > mDisplayWidth) {
                x = 0;
                y += mDefaultVerticalGap + mDefaultHeight;
                column = 0;
                ++mLayoutRows;
            }
            final Key key = new Key(row);
            key.x = x;
            key.realX = x;
            key.y = y;
            key.label = String.valueOf(c);
            key.codes = key.getFromString(key.label);
            column++;
            x += key.width + key.gap;
            mKeys.add(key);
            if (x > mTotalWidth) {
                mTotalWidth = x;
            }
        }
        mTotalHeight = y + mDefaultHeight;
        mLayoutColumns = columns == -1 ? column : maxColumns;
        setEdgeFlags();
    }
    private void setEdgeFlags() {
        if (mRowCount == 0) mRowCount = 1; // Assume one row if not set
        int row = 0;
        Key prevKey = null;
        int rowFlags = 0;
        for (Key key : mKeys) {
            int keyFlags = 0;
            if (prevKey == null || key.x <= prevKey.x) {
                if (prevKey != null) {
                    prevKey.edgeFlags |= Keyboard.EDGE_RIGHT;
                }
                rowFlags = 0;
                if (row == 0) rowFlags |= Keyboard.EDGE_TOP;
                if (row == mRowCount - 1) rowFlags |= Keyboard.EDGE_BOTTOM;
                ++row;
                keyFlags |= Keyboard.EDGE_LEFT;
            }
            key.edgeFlags = rowFlags | keyFlags;
            prevKey = key;
        }
        if (prevKey != null) prevKey.edgeFlags |= Keyboard.EDGE_RIGHT;
    }
    private void fixAltChars(Locale locale) {
        if (locale == null) locale = Locale.getDefault();
        Set<Character> mainKeys = new HashSet<Character>();
        for (Key key : mKeys) {
            if (key.label != null && !key.modifier && key.label.length() == 1) {
                char c = key.label.charAt(0);
                mainKeys.add(c);
            }
        }
        for (Key key : mKeys) {
            if (key.popupCharacters == null) continue;
            int popupLen = key.popupCharacters.length();
            if (popupLen == 0) {
                continue;
            }
            if (key.x >= mTotalWidth / 2) {
                key.popupReversed = true;
            }
/*            boolean needUpcase = key.label != null && key.label.length() == 1 && Character.isUpperCase(key.label.charAt(0));
            if (needUpcase) {
                key.popupCharacters = key.popupCharacters.toString().toUpperCase();
                popupLen = key.popupCharacters.length();
            }*/
            StringBuilder newPopup = new StringBuilder(popupLen);
            for (int i = 0; i < popupLen; ++i) {
                char c = key.popupCharacters.charAt(i);
                if (mainKeys.contains(c)) continue;  //orijnl already present elsewhere
//                if ((key.edgeFlags & EDGE_TOP) == 0) continue; //orijnl
//                if ( aski_dijit_kya(c) && mainKeys.contains(c)) continue;  //orijnl already present elsewhere
//                if ( aski_dijit_kya(c) && (key.edgeFlags & EDGE_TOP) == 0) continue; //orijnl
                //vimql if (mainKeys.contains(c)) continue;  // already present elsewhere//vimql
                //vimql if ((key.edgeFlags & EDGE_TOP) == 0 ) continue; //vimql
                newPopup.append(c);
            }
            key.popupCharacters = newPopup.toString();
        }
    }
    public List<Key> getKeys() {
        return mKeys;
    }
    public List<Key> getModifierKeys() {
        return mModifierKeys;
    }
    protected int getHorizontalGap() {
        return Math.round(mDefaultHorizontalGap);
    }
    protected void setHorizontalGap(int gap) {
        mDefaultHorizontalGap = gap;
    }
    protected int getVerticalGap() {
        return mDefaultVerticalGap;
    }
    protected void setVerticalGap(int gap) {
        mDefaultVerticalGap = gap;
    }
    protected int getKeyHeight() {
        return mDefaultHeight;
    }
    protected void setKeyHeight(int height) {
        mDefaultHeight = height;
    }
    protected int getKeyWidth() {
        return Math.round(mDefaultWidth);
    }
    protected void setKeyWidth(int width) {
        mDefaultWidth = width;
    }
    public int getHeight() {
        return mTotalHeight;
    }
    public int getScreenHeight() {
        return mDisplayHeight;
    }
    public int getMinWidth() {
        return mTotalWidth;
    }
    public boolean setShiftState(int shiftState, boolean updateKey) {
        if (updateKey && mShiftKey != null) {
            mShiftKey.on = (shiftState != SHIFT_OFF);
        }
        if (mShiftState != shiftState) {
            mShiftState = shiftState;
            return true;
        }
        return false;
    }
    public boolean setShiftState(int shiftState) {
        return setShiftState(shiftState, true);
    }
    public Key setCtrlIndicator(boolean active) {
        if (mCtrlKey != null) mCtrlKey.on = active;
        return mCtrlKey;
    }
    public Key setAltIndicator(boolean active) {
        if (mAltKey != null) mAltKey.on = active;
        return mAltKey;
    }
    public Key setMetaIndicator(boolean active) {
        if (mMetaKey != null) mMetaKey.on = active;
        return mMetaKey;
    }
//    public boolean isShiftCaps() {
//        return mShiftState == SHIFT_CAPS || mShiftState == SHIFT_CAPS_LOCKED;
//    }
//    public boolean isShifted(boolean applyCaps) {
    public boolean isShifted() {
//        if (applyCaps) {
//            return mShiftState != SHIFT_OFF;
//        } else {
            return mShiftState == SHIFT_ON || mShiftState == SHIFT_LOCKED;
//        }
    }
    public int getShiftState() {
        return mShiftState;
    }
    public int getShiftKeyIndex() {
        return mShiftKeyIndex;
    }
    private void computeNearestNeighbors() {
        mCellWidth = (getMinWidth() + mLayoutColumns - 1) / mLayoutColumns;
        mCellHeight = (getHeight() + mLayoutRows - 1) / mLayoutRows;
        mGridNeighbors = new int[mLayoutColumns * mLayoutRows][];
        int[] indices = new int[mKeys.size()];
        final int gridWidth = mLayoutColumns * mCellWidth;
        final int gridHeight = mLayoutRows * mCellHeight;
        for (int x = 0; x < gridWidth; x += mCellWidth) {
            for (int y = 0; y < gridHeight; y += mCellHeight) {
                int count = 0;
                for (int i = 0; i < mKeys.size(); i++) {
                    final Key key = mKeys.get(i);
                    boolean isSpace = key.codes != null && key.codes.length > 0 &&
                    		key.codes[0] == LatinIME.ASCII_SPACE;
                    if (key.squaredDistanceFrom(x, y) < mProximityThreshold ||
                            key.squaredDistanceFrom(x + mCellWidth - 1, y) < mProximityThreshold ||
                            key.squaredDistanceFrom(x + mCellWidth - 1, y + mCellHeight - 1)
                                < mProximityThreshold ||
                            key.squaredDistanceFrom(x, y + mCellHeight - 1) < mProximityThreshold ||
                            isSpace && !(
                            		x + mCellWidth - 1 < key.x ||
                            		x > key.x + key.width ||
                            		y + mCellHeight - 1 < key.y ||
                            		y > key.y + key.height)) {
                        indices[count++] = i;
                    }
                }
                int [] cell = new int[count];
                System.arraycopy(indices, 0, cell, 0, count);
                mGridNeighbors[(y / mCellHeight) * mLayoutColumns + (x / mCellWidth)] = cell;
            }
        }
    }
    public int[] getNearestKeys(int x, int y) {
        if (mGridNeighbors == null) computeNearestNeighbors();
        if (x >= 0 && x < getMinWidth() && y >= 0 && y < getHeight()) {
            int index = (y / mCellHeight) * mLayoutColumns + (x / mCellWidth);
            if (index < mLayoutRows * mLayoutColumns) {
                return mGridNeighbors[index];
            }
        }
        return new int[0];
    }
    protected Row createRowFromXml(Resources res, XmlResourceParser parser) {
        return new Row(res, this, parser);
    }
    protected Key createKeyFromXml(Resources res, Row parent, int x, int y,
            XmlResourceParser parser) {
        return new Key(res, parent, x, y, parser);
    }
    private void loadKeyboard(Context context, XmlResourceParser parser) {
        boolean inKey = false;
        boolean inRow = false;
        float x = 0;
        int y = 0;
        Key key = null;
        Row currentRow = null;
        Resources res = context.getResources();
        boolean skipRow = false;
        mRowCount = 0;
        try {
            int event;
            Key prevKey = null;
            while ((event = parser.next()) != XmlResourceParser.END_DOCUMENT) {
                if (event == XmlResourceParser.START_TAG) {
                    String tag = parser.getName();
                    if (TAG_ROW.equals(tag)) {
                        inRow = true;
                        x = 0;
                        currentRow = createRowFromXml(res, parser);
                        skipRow = currentRow.mode != 0 && currentRow.mode != mKeyboardMode;
                        if (currentRow.extension) {
                            if (mUseExtension) {
                                ++mExtensionRowCount;
                            } else {
                                skipRow = true;
                            }
                        }
                        if (skipRow) {
                            skipToEndOfRow(parser);
                            inRow = false;
                        }
                   } else if (TAG_KEY.equals(tag)) {
                        inKey = true;
                        key = createKeyFromXml(res, currentRow, Math.round(x), y, parser);
                        key.realX = x;
                        if (key.codes == null) {
                          // skip this key, adding its width to the previous one
                          if (prevKey != null) {
                              prevKey.width += key.width;
                          }
                        } else {
                          mKeys.add(key);
                          prevKey = key;
                          if (key.codes[0] == KEYCODE_SHIFT) {
                              if (mShiftKeyIndex == -1) {
                                  mShiftKey = key;
                                  mShiftKeyIndex = mKeys.size()-1;
                              }
                              mModifierKeys.add(key);
                          } else if (key.codes[0] == KEYCODE_ALT_SYM) {
                              mModifierKeys.add(key);
                          } else if (key.codes[0] == LatinKeyboardView.KEYCODE_CTRL_LEFT) {
                              mCtrlKey = key;
                          } else if (key.codes[0] == LatinKeyboardView.KEYCODE_ALT_LEFT) {
                              mAltKey = key;
                          } else if (key.codes[0] == LatinKeyboardView.KEYCODE_META_LEFT) {
                              mMetaKey = key;
                          }
                        }
                    } else if (TAG_KEYBOARD.equals(tag)) {
                        parseKeyboardAttributes(res, parser);
                    }
                } else if (event == XmlResourceParser.END_TAG) {
                    if (inKey) {
                        inKey = false;
                        x += key.realGap + key.realWidth;
                        if (x > mTotalWidth) {
                            mTotalWidth = Math.round(x);
                        }
                    } else if (inRow) {
                        inRow = false;
                        y += currentRow.verticalGap;
                        y += currentRow.defaultHeight;
                        mRowCount++;
                    } else {
                        // TODO: error or extend?
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Parse error:" + e);
            e.printStackTrace();
        }
        mTotalHeight = y - mDefaultVerticalGap;
    }
    public void setKeyboardWidth(int newWidth) {
        Log.i(TAG, "setKeyboardWidth newWidth=" + newWidth + ", mTotalWidth=" + mTotalWidth);
        if (newWidth <= 0) return;  // view not initialized?
        if (mTotalWidth <= newWidth) return;  // it already fits
        float scale = (float) newWidth / mDisplayWidth;
        Log.i("PCKeyboard", "Rescaling keyboard: " + mTotalWidth + " => " + newWidth);
        for (Key key : mKeys) {
            key.x = Math.round(key.realX * scale);
        }
        mTotalWidth = newWidth;
    }
    private void skipToEndOfRow(XmlResourceParser parser)
            throws XmlPullParserException, IOException {
        int event;
        while ((event = parser.next()) != XmlResourceParser.END_DOCUMENT) {
            if (event == XmlResourceParser.END_TAG
                    && parser.getName().equals(TAG_ROW)) {
                break;
            }
        }
    }
    private void parseKeyboardAttributes(Resources res, XmlResourceParser parser) {
        TypedArray a = res.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard);
        mDefaultWidth = getDimensionOrFraction(a,
                R.styleable.Keyboard_keyWidth,
                mDisplayWidth, mDisplayWidth / 10);
        mDefaultHeight = Math.round(getDimensionOrFraction(a,
                R.styleable.Keyboard_keyHeight,
                mDisplayHeight, mDefaultHeight));
        mDefaultHorizontalGap = getDimensionOrFraction(a,
                R.styleable.Keyboard_horizontalGap,
                mDisplayWidth, 0);
        mDefaultVerticalGap = Math.round(getDimensionOrFraction(a,
                R.styleable.Keyboard_verticalGap,
                mDisplayHeight, 0));
        mHorizontalPad = getDimensionOrFraction(a,
                R.styleable.Keyboard_horizontalPad,
                mDisplayWidth, res.getDimension(R.dimen.key_horizontal_pad));
        mVerticalPad = getDimensionOrFraction(a,
                R.styleable.Keyboard_verticalPad,
                mDisplayHeight, res.getDimension(R.dimen.key_vertical_pad));
        mLayoutRows = a.getInteger(R.styleable.Keyboard_layoutRows, DEFAULT_LAYOUT_ROWS);
        mLayoutColumns = a.getInteger(R.styleable.Keyboard_layoutColumns, DEFAULT_LAYOUT_COLUMNS);
        if (mDefaultHeight == 0 && mKeyboardHeight > 0 && mLayoutRows > 0) {
            mDefaultHeight = mKeyboardHeight / mLayoutRows;
        }
        mProximityThreshold = (int) (mDefaultWidth * SEARCH_DISTANCE);
        mProximityThreshold = mProximityThreshold * mProximityThreshold; // Square it for comparison
        a.recycle();
    }
    static float getDimensionOrFraction(TypedArray a, int index, int base, float defValue) {
        TypedValue value = a.peekValue(index);
        if (value == null) return defValue;
        if (value.type == TypedValue.TYPE_DIMENSION) {
            return a.getDimensionPixelOffset(index, Math.round(defValue));
        } else if (value.type == TypedValue.TYPE_FRACTION) {
            return a.getFraction(index, base, base, defValue);
        }
        return defValue;
    }
    @Override
    public String toString() {
        return "Keyboard(" + mLayoutColumns + "x" + mLayoutRows + " keys=" + mKeys.size() +
            " rowCount=" + mRowCount + " mode=" + mKeyboardMode + " size=" + mTotalWidth + "x" + mTotalHeight + ")";
    }
}