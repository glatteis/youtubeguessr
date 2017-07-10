package me.glatteis.youtubeguessr

import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage
import org.eclipse.jetty.websocket.api.annotations.WebSocket
import org.json.JSONObject
import spark.ModelAndView
import spark.Request
import spark.Response
import java.lang.Math.abs
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.schedule
import kotlin.concurrent.timer
import org.eclipse.jetty.websocket.api.Session as JSession

/**
 * Created by Linus on 19.03.2017!
 */
class Game(val name: String, val id: String, val countdownTime: Int, val isChillMode: Boolean, val winPoints: Int?,
           val isPublic: Boolean, val pointsForExactAnswers: Int) {

    /*
    An instance of this class represents a running game.

    Do differentiate it from a Spark Session, the WebSocket session is imported as "JSession"
     */

    // Reference to WebSocket sessions from User
    val userSessions = ConcurrentHashMap<User, JSession>()

    fun getUserBySession(session: JSession): User? {
        for ((u, s) in userSessions) {
            if (session == s) {
                return u
            }
        }
        return null
    }

    val guesses = ConcurrentHashMap<User, Int>()

    init {
        // Timer that looks for closed sockets every 10 seconds
        timer(daemon = true, initialDelay = 10000, period = 10000) {
            //Close & remove all sessions that are not open anymore
            val toRemove = HashSet<Pair<User, JSession>>()
            for ((u, s) in userSessions) {
                if (!s.isOpen) {
                    toRemove.add(Pair(u, s))
                }
            }
            for ((u, s) in toRemove) {
                println("Removing $u")
                if (s.isOpen) s.close()
                userSessions.remove(u)
                println(userSessions.keys)
                guesses.remove(u)
                readyUsers.remove(u)
            }

            if (userSessions.isEmpty()) {
                // If there are no users anymore, remove the game from games and cancel this timer
                games.remove(id)
                this.cancel()
            } else if (toRemove.isNotEmpty()) {
                // Else, update everyone with the remaining set of users
                for ((u, s) in userSessions.entries) {
                    GameWebSocketHandler.sendMessage(s, JSONObject(
                            mapOf(
                                    Pair("users", userSessions.keys),
                                    Pair("username", u.name)
                            )
                    ))
                }
            }
        }
    }

    // This function is called whenever requests the response from /game with id of this game
    fun getGame(request: Request, response: Response): ModelAndView {
        // If the requesting player does not yet have a username, send to choose_username
        val username: String? = request.session().attribute("username")
        if (username == null || username.isBlank()) {
            response.redirect("/choose_name?id=" + id)
        }
        // Add attributes to HTML
        val attributes = mapOf(
                Pair("username", username),
                Pair("game_name", name),
                Pair("game_id", id),
                Pair("points_to_win", winPoints),
                Pair("chillMode", isChillMode),
                Pair("isPublic", isPublic)
        )
        request.session().attribute("redirect_id", id)
        return ModelAndView(attributes, filePrefix + "game.html")
    }

    // If video is playing
    var isVideoPlaying = false
    // Countdown to end of guess time
    var countdown = 0
    // Currently watched video (default is empty video)
    var currentVideo = Video("", 0, 0)

    // This is for keeping track of users that have finished buffering the video
    val readyUsers = ArrayList<User>()

    fun message(message: JSONObject, session: JSession) {
        if (message.get("type") == "start") {
            // Someone has pressed the start button
            postVideo()
        } else if (message.get("type") == "guess") {
            // Someone has guessed
            if (!isVideoPlaying || countdown == 0) return

            // Parse number from string "views"
            val viewsString = message.getString("views")
            if (viewsString.isBlank()) return
            val views: Int
            try {
                views = viewsString.toInt()
            } catch (e: Exception) {
                return
            }
            if (views < 0) return

            // Put into guesses map
            val user = getUserBySession(session) ?: return
            guesses.put(user, views)
            user.hasGuessed = true

            // Update users
            GameWebSocketHandler.sendToAll(userSessions.values, JSONObject(
                    mapOf(
                            Pair("users", userSessions.keys)
                    )
            ))
        } else if (message.get("type") == "chatMessage") {
            // Scoop out user, message
            val user = getUserBySession(session) ?: return
            val chatMessage = message.get("message") ?: return
            if (chatMessage !is String || chatMessage.isBlank()) return

            //Send message to every user
            GameWebSocketHandler.sendToAll(userSessions.values, JSONObject(
                    mapOf(
                            Pair("type", "chatMessage"),
                            Pair("message", chatMessage),
                            Pair("senderName", user.name)
                    )
            ))
        } else if (message.get("type") == "videoBuffered") {
            // Someone's video has buffered

            // If video playback has already started, start video where it should be
            if (countdown <= countdownTime) {
                GameWebSocketHandler.sendMessage(session, JSONObject(
                        mapOf(
                                Pair("type", "startVideo"),
                                Pair("at", countdownTime - countdown)
                        )
                ))
            } else {
                // If video playback has not yet started
                val user = getUserBySession(session) ?: return

                // Add user to users that are ready
                if (!readyUsers.contains(user)) {
                    readyUsers.add(user)
                }

                // If all users have buffered and time is still in buffering time, start video for everyone
                // Starting when countdown == countdownTime is handled in postVideo
                if (readyUsers.size >= userSessions.size && countdown > countdownTime) {
                    countdown = countdownTime
                    GameWebSocketHandler.sendToAll(userSessions.values, JSONObject(
                            mapOf(
                                    Pair("type", "startVideo"),
                                    Pair("at", 0)
                            )
                    ))
                }
            }
        }
    }

    // If someone joins with their WebSocket and video is playing, send them current video at current time
    fun onJoin(session: JSession) {
        if (isVideoPlaying) {
            GameWebSocketHandler.sendMessage(session, JSONObject(
                    mapOf(
                            Pair("type", "newVideo"),
                            Pair("url", currentVideo.id),
                            Pair("duration", currentVideo.duration)
                    )
            ))
            if (countdownTime >= countdown) {
                GameWebSocketHandler.sendMessage(session, JSONObject(
                        mapOf(
                                Pair("type", "startVideo"),
                                Pair("at", countdownTime - countdown)
                        )
                ))
            }
        }
    }

    // Chooses and sends new video
    fun postVideo() {
        // If a video is already playing, don't play a video
        if (isVideoPlaying) return
        isVideoPlaying = true
        // Clear previous guesses
        guesses.clear()
        for ((u, _) in userSessions) {
            u.hasGuessed = false
        }
        GameWebSocketHandler.sendToAll(userSessions.values, JSONObject(
                mapOf(
                        Pair("users", userSessions.keys)
                )
        ))
        // Clear ready users
        readyUsers.clear()
        // Get a video from the video generator
        currentVideo = RandomVideoGenerator.fetchVideo(id)
        // Send new video to everyone
        GameWebSocketHandler.sendToAll(userSessions.values, JSONObject(
                mapOf(
                        Pair("type", "newVideo"),
                        Pair("url", currentVideo.id),
                        Pair("duration", currentVideo.duration),
                        Pair("users", userSessions.keys)
                )
        ))
        // Set the countdown. The additional 5s are for buffer time compensation
        countdown = countdownTime + 5
        timer(initialDelay = 1000, period = 1000, daemon = true) {
            // Do every second

            // Decrease timer
            countdown--

            // If it's still buffer compensation, don't do anything
            if (countdown > countdownTime) return@timer

            // If buffer compensation is over, play video for everyone that has buffered
            if (countdown == countdownTime) {
                GameWebSocketHandler.sendToAll(userSessions.values,
                        JSONObject(
                                mapOf(
                                        Pair("type", "startVideo"),
                                        Pair("at", 0)
                                )
                        ))
            }

            if (isChillMode) {
                // Set countdown blank
                GameWebSocketHandler.sendToAll(userSessions.values, JSONObject(
                        mapOf(
                                Pair("type", "updateCountdown"),
                                Pair("countdown", "")
                        )
                ))
            } else {
                // Update countdown for everyone
                GameWebSocketHandler.sendToAll(userSessions.values, JSONObject(
                        mapOf(
                                Pair("type", "updateCountdown"),
                                Pair("countdown", countdown)
                        )
                ))
            }

            // Chill Mode Self Delete
            if (isChillMode && countdown <= -1000) {
                this.cancel()
                games.remove(id)
            }

            if ((!isChillMode && countdown == 0) || guesses.size >= userSessions.size) {
                // Time is over
                // Cancel timer
                this.cancel()

                for ((u, _) in userSessions) {
                    if (!guesses.containsKey(u)) {
                        u.afk++
                        val session = userSessions[u] ?: continue
                        if (u.afk == 4) {
                            GameWebSocketHandler.onClose(session, 0, "")
                            GameWebSocketHandler.sendMessage(session, JSONObject(
                                    mapOf(
                                            Pair("type", "newVideo"),
                                            Pair("url", ""),
                                            Pair("duration", 0)
                                    )
                            ))
                        }
                        if (!session.isOpen) continue
                        GameWebSocketHandler.sendMessage(session, JSONObject(
                                mapOf(
                                        Pair("type", "chatMessage"),
                                        if (u.afk == 4) {
                                            Pair("message", "You were kicked from the game because you were AFK for too" +
                                                    "long.")
                                        } else {
                                            Pair("message", "You did not vote this round. If you are AFK for ${4 - u.afk} " +
                                                    "more round(s), you will be kicked from the game.")
                                        },
                                        Pair("senderName", "youtubeguessr")
                                )
                        ))
                        if (u.afk == 4) {
                            GameWebSocketHandler.sendMessage(session, JSONObject(
                                    mapOf(
                                            Pair("type", "kick ")
                                    )
                            ))
                        }
                    }

                }

                // If guesses are empty, continue with playing new video
                if (guesses.isEmpty()) {
                    isVideoPlaying = false
                    // If this game still exists, post a new video
                    if (games.contains(this@Game)) {
                        postVideo()
                    }
                    return@timer
                }
                // Guesses are not empty, so let's calculate our winners

                // These is the list of winners, first of all it will just be the first person
                // By definition, everyone in this list will have the same distance to the actual view count
                val closestUsers = mutableListOf(guesses.keys.first())
                // Go through every user
                for ((u, g) in guesses) {
                    // If this is the first iteration, continue
                    if (u == closestUsers[0]) continue
                    // Calculate distance to actual view count
                    val g1 = abs(g - currentVideo.views)
                    val g2 = abs(guesses[closestUsers[0]]!! - currentVideo.views)
                    if (g1 < g2) {
                        // If distance of this user is closer than closest user yet, this is the only closest user
                        closestUsers.clear()
                        closestUsers.add(u)
                    } else if (g1 == g2) {
                        // If distance of this user is the same as closest user yet, this user is also the closest
                        closestUsers.add(u)
                    }
                }
                // Give points to closest users
                var awardedPoints = 1
                if (guesses[closestUsers[0]]!! == currentVideo.views) {
                    // If view count is exactly right, award more points
                    awardedPoints = pointsForExactAnswers
                }
                for (c in closestUsers) {
                    c.points += awardedPoints
                }

                // Prepare data for display
                val shownGuesses = HashMap<String, Int>()
                for ((u, g) in guesses) {
                    shownGuesses.put(u.name, g)
                }
                val closestUsernames = Array(closestUsers.size) {
                    closestUsers[it].name
                }

                // Send results to everyone
                GameWebSocketHandler.sendToAll(userSessions.values, JSONObject(
                        mapOf(
                                Pair("type", "score"),
                                Pair("guesses", shownGuesses),
                                Pair("closestUsers", closestUsernames),
                                Pair("viewcount", currentVideo.views),
                                Pair("users", userSessions.keys)
                        )
                ))

                // Check for wins if winning is enabled
                if (winPoints != null) {
                    // Scoop up winners
                    val winners = ArrayList<String>()
                    for (u in userSessions.keys) {
                        if (u.points >= winPoints) {
                            winners.add(u.name)
                        }
                    }
                    if (winners.isNotEmpty()) {
                        // Send game end packet
                        GameWebSocketHandler.sendToAll(userSessions.values, JSONObject(
                                mapOf(
                                        Pair("type", "gameEnd"),
                                        Pair("winners", winners)
                                )
                        ))
                        // Commit sudoku
                        games.remove(id)
                        this.cancel()
                        return@timer
                    }
                }

                // In 10 seconds, play next video
                if (games.contains(this@Game)) {
                    Timer().schedule(10000) {
                        isVideoPlaying = false
                        postVideo()
                    }
                }
            }
        }
    }
}


