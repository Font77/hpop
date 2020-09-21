package org.kiibord.hpop;

class ModifierKeyState {
    private static final int RELEASING = 0;
    private static final int PRESSING = 1;
    private static final int CHORDING = 2;

    private int mState = RELEASING;

    public void onPress() {         mState = PRESSING;     }
    public void onRelease() {         mState = RELEASING;     } 
    public void onOtherKeyPressed() {         if (mState == PRESSING)             mState = CHORDING;     }
    public boolean isChording() {         return mState == CHORDING;     }
    public String toString() {     	return "modifierKeyState:" + mState;     }
}
