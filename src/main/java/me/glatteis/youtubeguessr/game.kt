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
class Game(val name: String, val id: String, val countdownTime: Int) {

    //todo game isn't disposed of correctly

    val userSessions = ConcurrentHashMap<User, JSession>()
    val guesses = ConcurrentHashMap<User, Int>()

    init {
        timer(daemon = true, initialDelay = 10000, period = 10000) {
            val toRemove = HashSet<User>()
            for ((u, s) in userSessions) {
                if (!s.isOpen) {
                    toRemove.add(u)
                }
            }
            for (u in toRemove) {
                userSessions[u]?.close()
                userSessions.remove(u)
            }
            if (userSessions.isEmpty()) {
                println("Removing game!")
                games.remove(id)
                this.cancel()
            } else {
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

    fun getGame(request: Request, response: Response): ModelAndView {
        val username: String? = request.session().attribute("username")

        if (username == null || username.isBlank()) {
            response.redirect("/choose_name?id=" + id)
        }

        val attributes = mapOf(
                Pair("username", username),
                Pair("game_name", name),
                Pair("game_id", id)
        )

        request.session().attribute("redirect_id", id)

        return ModelAndView(attributes, "game.html")
    }

    var hasStarted = false
    var countdown = 0
    var currentVideo = Video("", 0, 0)
    val readyUsers = ArrayList<User>()

    fun message(message: JSONObject, session: JSession) {
        if (message.get("type") == "start") {
            postVideo()
        } else if (message.get("type") == "guess") {
            if (!hasStarted || countdown == 0) return
            val viewsString = message.getString("views")
            if (viewsString.isBlank()) return
            if (viewsString.length >= Int.MAX_VALUE.toString().length) return
            val views: Int
            try {
                views = viewsString.toInt()
            } catch (e: Exception) {
                return
            }
            if (views < 0) return
            for ((u, s) in userSessions) {
                if (s == session) {
                    guesses.put(u, views)
                }
            }
        } else if (message.get("type") == "chatMessage") {
            var user: User? = null
            for ((u, s) in userSessions) {
                if (s == session) {
                    user = u
                    break
                }
            }
            user ?: return
            val chatMessage = message.get("message") ?: return
            if (chatMessage !is String || chatMessage.isBlank()) return
            GameWebSocketHandler.sendToAll(userSessions.values, JSONObject(
                    mapOf(
                            Pair("type", "chatMessage"),
                            Pair("message", chatMessage),
                            Pair("senderName", user.name)
                    )
            ))
        } else if (message.get("type") == "videoBuffered") {
            if (countdown <= countdownTime) {
                GameWebSocketHandler.sendMessage(session, JSONObject(
                        mapOf(
                                Pair("type", "startVideo"),
                                Pair("at", countdownTime - countdown)
                        )
                ))
            }
            var user: User? = null
            for ((u, s) in userSessions) {
                if (s == session) {
                    user = u
                    break
                }
            }
            user ?: return
            if (!readyUsers.contains(user)) {
                readyUsers.add(user)
            }
            if (readyUsers.size >= userSessions.size) {
                countdown = countdownTime
                readyUsers.clear()
                GameWebSocketHandler.sendToAll(userSessions.values, JSONObject(
                        mapOf(
                                Pair("type", "startVideo"),
                                Pair("at", 0)
                        )
                ))
            }
        }
    }

    fun onJoin(session: JSession) {
        if (hasStarted) {
            GameWebSocketHandler.sendMessage(session, JSONObject(
                    mapOf(
                            Pair("type", "newVideo"),
                            Pair("url", currentVideo.id),
                            Pair("duration", currentVideo.duration)
                    )
            ))
        }
    }

    fun postVideo() {
        if (hasStarted) return
        hasStarted = true
        guesses.clear()
        currentVideo = RandomVideoGenerator.generateRandomVideo()
        GameWebSocketHandler.sendToAll(userSessions.values, JSONObject(
                mapOf(
                        Pair("type", "newVideo"),
                        Pair("url", currentVideo.id),
                        Pair("duration", currentVideo.duration),
                        Pair("users", userSessions.keys)
                )
        ))
        countdown = countdownTime + 5
        timer(initialDelay = 1000, period = 1000, daemon = true) {
            countdown--
            if (countdown > countdownTime) return@timer
            GameWebSocketHandler.sendToAll(userSessions.values, JSONObject(
                    mapOf(
                            Pair("type", "updateCountdown"),
                            Pair("countdown", countdown)
                    )
            ))
            if (countdown == 0) {
                this.cancel()
                if (guesses.isEmpty()) {
                    hasStarted = false
                    if (!games.contains(this@Game)) {
                        this@timer.cancel()
                    } else {
                        postVideo()
                    }
                    return@timer
                }
                val closestUsers = mutableListOf(guesses.keys.first())
                for (u in userSessions.keys) {
                    if (guesses[u] == null || closestUsers.contains(u)) continue
                    val g1 = abs(guesses[u]!! - currentVideo.views)
                    val g2 = abs(guesses[closestUsers[0]]!! - currentVideo.views)
                    if (g1 < g2) {
                        closestUsers.clear()
                        closestUsers.add(u)
                    } else if (g1 == g2) {
                        closestUsers.add(u)
                    }
                }
                for (c in closestUsers) {
                    c.points++
                }
                val shownGuesses = HashMap<String, Int>()
                for ((u, g) in guesses) {
                    shownGuesses.put(u.name, g)
                }
                val closestUsernames = Array(closestUsers.size) {
                    closestUsers[it].name
                }
                GameWebSocketHandler.sendToAll(userSessions.values, JSONObject(
                        mapOf(
                                Pair("type", "score"),
                                Pair("guesses", shownGuesses),
                                Pair("closestUsers", closestUsernames),
                                Pair("viewcount", currentVideo.views),
                                Pair("users", userSessions.keys)
                        )
                ))
                Timer().schedule(10000) {
                    hasStarted = false
                    if (!games.contains(this@Game)) {
                        this@timer.cancel()
                    } else {
                        postVideo()
                    }
                }
            }
        }
    }
}

class User(val name: String, var points: Int)
class Video(val id: String, val views: Int, val duration: Long)

@WebSocket
object GameWebSocketHandler {
    val sessions = ConcurrentHashMap<JSession, Pair<String, String>>()

    @OnWebSocketClose
    fun onClose(session: JSession, statusCode: Int, reason: String) {
        val users = games[sessions[session]?.first]?.userSessions?.keys
        if (users != null) {
            users
                    .filter { it.name == sessions[session]?.second }
                    .forEach {
                        users.remove(it)
                        games[sessions[session]?.first]?.readyUsers?.remove(it)
                    }
        }
        sessions.remove(session)

        sendToAll(sessions.keys, JSONObject(
                mapOf(
                        Pair("users", users)
                )
        ))
    }

    @OnWebSocketMessage
    fun onMessage(session: JSession, message: String) {
        val jsonObject = JSONObject(message)
        if (jsonObject["type"] == "connect") {
            var username = jsonObject["username"] as String
            val id = jsonObject["id"] as String
            sessions.put(session, Pair(id, username))
            val game = games[id] ?: return
            val users = game.userSessions.keys
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
                            points = u.points
                        }
                    }
                }
            } while (usernameBefore != username)
            for (u in usersToRemove) {
                users.remove(u)
            }
            val user = User(username, points)
            game.userSessions.put(user, session)
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
            val game = games[sessions[session]?.first]
            game ?: return
            game.message(jsonObject, session)
        }
    }

    fun sendMessage(session: JSession, message: JSONObject) {
        if (!session.isOpen) {
            val userSessions = games[sessions[session]?.first]?.userSessions
            if (userSessions != null) {
                for ((u, s) in userSessions) {
                    if (s == session) {
                        userSessions.remove(u)
                        break
                    }
                }
            }
            sessions.remove(session)
            return
        }
        session.remote.sendString(message.toString())
    }

    fun sendToAll(sessions: Iterable<JSession>, message: JSONObject) {
        sessions.forEach {
            GameWebSocketHandler.sendMessage(it, message)
        }
    }

}