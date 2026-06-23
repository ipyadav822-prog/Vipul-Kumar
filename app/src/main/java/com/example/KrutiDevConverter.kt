package com.example

import java.util.regex.Pattern

object KrutiDevConverter {

    /**
     * Converts a Hindi Unicode string into Krutidev 010 encoding.
     */
    fun convertUnicodeToKrutiDev(unicodeString: String): String {
        if (unicodeString.isEmpty()) return ""

        var text = unicodeString

        // 1. Remove ZWNJ and ZWJ characters
        text = text.replace("\u200C", "")
        text = text.replace("\u200D", "")

        // 2. Map standard nukta characters and special compound signs
        text = text.replace("क़", "ñ")
        text = text.replace("ख़", "Q+")
        text = text.replace("ग़", "w+")
        text = text.replace("ज़", "e+")
        text = text.replace("ड़", "r+")
        text = text.replace("ढ़", "y+")
        text = text.replace("फ़", "v+")
        text = text.replace("य़", "p+")
        text = text.replace("ऱ", "Z+")
        text = text.replace("ऩ", "A+")

        // 3. Shift Chhoti Ee ('ि') vowel sign before its consonant cluster.
        // In Unicode, 'ि' is after the consonant cluster. In Krutidev, 'f' is typed before the cluster.
        // E.g., "स्थिति" (s-्-थ-ि-त-ि) should have 'ि' shifted before 'स्थ' and 'त'.
        text = shiftChhotiEeMatra(text)

        // 4. Shift Reph ('र्') ABOVE signs which appear before a consonant.
        // E.g. "धर्म" (dh-र्-m) -> "ध" + "म" + Reph character 'Z'.
        text = shiftRephAbove(text)

        // 5. Replace special compound conjuncts/ligatures first
        val specialConjuncts = listOf(
            "कृ" to "Ñ",
            "श्र" to "J",
            "ज्ञ" to "K",
            "त्र" to "=",
            "क्ष" to "{",
            "द्र" to "æ",
            "प्र" to "iz",
            "ग्र" to "xz",
            "ब्र" to "cz",
            "भ्र" to "Hz",
            "क्र" to "dz",
            "फ्र" to "oz",
            "स्र" to "sz",
            "ह्र" to "gz",
            "द्व" to "î",
            "द्य" to "\|",
            "द्ध" to "¼",
            "ष्ट" to "ष्v",
            "ष्ठ" to "ष्B",
            "द्ग" to "n~x",
            "द्द" to "n~n",
            "द्दि" to "fn~n",
            "द्दु" to "n~uq",
            "द्द्" to "n~"
        )
        for ((unicode, kruti) in specialConjuncts) {
            text = text.replace(unicode, kruti)
        }

        // 6. Map independent vowel characters
        val independentVowels = listOf(
            "औ" to "vkS",
            "ओ" to "vks",
            "ऐ" to "vS",
            "ए" to "v",
            "आ" to "vk",
            "अ" to "v",
            "इ" to "b",
            "ई" to "bZ",
            "उ" to "m",
            "ऊ" to "Å",
            "ऋ" to "_"
        )
        for ((unicode, kruti) in independentVowels) {
            text = text.replace(unicode, kruti)
        }

        // 7. Map half-consonants explicitly (before full consonants)
        val halfConsonants = listOf(
            "क्" to "D",
            "ख्" to "[",
            "ग्" to "x",
            "घ्" to "?",
            "च्" to "p",
            "ज्" to "t",
            "ण्" to "k",
            "त्" to "r",
            "थ्" to "F",
            "ध्" to "/",
            "न्" to "u",
            "प्" to "i",
            "ब्" to "c",
            "भ्" to "H",
            "म्" to "e",
            "य्" to ";",
            "ल्" to "y",
            "व्" to "o",
            "श्" to "\"",
            "ष्" to "'",
            "स्" to "s"
        )
        for ((unicode, kruti) in halfConsonants) {
            text = text.replace(unicode, kruti)
        }

        // 8. Map individual full consonants
        val fullConsonants = listOf(
            "क" to "d",
            "ख" to "[k",
            "ग" to "xk",
            "घ" to "?k",
            "ङ" to "U",
            "च" to "pk",
            "छ" to "N",
            "ज" to "tk",
            "झ" to ">",
            "ञ" to "}",
            "ट" to "v",
            "ठ" to "B",
            "ड" to "M",
            "ढ" to "<",
            "ण" to "kk",
            "त" to "rk",
            "थ" to "Fk",
            "द" to "n",
            "ध" to "/k",
            "न" to "uk",
            "प" to "ik",
            "फ" to "Q",
            "ब" to "ck",
            "भ" to "Hk",
            "म" to "ek",
            "य" to ";k",
            "र" to "j",
            "ल" to "yk",
            "व" to "ok",
            "श" to "\"k",
            "ष" to "'k",
            "स" to "sk",
            "ह" to "g"
        )
        for ((unicode, kruti) in fullConsonants) {
            text = text.replace(unicode, kruti)
        }

        // 9. Map dependent vowels (matras & other signs)
        val matras = listOf(
            "ा" to "k",
            "ि" to "f",
            "ी" to "h",
            "ु" to "q",
            "ू" to "w",
            "ृ" to "=",
            "े" to "s",
            "ै" to "S",
            "ो" to "ks",
            "ौ" to "kS",
            "ं" to "a",
            "ँ" to "W",
            "ः" to "%",
            "ाँ" to "kSm",
            "्" to "d"
        )
        for ((unicode, kruti) in matras) {
            text = text.replace(unicode, kruti)
        }

        return text
    }

