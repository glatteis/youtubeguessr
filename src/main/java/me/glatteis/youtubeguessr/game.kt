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
class Game(val name: String, val id: String) {

    val users = HashSet<User>()
    val userSessions = HashMap<User, JSession>()
    val guesses = HashMap<User, Int>()

    fun getGame(request: Request, response: Response): ModelAndView {
        val username: String? = request.session().attribute("username")

        if (username == null) {
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
    var currentVideo = ""
    var currentVideoViews = 0

    fun message(message: JSONObject, session: JSession) {
        if (message.get("type") == "start") {
            postVideo()
        }
        if (message.get("type") == "guess") {
            if (!hasStarted || countdown == 0) return
            val viewsString = message.getString("views")
            if (viewsString.isBlank()) return
            val views = viewsString.toInt()
            for ((u, s) in userSessions) {
                if (s == session) {
                    guesses.put(u, views)
                }
            }
        }
    }

    fun onJoin(session: JSession) {
        if (hasStarted) {
            GameWebSocketHandler.sendMessage(session, JSONObject(
                    mapOf(
                            Pair("type", "newVideo"),
                            Pair("url", currentVideo)
                    )
            ))
        }
    }

    fun postVideo() {
        if (hasStarted) return
        hasStarted = true
        if (userSessions.isEmpty()) {
            games.remove(name)
            return
        }
        guesses.clear()
        val vPair = RandomVideoGenerator.generateRandomVideo()
        currentVideo = vPair.first
        currentVideoViews = vPair.second
        GameWebSocketHandler.sendToAll(userSessions.values, JSONObject(
                mapOf(
                        Pair("type", "newVideo"),
                        Pair("url", currentVideo)
                )
        ))
        countdown = 30
        timer(initialDelay = 1000, period = 1000) {
            countdown--
            GameWebSocketHandler.sendToAll(userSessions.values, JSONObject(
                    mapOf(
                            Pair("type", "updateCountdown"),
                            Pair("countdown", countdown)
                    )
            ))
            if (countdown == 0) {
                this.cancel()
                if (guesses.isEmpty()) {
                    postVideo()
                }
                var closestUser = guesses.keys.first()
                for (u in users) {
                    if (guesses[u] != null && abs(guesses[u]!! - currentVideoViews) <
                            abs(guesses[closestUser]!! - currentVideoViews)) {
                        closestUser = u
                    }
                }
                closestUser.points++
                val shownGuesses = HashMap<String, Int>()
                for ((u, g) in guesses) {
                    shownGuesses.put(u.name, g)
                }
                GameWebSocketHandler.sendToAll(userSessions.values, JSONObject(
                        mapOf(
                                Pair("type", "score"),
                                Pair("guesses", shownGuesses),
                                Pair("closestUser", closestUser.name),
                                Pair("viewcount", currentVideoViews),
                                Pair("users", users)
                        )
                ))
                Timer().schedule(10000) {
                    hasStarted = false
                    postVideo()
                }
            }
        }
    }

}

class User(val name: String, var points: Int)

@WebSocket
object GameWebSocketHandler {
    val sessions = ConcurrentHashMap<JSession, Pair<String, String>>()

    @OnWebSocketClose
    fun onClose(session: JSession, statusCode: Int, reason: String) {
        val users = games[sessions[session]?.first]?.users
        if (users != null) {
            users
                    .filter { it.name == sessions[session]?.second }
                    .forEach { users.remove(it) }
        }
        sessions.remove(session)
    }

    @OnWebSocketMessage
    fun onMessage(session: JSession, message: String) {
        val jsonObject = JSONObject(message)
        if (jsonObject["type"] == "connect") {
            var username = jsonObject["username"] as String
            val id = jsonObject["id"] as String
            sessions.put(session, Pair(id, username))
            val game = games[id] ?: return
            val users = game.users
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
            users.add(user)
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
        for (s in sessions) {
            GameWebSocketHandler.sendMessage(s, message)
        }
    }

}