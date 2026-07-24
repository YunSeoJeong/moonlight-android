package com.foldchord.mvp;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Maps FoldChord rear-key codes to characters and direct editor actions. */
public final class ChordMapper {
    public static final int TL = 0x01;
    public static final int LR = 0x02;
    public static final int LM = 0x04;
    public static final int LI = 0x08;
    public static final int RI = 0x10;
    public static final int RM = 0x20;
    public static final int RR = 0x40;
    public static final int TR = 0x80;

    public static final int THUMB_MASK = TL | TR;
    public static final int FINGER_MASK = LR | LM | LI | RI | RM | RR;
    public static final int BACKSPACE_MASK = LI | LM | LR;
    public static final int SPACE_MASK = RI | RM | RR;
    public static final int ENTER_MASK = LI | LM | RI | RM;

    public static final int MODIFIER_SHIFT = 1;
    public static final int MODIFIER_CTRL = 1 << 1;
    public static final int MODIFIER_ALT = 1 << 2;
    public static final int MODIFIER_META = 1 << 3;
    public static final int MODIFIER_ALT_GR = 1 << 4;

    private static final Map<Integer, String> BASE_LETTERS;
    private static final Map<Integer, String> TR_LETTERS;
    private static final Map<Integer, String> NUMBER_LAYER;
    private static final Map<Integer, String> SYMBOL_LAYER;
    private static final Map<Integer, String> HANGUL_CONSONANTS;
    private static final Map<Integer, String> HANGUL_TENSE_CONSONANTS;
    private static final Map<Integer, String> HANGUL_VOWELS;
    private static final Map<Integer, String> HANGUL_SHIFT_VOWELS;

