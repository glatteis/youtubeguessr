package me.glatteis.youtubeguessr

import java.security.SecureRandom
import java.util.*

/**
 * Created by Linus on 19.03.2017!
 */
object RandomStringGenerator {

    private val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray()
    private val rnd = SecureRandom()

    fun randomString(len: Int, random: Random = rnd): String {
        val sb = StringBuilder(len)
        for (i in 0..(len - 1))
            sb.append(alphabet[rnd.nextInt(alphabet.size)])
        return sb.toString()
    }

}