package org.kiibord.hpop;
import android.util.Log;
import org.kiibord.hpop.Keyboard.Key;
import java.util.Arrays;
import java.util.List;
abstract class KeyDetector {
    protected Keyboard mKeyboard;private Key[] mKeys;protected int mCorrectionX;protected int mCorrectionY;
    protected boolean mProximityCorrectOn;protected int mProximityThresholdSquare;
    public Key[] setKeyboard(Keyboard keyboard, float correctionX, float correctionY) {
        Log.i("kii_ditqktr", "kii_ditqktr krqksn_x=" + correctionX + " krqksn_y=" + correctionY);
        if (keyboard == null) throw new NullPointerException();mCorrectionX = (int)correctionX;mCorrectionY = (int)correctionY;mKeyboard = keyboard;
        List<Key> keys = mKeyboard.getKeys();Key[] array = keys.toArray(new Key[keys.size()]);
        mKeys = array;return array;
    }
    protected int getTouchX(int x) { return x + mCorrectionX; }
    protected int getTouchY(int y) { return y + mCorrectionY; }
    protected Key[] getKeys() { if (mKeys == null) throw new IllegalStateException("keyboard isn't set");
        return mKeys;
    }
    public void setProximityCorrectionEnabled(boolean enabled) {
        mProximityCorrectOn = enabled;
    }
    public boolean isProximityCorrectionEnabled() {
        return mProximityCorrectOn;
    }
    public void setProximityThreshold(int threshold) { mProximityThresholdSquare = threshold * threshold; }
    public int[] newCodeArray() {
        int[] codes = new int[getMaxNearbyKeys()];
        Arrays.fill(codes, LatinKeyboardBaseView.NOT_A_KEY);
        return codes;
    }
    abstract protected int getMaxNearbyKeys();
    abstract public int getKeyIndexAndNearbyCodes(int x, int y, int[] allKeys);
}
