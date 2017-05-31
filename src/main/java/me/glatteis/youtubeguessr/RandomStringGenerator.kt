package me.glatteis.youtubeguessr

import java.util.*

/**
 * Created by Linus on 19.03.2017!
 */
class RandomStringGenerator {

    // Characters to use in search query
    private val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray()

    fun randomString(len: Int, random: Random): String {
        val sb = StringBuilder(len)
        for (i in 0..(len - 1))
            sb.append(alphabet[random.nextInt(alphabet.size)])
        return sb.toString()
    }

}