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

    /*
    The RandomVideoGenerator will search for a random query, accumulate every result and put it into the video bucket.
    Games fetch videos from the video bucket. Videos will then be deleted from the bucket. A game cannot fetch from the
    same video query twice, the bucket will be regenerated if that would be the case.
     */

    // This is the video bucket
    private object VideoBucket {
        val videos = HashSet<Video>()
        val gamesThatFetched = HashSet<String>()
        fun clear() {
            videos.clear()
            gamesThatFetched.clear()
        }
    }

    // This is the random string generator which will generate random search queries
    val randomStringGenerator = RandomStringGenerator(SecureRandom())

    // Google API URLs
    val urlStart = "https://www.googleapis.com/youtube/v3/search?part=id&maxResults=50&type=video&videoSyndicated=true&q="
    val urlInfo = "https://www.googleapis.com/youtube/v3/videos?part=contentDetails,statistics,status&id="
    val key: String

    // Load up key file with API key
    init {
        val keyFile = File(filePrefix + "key")
        val scanner = Scanner(keyFile.inputStream())
        var keyBuffer = ""
        while (scanner.hasNextLine()) {
            keyBuffer += scanner.nextLine()
        }
        scanner.close()
        key = "&key=$keyBuffer"
    }

    // Amount of chars in search query
    val numGeneratedChars = 5

    // Behaviour is explained above
    fun fetchVideo(gameId: String): Video {
        with(VideoBucket) {
            if (!gamesThatFetched.contains(gameId) && !videos.isEmpty()) {
                gamesThatFetched.add(gameId)
                // "videos" is a HashSet, so "first" has no relevance to where in the API response the video was
                val v = videos.first()
                videos.remove(v)
                return v
            } else {
                regenerateVideos()
                return fetchVideo(gameId)
            }
        }
    }

    fun regenerateVideos() {
        VideoBucket.clear()
        while (VideoBucket.videos.isEmpty()) {

            // Look for videos using a random string and see if it has at least one video result
            var json: JSONObject? = null
            do {
                try {
                    val randomString = randomStringGenerator.randomString(numGeneratedChars)
                    json = grabResult(URL(urlStart + randomString + key))
                    if (json.getJSONArray("items").length() < 1) {
                        continue
                    }
                } catch (e: JSONException) {
                    // Keep on searching...
                    println("JSONException while looking for videos")
                    e.printStackTrace()
                }
            } while (json == null)

            // Fetch data of every video in response
            for (i in 0..json.getJSONArray("items").length() - 1) {
                try {
                    // Get ID
                    val foundVideo = json.getJSONArray("items").getJSONObject(i).getJSONObject("id").getString("videoId")

                    // Get info: contentDetails, statistics and status
                    val infoJSON = grabResult(URL(urlInfo + foundVideo + key)).getJSONArray("items").getJSONObject(0)

                    // Grab view count from statistics
                    val viewCount = infoJSON.getJSONObject("statistics").getString("viewCount").toInt()

                    // Grab duration from contentDetails
                    val contentDetails = infoJSON.getJSONObject("contentDetails")
                    val duration = contentDetails.getString("duration")

                    // If video is not embeddable, it won't be able to play on youtubeguessr
                    val embeddable = infoJSON.getJSONObject("status").getBoolean("embeddable")

                    // If video has region restrictions or licensed content, don't play it
                    if (!embeddable || contentDetails.has("regionRestriction") || contentDetails.getBoolean("licensedContent")) {
                        continue
                    }

                    VideoBucket.videos.add(Video(foundVideo, viewCount, iso8601toSeconds(duration)))
                } catch (e: Exception) {
                }
            }
        }
        println(VideoBucket.videos)
    }

    // Convert ISO 8601 (YT time standard) to seconds
    fun iso8601toSeconds(duration: String): Long {
        val d = Duration.parse(duration)
        val seconds = d.seconds
        return seconds
    }

    // Read from URL
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