// Handles all WebSocket. There is only one instance per server
@WebSocket
object GameWebSocketHandler {

    // Connection between username and game ID as used in sessions
    private data class UserSession(val gameId: String, val username: String)

    // Links WebSocket session to game & user
    private val sessions = ConcurrentHashMap<JSession, UserSession>()

    @Suppress("UNUSED_PARAMETER")
    @OnWebSocketClose
    fun onClose(session: JSession, a: Int, b: String) {
        // Get data of that session
        val userSession = sessions[session] ?: return
        val game = games[userSession.gameId] ?: return
        val users = game.userSessions.keys
        val user = game.getUserBySession(session) ?: return
        // Remove from users in game, guesses in game, global sessions
        users.remove(user)
        game.guesses.remove(user)
        sessions.remove(session)

        // Send user update to all users in game
        sendToAll(game.userSessions.values, JSONObject(
                mapOf(
                        Pair("users", users)
                )
        ))
    }

    @OnWebSocketMessage
    fun onMessage(session: JSession, message: String) {
        // Parse message in JSON
        val jsonObject = JSONObject(message)
        // Session wants to connect
        if (jsonObject["type"] == "connect") {
            // Get game ID and username
            var username = (jsonObject["username"] ?: return) as String
            val id = (jsonObject["id"] ?: return) as String
            // Put into sessions map
            sessions.put(session, UserSession(id, username))
            val game = games[id] ?: return
            val users = game.userSessions.keys
            // If this user was already online before and has simply reloaded the page,
            // transfer their points and keep their old name, remove old user
            var points = 0
            val usersToRemove = HashSet<User>()
            do {
                val usernameBefore = username
                for (u in users) {
                    if (u.name == username) {
                        if ((game.userSessions[u]?.isOpen) ?: false) {
                            username += "~"
                            break
                        } else {
                            usersToRemove.add(u)
                            game.userSessions[u]?.close()
                            game.userSessions.remove(u)
                            game.guesses.remove(u)
                            points = u.points
                        }
                    }
                }
            } while (usernameBefore != username)
            // Remove users later to prevent concurrent modification
            for (u in usersToRemove) {
                users.remove(u)
            }
            // Create user
            val user = User(username, points)
            game.userSessions.put(user, session)
            // Update user table
            for ((u, s) in game.userSessions.entries) {
                sendMessage(s, JSONObject(
                        mapOf(
                                Pair("users", users),
                                Pair("username", u.name)
                        )
                ))
            }
            game.onJoin(session)
        } else {
            // If message is not "connect", send it for the game to handle
            val gameId = sessions[session]?.gameId ?: return
            val game = games[gameId] ?: return
            game.message(jsonObject, session)
        }
    }

    fun sendMessage(session: JSession, message: JSONObject) {
        if (session.isOpen) {
            session.remote.sendString(message.toString())
        }

    }

    fun sendToAll(sessions: Iterable<JSession>, message: JSONObject) {
        sessions.forEach {
            sendMessage(it, message)
        }
    }

}