    /**
     * Shifts the Chhoti Ee ('ि') vowel sign so it appears BEFORE its consonant cluster.
     */
    private fun shiftChhotiEeMatra(input: String): String {
        var text = input
        var pos = text.indexOf('ि')
        
        while (pos != -1) {
            // Find the cluster start before 'ि'
            var clusterStart = pos - 1
            if (clusterStart >= 0) {
                // If there's a half-consonant cluster preceding, we must scan backwards.
                // Suffix virama '्' (\u094d) indicates previous character is halved.
                while (clusterStart > 0) {
                    if (text[clusterStart] == '्') {
                        clusterStart -= 2 // skip consonant + virama
                    } else if (clusterStart > 1 && text[clusterStart - 1] == '्') {
                        clusterStart -= 2
                    } else {
                        break
                    }
                }
                if (clusterStart < 0) clusterStart = 0

                // Move 'ि' to before visual cluster start
                val partBefore = text.substring(0, clusterStart)
                val cluster = text.substring(clusterStart, pos)
                val partAfter = text.substring(pos + 1)
                
                // We write "ि + cluster", which translates later to "f + cluster_in_Kruti"
                text = partBefore + "ि" + cluster + partAfter
            }
            pos = text.indexOf('ि', pos + 1)
        }
        return text
    }

    /**
     * Shifts Reph ('र्') above signs so they appear AFTER the consonant cluster.
     * In Krutidev, Reph is represented by 'Z' placed *after* the letters in typing.
     */
    private fun shiftRephAbove(input: String): String {
        var text = input
        var pos = text.indexOf("र्")
        
        while (pos != -1) {
            if (pos + 2 < text.length) {
                // Find how far the cluster goes after "र्"
                // It should skip any full consonants or half consonants + vowels
                var clusterEnd = pos + 2 // first character of cluster
                
                // Scan forward to capture the full consonant cluster + matras
                while (clusterEnd < text.length) {
                    val c = text[clusterEnd]
                    // If we find a matra or nasal key, continue scanning
                    if (c == 'ा' || c == 'ि' || c == 'ी' || c == 'ु' || c == 'ू' || 
                        c == 'ृ' || c == 'े' || c == 'ै' || c == 'ो' || c == 'ौ' || 
                        c == 'ं' || c == 'ँ' || c == 'ः') {
                        clusterEnd++
                    } else if (c == '्' && clusterEnd + 1 < text.length) {
                        clusterEnd += 2 // Skip half letter
                    } else {
                        break
                    }
                }
                
                val partBefore = text.substring(0, pos)
                val cluster = text.substring(pos + 2, clusterEnd)
                val partAfter = text.substring(clusterEnd)
                
                // We move Reph to after the cluster, represented as "Z"
                text = partBefore + cluster + "Z" + partAfter
            }
            pos = text.indexOf("र्", pos + 1)
        }
        return text
    }
}
