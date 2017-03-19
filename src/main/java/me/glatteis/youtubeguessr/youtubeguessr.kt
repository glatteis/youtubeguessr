package me.glatteis.youtubeguessr

import spark.ModelAndView
import spark.Spark.*
import spark.template.mustache.MustacheTemplateEngine
import java.util.*

val games = HashMap<String, Game>()
val random = Random()
val mustacheTemplateEngine = MustacheTemplateEngine()

fun main(args: Array<String>) {
    webSocket("/gamesocket", GameWebSocketHandler)
    staticFileLocation("/")
    get("/", { request, response ->
        val attributes = HashMap<String, Any>()
        ModelAndView(attributes, "create_game.html")
    }, mustacheTemplateEngine)
    post("/create", { request, response ->
        val gameName = request.queryParams("game_name")
        val username = request.queryParams("username")
        if (gameName == null || gameName.isBlank() || username == null || username.isBlank()) {
            return@post response.redirect("/")
        }
        val id = RandomStringGenerator.randomString(8)
        val game = Game(gameName, id)
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
            halt("You know what you did.")
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
