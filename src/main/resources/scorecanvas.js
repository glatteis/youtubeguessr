
function drawCanvas(guesses, actualViews, winners) {
    var canvas = $("#score_canvas")[0];
    const w = canvas.width;
    const h = canvas.height;

    var standardDeviationHelper = 0;
    Object.keys(guesses).forEach(function (username) {
        standardDeviationHelper += Math.pow((actualViews - guesses[username]), 2);
    });
    var standardDeviation = Math.sqrt(standardDeviationHelper / Object.keys(guesses).length);

    var min = actualViews + standardDeviation;
    var max = actualViews - standardDeviation;

    Object.keys(guesses).forEach(function (username) {
        const guess = guesses[username];
        if (guess >= (actualViews - standardDeviation) && guess <= (actualViews + standardDeviation)) {
            if (guess < min) {
                min = guess;
            }
            if (guess > max) {
                max = guess;
            }
        }
    });

    if (actualViews < min) {
        min = actualViews;
    }
    if (actualViews > max) {
        max = actualViews;
    }

    if (canvas.getContext) {
        const context = canvas.getContext("2d");
        const lineWidth = w - 100;

        context.clearRect(0, 0, w, h);

        context.fillStyle = "#dedfde";
        context.fillRect(25, h / 2 - 20, w - 50, 40);
        context.beginPath();
        context.arc(25, h / 2, 20, 0, 180);
        context.arc(w - 25, h / 2, 20, 180, 360);
        context.fill();
        context.textAlign = "center";
        context.font = "15px Arial";

        if (max + min === 0) return;
        var cutOffLeft = "";
        var cutOffRight = "";

        Object.keys(guesses).forEach(function (username) {
            const guess = guesses[username];
            var name = username;
            if (winners.indexOf(username) !== -1) {
                name += " 👑";
            }
            const place = (guess - min) / (max - min);
            if (place < 0) {
                cutOffLeft += " " + name + " " + guess.toString() + ",";
                return
            } else if (place > 1) {
                cutOffRight += " " + name + " " + guess.toString() + ",";
                return
            }
            const xValue = lineWidth * ((guess - min) / (max - min)) + 50;
            context.fillStyle = "#df3023";
            context.beginPath();
            context.arc(xValue, h / 2, 20, 0, 360);
            context.fill();
            context.fillText(name, xValue, h / 2 + 40, 80);
            context.fillText(guess.toString(), xValue, h / 2 + 60, 80);
        });

        context.font = "20px Arial";
        const xValueActualViews = lineWidth * ((actualViews - min) / (max - min)) + 50;
        context.beginPath();
        context.arc(xValueActualViews, h / 2, 30, 0, 360);
        context.fill();
        context.fillText("Actual views", xValueActualViews, h / 2 + 80, 100);
        context.fillText(actualViews.toString(), xValueActualViews, h / 2 + 100, 100);

        context.font = "15px Arial";
        context.textAlign = "left";
        if (cutOffLeft !== "") {
            context.fillText("<- " + cutOffLeft.substr(0, cutOffLeft.length - 1), 0, 50);
        }

        context.textAlign = "right";
        if (cutOffRight !== "") {
            context.fillText(cutOffRight.substr(0, cutOffRight.length - 1) + " ->", w, 50);
        }
    }
}