    static {
        Map<Integer, String> letters = new LinkedHashMap<>();
        letters.put(LR, "e");
        letters.put(LM, "t");
        letters.put(LI, "a");
        letters.put(RI, "o");
        letters.put(RM, "i");
        letters.put(RR, "n");
        letters.put(LR | LM, "s");
        letters.put(LM | LI, "h");
        letters.put(RI | RM, "r");
        letters.put(RM | RR, "d");
        letters.put(LR | LI, "l");
        letters.put(RI | RR, "u");
        letters.put(LI | RI, "c");
        letters.put(LM | RM, "m");
        letters.put(LR | RR, "f");
        letters.put(LM | RI, "y");
        letters.put(LI | RM, "w");
        letters.put(LR | RM, "g");
        letters.put(LM | RR, "p");
        letters.put(LR | RI, "b");
        letters.put(LI | RR, "k");
        BASE_LETTERS = Collections.unmodifiableMap(letters);

        Map<Integer, String> trLetters = new HashMap<>();
        trLetters.put(LR, "v");
        trLetters.put(LM, "j");
        trLetters.put(LI, "x");
        trLetters.put(RI, "q");
        trLetters.put(RM, "z");
        trLetters.put(RR, ".");
        TR_LETTERS = Collections.unmodifiableMap(trLetters);

        Map<Integer, String> numbers = new HashMap<>();
        numbers.put(LR, "1");
        numbers.put(LM, "2");
        numbers.put(LI, "3");
        numbers.put(RI, "4");
        numbers.put(RM, "5");
        numbers.put(RR, "6");
        numbers.put(LR | LM, "7");
        numbers.put(LM | LI, "8");
        numbers.put(RI | RM, "9");
        numbers.put(RM | RR, "0");
        NUMBER_LAYER = Collections.unmodifiableMap(numbers);

        Map<Integer, String> symbols = new HashMap<>();
        symbols.put(LI | RI, ".");
        symbols.put(LM | RM, ",");
        symbols.put(LR | RR, "?");
        symbols.put(LR | RI, "!");
        symbols.put(LR | LI, "-");
        symbols.put(RI | RR, "_");
        symbols.put(LR | RM, "(");
        symbols.put(LM | RR, ")");
        symbols.put(LM | RI, ":");
        symbols.put(LI | RM, ";");
        symbols.put(LI | RR, "'");
        SYMBOL_LAYER = Collections.unmodifiableMap(symbols);

        Map<Integer, String> consonants = new HashMap<>();
        consonants.put(LR, "ㄱ");
        consonants.put(LM, "ㄴ");
        consonants.put(LI, "ㄷ");
        consonants.put(RI, "ㅁ");
        consonants.put(RM, "ㅅ");
        consonants.put(RR, "ㅇ");
        consonants.put(LR | LM, "ㄹ");
        consonants.put(LM | LI, "ㅂ");
        consonants.put(RI | RM, "ㅈ");
        consonants.put(RM | RR, "ㅎ");
        consonants.put(LR | LI, "ㅋ");
        consonants.put(LI | RI, "ㅌ");
        consonants.put(RI | RR, "ㅍ");
        consonants.put(LM | RM, "ㅊ");
        HANGUL_CONSONANTS = Collections.unmodifiableMap(consonants);

        Map<Integer, String> tenseConsonants = new HashMap<>();
        tenseConsonants.put(LR, "ㄲ");
        tenseConsonants.put(LI, "ㄸ");
        tenseConsonants.put(LM | LI, "ㅃ");
        tenseConsonants.put(RM, "ㅆ");
        tenseConsonants.put(RI | RM, "ㅉ");
        HANGUL_TENSE_CONSONANTS = Collections.unmodifiableMap(tenseConsonants);

        Map<Integer, String> vowels = new HashMap<>();
        vowels.put(LR, "ㅏ");
        vowels.put(LM, "ㅓ");
        vowels.put(LI, "ㅗ");
        vowels.put(RI, "ㅜ");
        vowels.put(RM, "ㅡ");
        vowels.put(RR, "ㅣ");
        vowels.put(LR | LM, "ㅑ");
        vowels.put(LM | LI, "ㅕ");
        vowels.put(LI | RI, "ㅛ");
        vowels.put(RI | RM, "ㅠ");
        vowels.put(LR | RR, "ㅐ");
        vowels.put(LM | RM, "ㅔ");
        HANGUL_VOWELS = Collections.unmodifiableMap(vowels);

        Map<Integer, String> shiftVowels = new HashMap<>();
        shiftVowels.put(LR | RR, "ㅒ");
        shiftVowels.put(LM | RM, "ㅖ");
        HANGUL_SHIFT_VOWELS = Collections.unmodifiableMap(shiftVowels);
    }

    private ChordMapper() {
    }

    public static Result resolve(int mask) {
        return resolve(mask, Language.ENGLISH, false, Layer.BASE);
    }

    public static Result resolve(int mask, Language language) {
        return resolve(mask, language, false, Layer.BASE);
    }

    /** Resolves one already-classified code. TL is never an inline Shift modifier. */
    public static Result resolve(int mask, Language language, boolean shifted, Layer layer) {
        mask &= 0xFF;
        boolean hasTl = (mask & TL) != 0;
        boolean hasTr = (mask & TR) != 0;
        int fingerMask = mask & FINGER_MASK;

        if (hasTl || fingerMask == 0) {
            return Result.none(hasTl ? "TL 제어키" : "출력 없음");
        }
        if (!hasTr) {
            if (fingerMask == BACKSPACE_MASK) {
                return Result.action(Action.BACKSPACE, "Backspace");
            }
            if (fingerMask == SPACE_MASK) {
                return Result.insert(" ", "Space");
            }
            if (fingerMask == ENTER_MASK) {
                return Result.action(Action.ENTER, "Enter");
            }
        }

        if (hasTr) {
            return language == Language.KOREAN
                    ? resolveHangulVowel(fingerMask, shifted)
                    : resolveEnglish(TR_LETTERS, fingerMask, shifted);
        }

        if (layer == Layer.NUMBER) {
            String number = NUMBER_LAYER.get(fingerMask);
            return number == null ? Result.unknown() : Result.insert(number, "숫자 " + number);
        }
        if (layer == Layer.SYMBOL) {
            String symbol = SYMBOL_LAYER.get(fingerMask);
            return symbol == null ? Result.unknown() : Result.insert(symbol, "기호 " + symbol);
        }
        if (language == Language.KOREAN) {
            String basic = HANGUL_CONSONANTS.get(fingerMask);
            if (basic == null) {
                return Result.unknown();
            }
            String tense = shifted ? HANGUL_TENSE_CONSONANTS.get(fingerMask) : null;
            String value = tense == null ? basic : tense;
            return Result.compose(value, shifted && tense != null ? "Shift " + value : value);
        }
        return resolveEnglish(BASE_LETTERS, fingerMask, shifted);
    }

