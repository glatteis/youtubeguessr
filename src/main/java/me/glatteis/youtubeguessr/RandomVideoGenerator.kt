package me.glatteis.youtubeguessr

import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader
import java.net.URL
import java.security.SecureRandom
import java.time.Duration
import java.util.*


/**
 * Created by Linus on 19.03.2017!
 */

object RandomVideoGenerator {

    private object VideoBucket {
        val videos = HashSet<Video>()
        val gamesThatFetched = HashSet<String>()
        fun clear() {
            videos.clear()
            gamesThatFetched.clear()
        }
    }

    val urlStart = "https://www.googleapis.com/youtube/v3/search?part=id&maxResults=50&type=video&videoSyndicated=true&q="
    val urlViews = "https://www.googleapis.com/youtube/v3/videos?part=statistics&id="
    val urlDuration = "https://www.googleapis.com/youtube/v3/videos?part=contentDetails&id="
    val key: String

    init {
        val keyFile = File("key")
        val scanner = Scanner(keyFile.inputStream())
        var keyBuffer = ""
        while (scanner.hasNextLine()) {
            keyBuffer += scanner.nextLine()
        }
        scanner.close()
        key = "&key=$keyBuffer"
    }

    val random = SecureRandom()

    val numGeneratedChars = 5

    fun fetchVideo(gameId: String): Video {
        with(VideoBucket) {
            if (!gamesThatFetched.contains(gameId) && !videos.isEmpty()) {
                gamesThatFetched.add(gameId)
                val v = videos.first()
                videos.remove(v)
                return v
            } else {
                generateVideos()
                return fetchVideo(gameId)
            }
        }
    }

    fun generateVideos() {
        VideoBucket.clear()

        while (VideoBucket.videos.isEmpty()) {
            var json: JSONObject? = null
            do {
                try {
                    val randomString = RandomStringGenerator.randomString(numGeneratedChars, random = random)
                    json = grabResult(URL(urlStart + randomString + key))
                    if (json.getJSONArray("items").length() < 1) {
                        continue
                    }
                } catch (e: JSONException) {
                    //
                }
            } while (json == null)



            for (i in 0..json.getJSONArray("items").length() - 1) {
                try {
                    val foundVideo = json.getJSONArray("items").getJSONObject(i).getJSONObject("id").getString("videoId")

                    val jsonV = grabResult(URL(urlViews + foundVideo + key))
                    val viewCount = jsonV.getJSONArray("items").getJSONObject(0).getJSONObject("statistics").getString("viewCount").toInt()

                    val jsonD = grabResult(URL(urlDuration + foundVideo + key))
                    val contentDetails = jsonD.getJSONArray("items").getJSONObject(0).getJSONObject("contentDetails")
                    val duration = contentDetails.getString("duration")
                    if (contentDetails.has("regionRestriction") || contentDetails.getBoolean("licensedContent")) {
                        continue
                    }

                    VideoBucket.videos.add(Video(foundVideo, viewCount, iso8601toSeconds(duration)))
                } catch (e: Exception) {
                }
            }
        }
        println(VideoBucket.videos)
    }

    fun iso8601toSeconds(duration: String): Long {
        val d = Duration.parse(duration)
        val seconds = d.seconds
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