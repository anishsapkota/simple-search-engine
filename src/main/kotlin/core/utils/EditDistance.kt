package com.asap.core.utils

import kotlin.math.abs

class EditDistance {
    companion object {
        fun levenshtein(s1: String, s2: String, maxDistance: Int = Int.MAX_VALUE): Int {
            if (abs(s1.length - s2.length) > maxDistance) return maxDistance + 1

            val len1 = s1.length
            val len2 = s2.length

            if (len1 == 0) return len2
            if (len2 == 0) return len1

            var prevRow = IntArray(len2 + 1) { it }
            var currRow = IntArray(len2 + 1)

            for (i in 1..len1) {
                currRow[0] = i
                var minInRow = i

                for (j in 1..len2) {
                    val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                    currRow[j] = minOf(
                        currRow[j - 1] + 1,      // insertion
                        prevRow[j] + 1,          // deletion
                        prevRow[j - 1] + cost    // substitution
                    )
                    minInRow = minOf(minInRow, currRow[j])
                }

                // Early termination if minimum distance exceeds threshold
                if (minInRow > maxDistance) return maxDistance + 1

                val temp = prevRow
                prevRow = currRow
                currRow = temp
            }

            return prevRow[len2]
        }
    }
}