package com.foldchord.mvp;

import java.util.HashMap;
import java.util.Map;

/** Pure state machine for composing modern Korean compatibility jamo into syllables. */
public final class HangulComposer {
    private static final char[] INITIALS = {
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
            'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };
    private static final char[] MEDIALS = {
            'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ',
            'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ'
    };
    private static final char[] FINALS = {
            0, 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ',
            'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ',
            'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };

    private static final Map<String, Character> COMPOUND_VOWELS = new HashMap<>();
    private static final Map<Character, Character> VOWEL_BACKSPACE = new HashMap<>();
    private static final Map<String, Character> COMPOUND_FINALS = new HashMap<>();
    private static final Map<Character, FinalSplit> FINAL_SPLITS = new HashMap<>();

    static {
        addVowel('ㅏ', 'ㅣ', 'ㅐ');
        addVowel('ㅑ', 'ㅣ', 'ㅒ');
        addVowel('ㅓ', 'ㅣ', 'ㅔ');
        addVowel('ㅕ', 'ㅣ', 'ㅖ');
        addVowel('ㅗ', 'ㅏ', 'ㅘ');
        addVowel('ㅗ', 'ㅐ', 'ㅙ');
        addVowel('ㅘ', 'ㅣ', 'ㅙ');
        addVowel('ㅗ', 'ㅣ', 'ㅚ');
        addVowel('ㅜ', 'ㅓ', 'ㅝ');
        addVowel('ㅜ', 'ㅔ', 'ㅞ');
        addVowel('ㅝ', 'ㅣ', 'ㅞ');
        addVowel('ㅜ', 'ㅣ', 'ㅟ');
        addVowel('ㅡ', 'ㅣ', 'ㅢ');

        VOWEL_BACKSPACE.put('ㅐ', 'ㅏ');
        VOWEL_BACKSPACE.put('ㅒ', 'ㅑ');
        VOWEL_BACKSPACE.put('ㅔ', 'ㅓ');
        VOWEL_BACKSPACE.put('ㅖ', 'ㅕ');
        VOWEL_BACKSPACE.put('ㅘ', 'ㅗ');
        VOWEL_BACKSPACE.put('ㅙ', 'ㅘ');
        VOWEL_BACKSPACE.put('ㅚ', 'ㅗ');
        VOWEL_BACKSPACE.put('ㅝ', 'ㅜ');
        VOWEL_BACKSPACE.put('ㅞ', 'ㅝ');
        VOWEL_BACKSPACE.put('ㅟ', 'ㅜ');
        VOWEL_BACKSPACE.put('ㅢ', 'ㅡ');

        addFinal('ㄱ', 'ㅅ', 'ㄳ');
        addFinal('ㄴ', 'ㅈ', 'ㄵ');
        addFinal('ㄴ', 'ㅎ', 'ㄶ');
        addFinal('ㄹ', 'ㄱ', 'ㄺ');
        addFinal('ㄹ', 'ㅁ', 'ㄻ');
        addFinal('ㄹ', 'ㅂ', 'ㄼ');
        addFinal('ㄹ', 'ㅅ', 'ㄽ');
        addFinal('ㄹ', 'ㅌ', 'ㄾ');
        addFinal('ㄹ', 'ㅍ', 'ㄿ');
        addFinal('ㄹ', 'ㅎ', 'ㅀ');
        addFinal('ㅂ', 'ㅅ', 'ㅄ');
    }

    private int initial = -1;
    private int medial = -1;
    private int fin;

    public Output input(char jamo) {
        int initialIndex = indexOf(INITIALS, jamo);
        int medialIndex = indexOf(MEDIALS, jamo);
        if (medialIndex >= 0) {
            return inputVowel(jamo, medialIndex);
        }
        if (initialIndex >= 0) {
            return inputConsonant(jamo, initialIndex);
        }
        throw new IllegalArgumentException("지원하지 않는 한글 자모: " + jamo);
    }

    public Output backspace() {
        if (fin > 0) {
            FinalSplit split = FINAL_SPLITS.get(FINALS[fin]);
            fin = split == null ? 0 : indexOf(FINALS, split.first);
        } else if (medial >= 0) {
            Character previous = VOWEL_BACKSPACE.get(MEDIALS[medial]);
            medial = previous == null ? -1 : indexOf(MEDIALS, previous);
        } else if (initial >= 0) {
            initial = -1;
        }
        return Output.composing(display());
    }

    public boolean hasComposition() {
        return initial >= 0 || medial >= 0;
    }

    public String getComposingText() {
        return display();
    }

    public String flush() {
        String value = display();
        reset();
        return value;
    }

    public void reset() {
        initial = -1;
        medial = -1;
        fin = 0;
    }

    private Output inputConsonant(char consonant, int consonantInitial) {
        String committed = "";
        if (medial < 0) {
            if (initial >= 0) {
                committed = display();
            }
            initial = consonantInitial;
            return new Output(committed, display());
        }

        if (initial < 0) {
            committed = display();
            reset();
            initial = consonantInitial;
            return new Output(committed, display());
        }

        int consonantFinal = indexOf(FINALS, consonant);
        if (fin == 0 && consonantFinal > 0) {
            fin = consonantFinal;
            return Output.composing(display());
        }

        if (fin > 0) {
            Character combined = COMPOUND_FINALS.get(pair(FINALS[fin], consonant));
            if (combined != null) {
                fin = indexOf(FINALS, combined);
                return Output.composing(display());
            }
        }

        committed = display();
        reset();
        initial = consonantInitial;
        return new Output(committed, display());
    }

    private Output inputVowel(char vowel, int vowelIndex) {
        if (medial < 0) {
            medial = vowelIndex;
            return Output.composing(display());
        }

        if (fin == 0) {
            Character combined = COMPOUND_VOWELS.get(pair(MEDIALS[medial], vowel));
            if (combined != null) {
                medial = indexOf(MEDIALS, combined);
                return Output.composing(display());
            }
            String committed = display();
            reset();
            medial = vowelIndex;
            return new Output(committed, display());
        }

        char finalCharacter = FINALS[fin];
        FinalSplit split = FINAL_SPLITS.get(finalCharacter);
        char nextInitial;
        if (split == null) {
            nextInitial = finalCharacter;
            fin = 0;
        } else {
            nextInitial = split.second;
            fin = indexOf(FINALS, split.first);
        }

        String committed = display();
        reset();
        initial = indexOf(INITIALS, nextInitial);
        medial = vowelIndex;
        return new Output(committed, display());
    }

    private String display() {
        if (initial >= 0 && medial >= 0) {
            char syllable = (char) (0xAC00 + ((initial * 21 + medial) * 28) + fin);
            return String.valueOf(syllable);
        }
        if (initial >= 0) {
            return String.valueOf(INITIALS[initial]);
        }
        if (medial >= 0) {
            return String.valueOf(MEDIALS[medial]);
        }
        return "";
    }

    private static void addVowel(char first, char second, char combined) {
        COMPOUND_VOWELS.put(pair(first, second), combined);
    }

    private static void addFinal(char first, char second, char combined) {
        COMPOUND_FINALS.put(pair(first, second), combined);
        FINAL_SPLITS.put(combined, new FinalSplit(first, second));
    }

    private static String pair(char first, char second) {
        return new String(new char[]{first, second});
    }

    private static int indexOf(char[] values, char target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == target) {
                return i;
            }
        }
        return -1;
    }

    public static final class Output {
        public final String committed;
        public final String composing;

        Output(String committed, String composing) {
            this.committed = committed;
            this.composing = composing;
        }

        static Output composing(String value) {
            return new Output("", value);
        }
    }

    private static final class FinalSplit {
        final char first;
        final char second;

        FinalSplit(char first, char second) {
            this.first = first;
            this.second = second;
        }
    }
}
