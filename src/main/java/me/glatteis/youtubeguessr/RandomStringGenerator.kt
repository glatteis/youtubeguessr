package me.glatteis.youtubeguessr

import java.security.SecureRandom
import java.util.*

/**
 * Created by Linus on 19.03.2017!
 */
class RandomStringGenerator(val random: Random) {

    // Characters to use in search query
    private val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray()

    fun randomString(len: Int): String {
        val sb = StringBuilder(len)
        for (i in 0..(len - 1))
            sb.append(alphabet[random.nextInt(alphabet.size)])
        return sb.toString()
    }

}