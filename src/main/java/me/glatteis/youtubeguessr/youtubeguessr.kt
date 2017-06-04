package me.glatteis.youtubeguessr

import org.json.JSONArray
import spark.ModelAndView
import spark.Spark.*
import spark.template.mustache.MustacheTemplateEngine
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.ConcurrentHashMap

val games = ConcurrentHashMap<String, Game>()
val mustacheTemplateEngine = MustacheTemplateEngine()

private val randomStringGenerator = RandomStringGenerator(SecureRandom())

// Used data classes

// Represents in-game player
data class User(val name: String, var points: Int) {
    var afk = 0
    var hasGuessed = false
}

// Represents YouTube video
data class Video(val id: String, val views: Int, val duration: Long)

var filePrefix: String = ""
var environmentKey: String? = null

fun main(args: Array<String>) {
    if (args.isEmpty()) throw IllegalArgumentException("Port has to be specified.")
    val portAsString = args[0]
    val port = portAsString.toInt()
    port(port)
    webSocket("/gamesocket", GameWebSocketHandler)

    if (args.size > 1) {
        filePrefix = args[1]
        externalStaticFileLocation("/app/${args[1]}")
    } else {
        staticFileLocation("/")
    }

    environmentKey = System.getenv("YT_KEY")

    // Serve start.html as front page
    get("/", { _, _ ->
        ModelAndView(null, filePrefix + "start.html")
    }, mustacheTemplateEngine)
    // Server create.html as game creation page
    get("/create", { _, _ ->
        val attributes = HashMap<String, Any>()
        ModelAndView(attributes, filePrefix + "create_game.html")
    }, mustacheTemplateEngine)
    // Create a game
    post("/create", { request, response ->
        //Fetch params
        val gameName = request.queryParams("game_name")
        val username = request.queryParams("username")
        val timeAsString = request.queryParams("countdown_time")
        val pointsToWinAsString = request.queryParams("points_to_win")
        val publicAsString = request.queryParams("public")
        val chillModeAsString = request.queryParams("chill_mode")
        val pointsForExactGuessesAsString = request.queryParams("points_for_exact_guesses") ?: "1"
        // A public game will be visible from the game browser
        val isPublic = (publicAsString == "on")
        val isChillMode = (chillModeAsString == "on")
        // Check for invalid data
        if (gameName == null || gameName.isBlank() || username == null || username.isBlank() || timeAsString == null ||
                timeAsString.isBlank() || username.length > 16 || gameName.length > 16) {
            return@post response.redirect("/")
        }
        // Try to convert time and points to win values to numbers. If not successful, abort
        val time: Int
        val pointsToWin: Int?
        val pointsForExactGuesses: Int
        try {
            time = timeAsString.toInt()
            pointsToWin = if (pointsToWinAsString.isBlank()) null else pointsToWinAsString.toInt()
            pointsForExactGuesses = pointsForExactGuessesAsString.toInt()
        } catch (e: Exception) {
            return@post response.redirect("/")
        }
        // Check for invalid data
        if (time < 10 || (pointsToWin != null && pointsToWin < 1) || pointsForExactGuesses < 1)
            return@post response.redirect("/")
        // Games will be identified using unique IDs
        var id: String
        do {
            id = randomStringGenerator.randomString(8)
        } while (games.containsKey(id))
        // Create game instance
        val game = Game(gameName, id, if (isChillMode) 0 else time, isChillMode, pointsToWin, isPublic,
                pointsForExactGuesses)
        games.put(id, game)
        // Add username to session
        request.session(true).attribute("username", username)
        // Redirect
        response.redirect("/game?id=" + id)
    })
    // Fetch a list of every game
    get("/list", { _, _ ->
        // For every game: Get data if public
        val publicGames = games.values
                .filter { it.isPublic }
                .map {
                    mapOf(
                            Pair("name", it.name),
                            Pair("players", it.userSessions.size),
                            Pair("pointsToWin", it.winPoints),
                            Pair("countdownTime", it.countdownTime),
                            Pair("hasStarted", it.isVideoPlaying),
                            Pair("isChillMode", it.isChillMode),
                            Pair("id", it.id)
                    )
                }
        // Pass to JS on page as attribute, script will build table
        val attributes = HashMap<String, Any>()
        attributes["games"] = JSONArray(publicGames)
        ModelAndView(attributes, filePrefix + "list_games.html")
    }, mustacheTemplateEngine)
    // Given an ID, return response of game
    get("/game", { request, response ->
        val id = request.queryParams("id")
        if (id == null) {
            halt("You did not specify an id. <a href='/'>Back to youtubeguessr</a>")
            return@get null
        }
        val game = games[id]
        if (game == null) {
            halt("There's no game of that id. <a href='/'>Back to youtubeguessr</a>")
            return@get null
        }
        game.getGame(request, response)
    }, mustacheTemplateEngine)
    // Game will redirect to choose_name if user does not have a name yet.
    get("/choose_name", { request, _ ->
        // This is the game ID that the server will redirect to after choosing name
        val id: String? = request.queryParams("id")
        if (id == null) {
            halt("Error: ID is null. Please try again.")
            return@get null
        }
        request.session().attribute("redirect_id", id)
        val attributes = HashMap<String, Any>()
        ModelAndView(attributes, filePrefix + "choose_name.html")
    }, mustacheTemplateEngine)
    // After choosing a name, data will end up in session
    post("/choose_name", { request, response ->
        val id: String? = request.session().attribute("redirect_id")
        val name: String? = request.queryParams("username")
        if (id != null && name != null && name.isNotBlank() && name.length <= 16) {
            request.session().attribute("username", name)
            // Redirect to game
            return@post response.redirect("/game?id=" + id)
        }
        ""
    })
}