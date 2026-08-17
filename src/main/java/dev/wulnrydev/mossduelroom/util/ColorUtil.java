package dev.wulnrydev.mossduelroom.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renk kodu donusturucusu.
 * <p>
 * Hem klasik {@code &a} kodlarini hem de {@code &#RRGGBB} hex kodlarini destekler.
 * Donusum sonucu {@code §x§R§R§G§G§B§B} formatina cevrilir; bu format 1.16+
 * tum Spigot/Paper turevlerinde calisir ve ekstra bir kutuphane gerektirmez.
 */
public final class ColorUtil {

    /** {@code &#RRGGBB} kaliplarini yakalar. */
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private ColorUtil() {
    }

    /**
     * Metni oyun ici renkli hale getirir.
     *
     * @param input ham metin (null olabilir)
     * @return renklendirilmis metin, null girdide bos metin
     */
    public static String colorize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        // Once hex kodlari cozulur, ardindan klasik & kodlari.
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuilder builder = new StringBuilder(input.length() + 32);
        while (matcher.find()) {
            matcher.appendReplacement(builder, Matcher.quoteReplacement(toLegacyHex(matcher.group(1))));
        }
        matcher.appendTail(builder);

        return translateAmpersand(builder.toString());
    }

    /**
     * "00D420" -> "§x§0§0§D§4§2§0" donusumu.
     * Bungee ChatColor.of(...) ile ayni ciktiyi uretir ancak reflection/kutuphane
     * bagimliligi olusturmaz.
     */
    private static String toLegacyHex(String hex) {
        StringBuilder out = new StringBuilder(14);
        out.append('§').append('x');
        for (int i = 0; i < hex.length(); i++) {
            out.append('§').append(Character.toLowerCase(hex.charAt(i)));
        }
        return out.toString();
    }

    /**
     * {@code &} ile baslayan klasik renk kodlarini {@code §} karakterine cevirir.
     * Bukkit'in ChatColor.translateAlternateColorCodes metodunun ayni davranisi,
     * fakat gereksiz kopya olusturmadan.
     */
    private static String translateAmpersand(String input) {
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] != '&') {
                continue;
            }
            if ("0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(chars[i + 1]) == -1) {
                continue;
            }
            chars[i] = '§';
            chars[i + 1] = Character.toLowerCase(chars[i + 1]);
        }
        return new String(chars);
    }

    /**
     * Metindeki tum renk kodlarini temizler (konsol ciktilari icin).
     */
    public static String strip(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return colorize(input).replaceAll("§[0-9a-fk-orx]", "");
    }
}
