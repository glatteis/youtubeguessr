package me.glatteis.youtubeguessr

import org.json.JSONException
import org.json.JSONObject
import java.io.InputStreamReader
import java.net.URL
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import javax.xml.datatype.DatatypeFactory


/**
 * Created by Linus on 19.03.2017!
 */
object RandomVideoGenerator {

    val urlStart = "https://www.googleapis.com/youtube/v3/search?part=id&maxResults=50&type=video&videoSyndicated=true&q="
    val urlViews = "https://www.googleapis.com/youtube/v3/videos?part=statistics&id="
    val urlDuration = "https://www.googleapis.com/youtube/v3/videos?part=contentDetails&id="
    val key = "&key=AIzaSyCvAthOGvbUcWN6NfH8Oj4awnEvbtKk0Js"

    val random = SecureRandom()

    val numGeneratedChars = 5

    fun generateRandomVideo(): Video {
        var foundVideo: String?
        var viewCount: Int?
        var duration: String?
        do {
            println("Generating...")
            foundVideo = null
            viewCount = null
            duration = null
            try {
                val randomString = RandomStringGenerator.randomString(numGeneratedChars, random = random)
                val json = grabResult(URL(urlStart + randomString + key))
                if (json.getJSONArray("items").length() < 1) {
                    continue
                }
                foundVideo = json.getJSONArray("items").getJSONObject(
                        random.nextInt(json.getJSONArray("items").length())
                ).getJSONObject("id").getString("videoId")

                val jsonV = grabResult(URL(urlViews + foundVideo + key))
                viewCount = jsonV.getJSONArray("items").getJSONObject(0).getJSONObject("statistics").getString("viewCount").toInt()

                val jsonD = grabResult(URL(urlDuration + foundVideo + key))
                println(jsonD)
                duration = jsonD.getJSONArray("items").getJSONObject(0).getJSONObject("contentDetails").getString("duration")
            } catch (e: JSONException) {
                //
            }
        } while (foundVideo == null || viewCount == null || duration == null)
        return Video(foundVideo, viewCount, iso8601toSeconds(duration))
    }

    fun iso8601toSeconds(duration: String): Long {
        print(duration)
        val d = Duration.parse(duration)
        val seconds = d.seconds
        println(seconds)
        return seconds
    }

    fun grabResult(url: URL): JSONObject {
        val urlConnection = url.openConnection()
        val reader = Scanner(
                InputStreamReader(urlConnection.getInputStream()))
        var text = ""
        while (reader.hasNextLine()) {
            text += reader.nextLine()
        }
        reader.close()
        return JSONObject(text)
    }

}