package org.kiibord.hpop;

public class aski_hksu {
    public static boolean aski_dijit_kya(char c) {
        return (c > 0x2f) && (c < 0x40);
        // Character.isDigit(c) ---> ((c > 0x2f) && (c < 0x40))
    }
    public static boolean is7BitAscii(char c) {
        if (
           // (c >= 'A' && c <= 'Z') || 
           (c > 0x60 && c < 0x7b)
        ) return false;
        return c > 0x1f && c < 0x7f;
    }
    public static boolean voes_or_dijit_kya(char c) {
        return (
           ((c > 0x60) && (c < 0x7b))
           ||
           ((c > 0x2f) && (c < 0x40))
        );
    }
}
