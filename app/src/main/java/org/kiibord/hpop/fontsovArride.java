package org.kiibord.hpop;
import android.content.Context;
import android.graphics.Typeface;
import android.support.v4.content.res.ResourcesCompat;
import java.lang.reflect.Field;

public final class fontsovArride {
    public static void setDefaultFont(Context context, String staticTypefaceFieldName, int newTypefaceint) {
        final Typeface regular = ResourcesCompat.getFont(context, newTypefaceint);
        replaceFont(staticTypefaceFieldName, regular);
    }
    protected static void replaceFont(String staticTypefaceFieldName, final Typeface newTypeface) {
        try {
            final Field staticField = Typeface.class.getDeclaredField(staticTypefaceFieldName);
            staticField.setAccessible(true);
            staticField.set(null, newTypeface);
        } catch (NoSuchFieldException e) { e.printStackTrace(); } catch (IllegalAccessException e) { e.printStackTrace(); }
    }
}