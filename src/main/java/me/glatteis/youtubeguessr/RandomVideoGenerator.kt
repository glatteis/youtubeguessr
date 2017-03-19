package me.glatteis.youtubeguessr

import jdk.nashorn.internal.parser.JSONParser
import jdk.nashorn.internal.runtime.ScriptingFunctions.readLine
import org.json.JSONObject
import java.io.InputStreamReader
import java.io.BufferedReader
import java.net.URL
import java.util.*


/**
 * Created by Linus on 19.03.2017!
 */
object RandomVideoGenerator {

    val urlStart = "https://www.googleapis.com/youtube/v3/search?part=id&maxResults=1&type=video&q="
    val urlViews = "https://www.googleapis.com/youtube/v3/videos?part=statistics&id="
    val key = "&key=AIzaSyCvAthOGvbUcWN6NfH8Oj4awnEvbtKk0Js"
    
    fun generateRandomVideo(): Pair<String, Int> {
        var foundVideo: String? = null
        var viewCount: Int? = null
        do {
            val randomString = RandomStringGenerator.randomString(4)
            val url = URL(urlStart + randomString + key)
            val urlConnection = url.openConnection()
            val reader = Scanner(
                    InputStreamReader(urlConnection.getInputStream()))
            var text = ""
            while (reader.hasNextLine()) {
                text += reader.nextLine()
            }
            reader.close()
            val json = JSONObject(text)
            foundVideo = json.getJSONArray("items")?.getJSONObject(0)?.getJSONObject("id")?.getString("videoId")

            val urlV = URL(urlViews + foundVideo + key)
            val urlConnectionV = urlV.openConnection()
            val readerV = Scanner(
                    InputStreamReader(urlConnectionV.getInputStream()))
            var textV = ""
            while (readerV.hasNextLine()) {
                textV += readerV.nextLine()
            }
            readerV.close()
            val jsonV = JSONObject(textV)
            viewCount = jsonV.getJSONArray("items")?.getJSONObject(0)?.getJSONObject("statistics")?.getString("viewCount")?.toInt()
            
        } while (foundVideo == null || viewCount == null)
        return Pair(foundVideo, viewCount)
    }

}