package com.evolt.teamecon.client;

import com.ibm.icu.text.Transliterator;
import java.text.Normalizer;
import java.util.Locale;

/** A catalogue entry's search keys, built once rather than transliterated on every keypress. */
public record SearchText(String literal, String pinyin, String initials) {
    private static final class Han {
        // ICU is already supplied by every supported Minecraft version.
        private static final Transliterator LATIN = Transliterator.getInstance("Han-Latin; Latin-ASCII");
        private static synchronized String romanize(String name) { return LATIN.transliterate(name); }
    }

    public static SearchText of(String name, String id) {
        String literal = fold(name) + "\n" + fold(id);
        if (name.codePoints().noneMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN))
            return new SearchText(literal, "", "");
        String[] syllables = fold(Han.romanize(name)).split("[^a-z0-9]+");
        StringBuilder full = new StringBuilder(), shortName = new StringBuilder();
        for (String syllable : syllables) if (!syllable.isEmpty()) {
            full.append(syllable);
            shortName.append(syllable.charAt(0));
        }
        return new SearchText(literal, full.toString(), shortName.toString());
    }

    public boolean matches(String query) {
        String needle = fold(query).trim();
        if (needle.isEmpty() || literal.contains(needle)) return true;
        String phonetic = needle.replace("u:", "u").replace('v', 'u').replaceAll("[\\s'’\\-]+", "");
        return !phonetic.isEmpty() && (pinyin.contains(phonetic) || initials.contains(phonetic));
    }

    private static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
}
