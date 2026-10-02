/* Foldegram branding helper. Client source: GPL v2 or later; bundled font: SIL OFL 1.1. */
package org.telegram.ui.Components;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import org.telegram.messenger.ApplicationLoader;

/** Bricolage Grotesque 650/100/24 is reserved for the app name, never conversation text. */
public final class FoldegramWordmark {
    private static Typeface typeface;
    public static synchronized Typeface typeface() {
        if (typeface == null) {
            typeface = Typeface.createFromAsset(ApplicationLoader.applicationContext.getAssets(),
                    "fonts/Foldegram-Bricolage650.ttf");
        }
        return typeface;
    }
    public static CharSequence style(CharSequence text) {
        if (text == null) return null;
        SpannableStringBuilder result = new SpannableStringBuilder(text);
        int start = text.toString().indexOf("Foldegram");
        if (start >= 0) result.setSpan(new TypefaceSpan(typeface()), start, start + 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return result;
    }
    private FoldegramWordmark() {}
}