    private static Result resolveEnglish(Map<Integer, String> map,
                                         int fingerMask,
                                         boolean shifted) {
        String letter = map.get(fingerMask);
        if (letter == null) {
            return Result.unknown();
        }
        String value = shifted ? letter.toUpperCase(Locale.ROOT) : letter;
        return Result.insert(value, shifted ? "Shift " + value : value);
    }

    private static Result resolveHangulVowel(int fingerMask, boolean shifted) {
        String basic = HANGUL_VOWELS.get(fingerMask);
        if (basic == null) {
            return Result.unknown();
        }
        String transformed = shifted ? HANGUL_SHIFT_VOWELS.get(fingerMask) : null;
        String value = transformed == null ? basic : transformed;
        return Result.compose(value, shifted && transformed != null ? "Shift " + value : value);
    }

    public static String describeMask(int mask) {
        StringBuilder result = new StringBuilder();
        appendBit(result, mask, TL, "TL");
        appendBit(result, mask, LR, "LR");
        appendBit(result, mask, LM, "LM");
        appendBit(result, mask, LI, "LI");
        appendBit(result, mask, RI, "RI");
        appendBit(result, mask, RM, "RM");
        appendBit(result, mask, RR, "RR");
        appendBit(result, mask, TR, "TR");
        return result.length() == 0 ? "—" : result.toString();
    }

    public static String formatMask(int mask) {
        return String.format("0x%02X", mask & 0xFF);
    }

    private static void appendBit(StringBuilder builder, int mask, int bit, String name) {
        if ((mask & bit) == 0) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(" + ");
        }
        builder.append(name);
    }

    public enum Action {
        INSERT,
        COMPOSE_HANGUL,
        BACKSPACE,
        ENTER,
        SEND_KEY,
        TOGGLE_LANGUAGE,
        STATE_CHANGE,
        NONE,
        UNKNOWN
    }

    public enum Language {
        ENGLISH,
        KOREAN
    }

    public enum Layer {
        BASE,
        NUMBER,
        SYMBOL
    }

    public static final class Result {
        public final Action action;
        public final String text;
        public final String label;
        public final int modifiers;
        public final int repeatCount;

        private Result(Action action, String text, String label,
                       int modifiers, int repeatCount) {
            this.action = action;
            this.text = text;
            this.label = label;
            this.modifiers = modifiers;
            this.repeatCount = repeatCount;
        }

        public static Result insert(String text, String label) {
            return new Result(Action.INSERT, text, label, 0, 1);
        }

        public static Result compose(String text, String label) {
            return new Result(Action.COMPOSE_HANGUL, text, label, 0, 1);
        }

        public static Result action(Action action, String label) {
            return new Result(action, "", label, 0, 1);
        }

        public static Result key(String keyName, int modifiers,
                                 int repeatCount, String label) {
            return new Result(Action.SEND_KEY, keyName, label,
                    modifiers, Math.max(1, repeatCount));
        }

        public static Result state(String label) {
            return new Result(Action.STATE_CHANGE, "", label, 0, 1);
        }

        public static Result none(String label) {
            return new Result(Action.NONE, "", label, 0, 1);
        }

        public static Result unknown() {
            return unknown("미등록 코드");
        }

        public static Result unknown(String label) {
            return new Result(Action.UNKNOWN, "", label, 0, 1);
        }

        public Result withRepeatCount(int count) {
            return new Result(action, text, label, modifiers, Math.max(1, count));
        }
    }
}
