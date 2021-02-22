package org.kiibord.hpop;

import android.text.TextUtils;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

public class EditingUtil {
    private static final int LOOKBACK_CHARACTER_NUM = 15;
    private static boolean sMethodsInitialized;
    private static Method sMethodGetSelectedText;
    private static Method sMethodSetComposingRegion;

    private EditingUtil() {};
    public static void appendText(InputConnection connection, String newText) {
        if (connection == null) { return; }
        connection.finishComposingText();
        CharSequence charBeforeCursor = connection.getTextBeforeCursor(1, 0);
        if (charBeforeCursor != null && !charBeforeCursor.equals(" ") && (charBeforeCursor.length() > 0)) {
            newText = " " + newText;
        }
        connection.setComposingText(newText, 1);
    }

    private static int getCursorPosition(InputConnection connection) {
        ExtractedText extracted = connection.getExtractedText( new ExtractedTextRequest(), 0);
        if (extracted == null) { return -1; }
        return extracted.startOffset + extracted.selectionStart;
    }
    public static String getWordAtCursor(InputConnection connection, String separators, Range range) {
        Range r = getWordRangeAtCursor(connection, separators, range);
        return (r == null) ? null : r.word;
    }
    public static void deleteWordAtCursor(
        InputConnection connection, String separators) {
        Range range = getWordRangeAtCursor(connection, separators, null);
        if (range == null) return;
        connection.finishComposingText();
        int newCursor = getCursorPosition(connection) - range.charsBefore;
        connection.setSelection(newCursor, newCursor);
        connection.deleteSurroundingText(0, range.charsBefore + range.charsAfter);
    }
    public static class Range {
        public int charsBefore; public int charsAfter; public String word;
        public Range() {}
        public Range(int charsBefore, int charsAfter, String word) {
            if (charsBefore < 0 || charsAfter < 0) { throw new IndexOutOfBoundsException(); }
            this.charsBefore = charsBefore;
            this.charsAfter = charsAfter;
            this.word = word;
        }
    }

    private static Range getWordRangeAtCursor(InputConnection connection, String sep, Range range) {
        if (connection == null || sep == null) { return null; }
        CharSequence before = connection.getTextBeforeCursor(1000, 0);
        CharSequence after = connection.getTextAfterCursor(1000, 0);
        if (before == null || after == null) { return null; }
        int start = before.length();
        while (start > 0 && !isWhitespace(before.charAt(start - 1), sep)) start--;
        int end = -1;
        while (++end < after.length() && !isWhitespace(after.charAt(end), sep));
        int cursor = getCursorPosition(connection);
        if (start >= 0 && cursor + end <= after.length() + before.length()) {
            String word = before.toString().substring(start, before.length()) + after.toString().substring(0, end);
            Range returnRange = range != null? range : new Range();
            returnRange.charsBefore = before.length() - start;
            returnRange.charsAfter = end;
            returnRange.word = word;
            return returnRange;
        }
        return null;
    }
    private static boolean isWhitespace(int code, String whitespace) {
        return whitespace.contains(String.valueOf((char) code));
    }
    private static final Pattern spaceRegex = Pattern.compile("\\s+");
    public static CharSequence getPreviousWord(InputConnection connection,
            String sentenceSeperators) {
        //TODO: Should fix this. This could be slow!
        CharSequence prev = connection.getTextBeforeCursor(LOOKBACK_CHARACTER_NUM, 0);
        if (prev == null) {
            return null;
        }
        String[] w = spaceRegex.split(prev);
        if (w.length >= 2 && w[w.length-2].length() > 0) {
            char lastChar = w[w.length-2].charAt(w[w.length-2].length() -1);
            if (sentenceSeperators.contains(String.valueOf(lastChar))) { return null; }
            return w[w.length-2];
        } else { return null; }
    }

    public static class SelectedWord { public int start; public int end; public CharSequence word; }
    private static boolean isWordBoundary(CharSequence singleChar, String wordSeparators) {
        return TextUtils.isEmpty(singleChar) || wordSeparators.contains(singleChar);
    }
    public static SelectedWord getWordAtCursorOrSelection(final InputConnection ic, int selStart, int selEnd, String wordSeparators) {
        if (selStart == selEnd) {
            EditingUtil.Range range = new EditingUtil.Range();
            CharSequence touching = getWordAtCursor(ic, wordSeparators, range);
            if (!TextUtils.isEmpty(touching)) {
                SelectedWord selWord = new SelectedWord();
                selWord.word = touching;
                selWord.start = selStart - range.charsBefore;
                selWord.end = selEnd + range.charsAfter;
                return selWord;
            }
        } else {
            CharSequence charsBefore = ic.getTextBeforeCursor(1, 0);
            if (!isWordBoundary(charsBefore, wordSeparators)) {
                return null;
            }
            CharSequence charsAfter = ic.getTextAfterCursor(1, 0);
            if (!isWordBoundary(charsAfter, wordSeparators)) { return null; } 
            CharSequence touching = getSelectedText(ic, selStart, selEnd);
            if (TextUtils.isEmpty(touching)) return null;
            final int length = touching.length();
            for (int i = 0; i < length; i++) {
                if (wordSeparators.contains(touching.subSequence(i, i + 1))) {
                    return null;
                }
            }
            SelectedWord selWord = new SelectedWord();
            selWord.start = selStart;
            selWord.end = selEnd;
            selWord.word = touching;
            return selWord;
        }
        return null;
    }
    private static void initializeMethodsForReflection() {
        try {
            // These will either both exist or not, so no need for separate try/catch blocks.
            // If other methods are added later, use separate try/catch blocks.
            sMethodGetSelectedText = InputConnection.class.getMethod("getSelectedText", int.class);
            sMethodSetComposingRegion = InputConnection.class.getMethod("setComposingRegion",
                    int.class, int.class);
        } catch (NoSuchMethodException exc) {
            // Ignore
        }
        sMethodsInitialized = true;
    }

    /**
     * Returns the selected text between the selStart and selEnd positions.
     */
    private static CharSequence getSelectedText(InputConnection ic, int selStart, int selEnd) {
        // Use reflection, for backward compatibility
        CharSequence result = null;
        if (!sMethodsInitialized) { initializeMethodsForReflection(); }
        if (sMethodGetSelectedText != null) {
            try {
                result = (CharSequence) sMethodGetSelectedText.invoke(ic, 0);
                return result;
            }
            catch (InvocationTargetException exc) {} // Ignore
            catch (IllegalArgumentException e) {}// Ignore
            catch (IllegalAccessException e) {}// Ignore
        }
        ic.setSelection(selStart, selEnd);
        result = ic.getTextAfterCursor(selEnd - selStart, 0);
        ic.setSelection(selStart, selEnd);
        return result;
    }
    public static void underlineWord(InputConnection ic, SelectedWord word) {
        if (!sMethodsInitialized) { initializeMethodsForReflection(); }
        if (sMethodSetComposingRegion != null) {
            try { sMethodSetComposingRegion.invoke(ic, word.start, word.end); }
            catch (InvocationTargetException exc) { } // Ignore
            catch (IllegalArgumentException e) { } // Ignore
            catch (IllegalAccessException e) { } // Ignore

        }
    }
}
