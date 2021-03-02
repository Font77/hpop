package org.kiibord.hpop;
import java.util.Arrays;
import java.util.HashSet ;

public class aski_hksu {
    public final static HashSet<Character> dizits_set = new HashSet<Character>(Arrays.asList(
    '0','1','2','3','4','5','6','7','8','9',
//    ':',';','<','=','>','?',
    'L','J','q','w','X','F'
    ));
    public final static HashSet<Character> voes_set = new HashSet<Character>(Arrays.asList(
            'A','a','i','u','e','o','N','y','h','H',
            'k','g','c','z','t','d','T','D','n','p','b','m','r','l','v','s'
    ));
    public static boolean aski_dijit_kya(char c) { return dizits_set.contains(c); }
    public static boolean voes_or_dijit_kya(char c) { return dizits_set.contains(c) || voes_set.contains(c); }
    public static boolean printebl_kya(char c) { return c >= ' ' && c <= '~' ; }
    public static boolean non_voes_printebl(char c) { return c >= ' ' && c <= '~' && !voes_set.contains(c); }
}
