package me.glatteis.youtubeguessr

import spark.ModelAndView
import spark.Spark.*
import spark.template.mustache.MustacheTemplateEngine
import java.util.*
import java.util.concurrent.ConcurrentHashMap

val games = ConcurrentHashMap<String, Game>()
val mustacheTemplateEngine = MustacheTemplateEngine()

fun main(args: Array<String>) {
    if (args.isEmpty()) throw IllegalArgumentException("Port has to be specified.")
    val portAsString = args[0]
    val port = portAsString.toInt()
    port(port)
    webSocket("/gamesocket", GameWebSocketHandler)
    staticFileLocation("/")
    get("/", { request, response ->
        val attributes = HashMap<String, Any>()
        ModelAndView(attributes, "create_game.html")
    }, mustacheTemplateEngine)
    post("/create", { request, response ->
        val gameName = request.queryParams("game_name")
        val username = request.queryParams("username")
        val timeAsString = request.queryParams("countdown_time")
        val pointsToWinAsString = request.queryParams("points_to_win")
        if (gameName == null || gameName.isBlank() || username == null || username.isBlank() || timeAsString == null ||
                timeAsString.isBlank()) {
            return@post response.redirect("/")
        }
        val time: Int
        val pointsToWin: Int?
        try {
            time = timeAsString.toInt()
            pointsToWin = if (pointsToWinAsString.isBlank()) null else pointsToWinAsString.toInt()
        } catch (e: Exception) {
            return@post response.redirect("/")
        }
        if (time < 0 || (pointsToWin != null && pointsToWin < 1)) return@post response.redirect("/")
        val id = RandomStringGenerator.randomString(8)
        val game = Game(gameName, id, time, pointsToWin)
        games.put(id, game)
        request.session(true).attribute("username", username)
        response.redirect("/game?id=" + id)
    })
    get("/game", { request, response ->
        val id = request.queryParams("id")
        val game = games[id]
        if (game == null) {
            halt("There's no game of that id.")
            return@get null
        }
        game.getGame(request, response)
    }, mustacheTemplateEngine)
    get("/choose_name", { request, response ->
        val id: String? = request.queryParams("id")
        if (id == null) {
            halt("Error: ID is null. Please try again.")
            return@get null
        }
        request.session().attribute("redirect_id", id)
        val attributes = HashMap<String, Any>()
        ModelAndView(attributes, "choose_name.html")
    }, mustacheTemplateEngine)
    post("/choose_name", { request, response ->
        val id: String? = request.session().attribute("redirect_id")
        val name: String? = request.queryParams("username")
        if (id != null && name != null) {
            request.session().attribute("username", name)
            return@post response.redirect("/game?id=" + id)
        }
        ""
    })
}
