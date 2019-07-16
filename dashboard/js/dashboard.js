function getLatestDateAddedToDashboard() {
    return $(".list-group-item").first().data('added-to-dashboard');
}

function addTestItem(item) {
    let platformLogo  = "";
    if (item.platform.toLowerCase().includes("mac")) {
        platformLogo = "apple";
    } else if (item.platform.toLowerCase().includes("windows")) {
        platformLogo = "windows";
    } else {
        platformLogo = item.platform.toLowerCase();
    }
    let buildDirectory = item.videoFolderPath.replace("/home/seluser/videos", "");
    buildDirectory = buildDirectory.trim().length > 0 ? buildDirectory.replace("/", "").concat("/") : "";
    const fileName = buildDirectory.concat(item.fileName);
    const seleniumLogFileName = buildDirectory.concat(item.seleniumLogFileName);
    const browserDriverLogFileName = buildDirectory.concat(item.browserDriverLogFileName);
    const testItem =
        "<a href=\"#\" class=\"list-group-item list-group-item-action flex-column align-items-start p-2\"" +
        " data-video=\"" + fileName + "\"" +
        " data-test-name=\"" + item.testName + "\"" +
        " data-test-selenium-session-id=\"" + item.seleniumSessionId + "\"" +
        " data-test-status=\"" + item.testStatus + "\"" +
        " data-browser=\"" + item.browser + "\"" +
        " data-browser-version=\"" + item.browserVersion + "\"" +
        " data-platform=\"" + platformLogo + "\"" +
        " data-proxy-name=\"" + item.proxyName + "\"" +
        " data-date-time=\"" + item.timestamp + "\"" +
        " data-added-to-dashboard=\"" + item.addedToDashboardTime + "\"" +
        " data-screen-dimension=\"" + item.screenDimension + "\"" +
        " data-time-zone=\"" + item.timeZone + "\"" +
        " data-test-build=\"" + item.build + "\"" +
        " data-selenium-log=\"" + seleniumLogFileName + "\"" +
        " data-browser-driver=\"" + browserDriverLogFileName + "\"" +
        " data-retention-date=\"" + item.retentionDate + "\">" +
        "<div class=\"d-flex w-100 justify-content-between\">" +
        "<small class=\"font-weight-bold text-truncate\">" + item.testName + "</small>" +
        "</div>" +
        "<div class=\"d-flex w-100 justify-content-between\">" +
        "<small>" + item.timestamp + "</small>" +
        "<small>" +
        "<img alt=\"" + item.proxyName + "\" src=\"img/" + item.proxyName.toLowerCase() + ".png\" width=\"24px\" height=\"24px\">" +
        "</small>" +
        "</div>" +
        "<div class=\"d-flex w-100 justify-content-between\">" +
        "<span>" +
        "<img alt=\"" + platformLogo + "\" src=\"img/" + platformLogo + ".png\" width=\"24px\" height=\"24px\">" +
        "<img alt=\"" + item.browser + "\" src=\"img/" + item.browser.toLowerCase() + ".png\" width=\"24px\" height=\"24px\">" +
        "<small class=\"pl-1\">" + item.browserVersion + "</small>" +
        "</span>" +
        "<span>" +
        "<img alt=\"" + item.testStatus + "\" src=\"img/" + item.testStatus.toLowerCase() + ".png\" width=\"24px\" height=\"24px\">" +
        "</span>" +
        "</div>" +
        "</a>";
    $('#tests').prepend(testItem);
    const testCount = $(".list-group-item").length;
    const testCountElement = $('#testCount');
    testCountElement.removeClass("btn-dark");
    testCountElement.addClass("btn-light");
    testCountElement.html("Tests <span class=\"badge badge-primary\">" + testCount + "</span>");
}

function loadDashboardItems() {
    let latestDateAdded = getLatestDateAddedToDashboard();
    if (latestDateAdded === undefined) {
        latestDateAdded = 0;
    }
    const newDashboardItems = [location.protocol, '//', location.host, location.pathname].join('') +
        'information?lastDateAddedToDashboard=' + latestDateAdded;
    $.getJSON(newDashboardItems, function(data) {
        $.each(data, function (i, item) {
            addTestItem(item);
        });
        searchTestsList();
    });
}

function playVideo($video) {
    const video = $('#video');
    const source = $('#video-source');
    source.attr("src", $video);
    source.attr("type", "video/mp4");
    video.get(0).pause();
    video.get(0).load();
    video.get(0).play();
}

function setTestInformation($testName, $browser, $browserVersion, $platform, $proxyName, $dateTime,
                            $screenDimension, $timeZone, $build, $testStatus, $retentionDate) {
    const testName = $("#test-name");
    testName.html("");
    testName.append("<img alt=\"" + $testStatus + "\" src=\"img/" + $testStatus.toLowerCase() + ".png\" class=\"mr-1\" " +
        "width=\"48px\" height=\"48px\">");
    testName.append($testName);
    testName.append("<small class=\"float-right\">" + $dateTime + "</small>");

    const browserPlatformProxy = $("#browser-platform-proxy");
    browserPlatformProxy.html("");
    browserPlatformProxy.append("<img alt=\"" + $platform + "\" src=\"img/" + $platform.toLowerCase() + ".png\" class=\"mr-1\" " +
        "width=\"48px\" height=\"48px\">");
    browserPlatformProxy.append("<img alt=\"" + $browser + "\" src=\"img/" + $browser.toLowerCase() + ".png\" class=\"mr-1\" " +
        "width=\"48px\" height=\"48px\">");
    browserPlatformProxy.append($browserVersion);
    browserPlatformProxy.append("<img alt=\"" + $proxyName + "\" src=\"img/" + $proxyName.toLowerCase() + ".png\" class=\"float-right\" " +
        "width=\"48px\" height=\"48px\">");

    const screenResolutionTimeZone = $("#screen-resolution-time-zone");
    screenResolutionTimeZone.html("");
    if ($screenDimension.length > 0) {
        screenResolutionTimeZone.append("<img alt=\"Screen Resolution\" src=\"img/screen-resolution.png\" class=\"mr-1\" " +
            "width=\"48px\" height=\"48px\">");
        screenResolutionTimeZone.append("<small class=\"mr-1\">" + $screenDimension + "</small>");
    }
    if ($timeZone.length > 0) {
        screenResolutionTimeZone.append("<img alt=\"Time Zone\" src=\"img/timezone.png\" class=\"mr-1\" " +
            "width=\"48px\" height=\"48px\">");
        screenResolutionTimeZone.append("<small class=\"mr-1\">" + $timeZone + "</small>");
    }
    screenResolutionTimeZone.append("<span class=\"float-right\"><img alt=\"Retention Date\" src=\"img/retention-date.png\" " +
        "class=\"mr-1\" width=\"48px\" height=\"48px\"><small>" + $retentionDate + "</small></span>");
    if ($build.toString().length > 0) {
        const buildElement = $("#build");
        buildElement.html("");
        buildElement.removeClass("p-0");
        buildElement.addClass("p-1");
        buildElement.parent().removeClass("invisible");
        buildElement.append("<img alt=\"Build\" src=\"img/build.png\" class=\"mr-1\" width=\"48px\" height=\"48px\">");
        buildElement.append("<small class=\"mr-1\">" + $build + "</small>");
    } else {
        const buildElement = $("#build");
        buildElement.html("");
        buildElement.removeClass("p-1");
        buildElement.addClass("p-0");
        buildElement.parent().addClass("invisible");
    }

    $("#main-container").removeClass("invisible");
}

function loadLogs($seleniumLogFile, $browserDriverLogFile) {
    $("#collapseOne").removeClass("show");
    $("#collapseTwo").removeClass("show");
    const seleniumLog = $("#seleniumLog");
    seleniumLog.html("Selenium Log not loaded yet...");
    const browserDriverLog = $("#browserDriverLog");
    browserDriverLog.html("Browser Driver Log not loaded yet...");

    if ($seleniumLogFile.length > 0) {
        seleniumLog.load($seleniumLogFile);
    }
    if ($browserDriverLogFile.length > 0) {
        browserDriverLog.load($browserDriverLogFile);
    }
}

function blockUi() {
    const overlay = document.getElementById("ui_blocker");
    if (overlay != null) {
        overlay.style.display = "block";
        overlay.style.right = "0px";
        overlay.style.bottom = "0px";
        overlay.addEventListener("click", function(event) {
            event.preventDefault();
            return false;
        }, false);
    }
}

function unblockUi() {
    const overlay = document.getElementById("ui_blocker");
    if (overlay != null) {
        overlay.style.display = "none";
    }
}

function searchTestsList() {
    const currentQuery = $("#search").val().toUpperCase();
    if (currentQuery !== "") {
        const tokensCrtQuery = currentQuery.split(" ");
        $(".list-group-item").each(function(){
            $(this).hide();
            const currentKeyword = $(this).text().toUpperCase() + $(this).data("browser").toUpperCase() +
                $(this).data("platform").toUpperCase() + $(this).data("test-build").toString().toUpperCase() +
                $(this).data("test-status").toUpperCase() + $(this).data("proxy-name").toUpperCase() +
                $(this).data("time-zone").toUpperCase();
            let allTokensFound = true;
            for (let i = 0; i < tokensCrtQuery.length; i++) {
                const crtToken = tokensCrtQuery[i];
                if (currentKeyword.indexOf(crtToken) < 0) {
                    allTokensFound = false;
                    break;
                }
            }
            if (allTokensFound) {
                $(this).show();
            }
        });
    } else {
        $(".list-group-item").show();
    }
}

$(document).ready(function() {

    // Load items as soon as the page loads
    loadDashboardItems();

    // Retrieve deltas every 15 seconds
    setInterval(function() {
        loadDashboardItems();
    }, 15000);

    $("#tests").on("click", ".list-group-item", function() {
        const $this = $(this);
        const $video = $this.data("video");
        const $testName = $this.data("test-name");
        const $browser = $this.data("browser");
        const $browserVersion = $this.data("browser-version");
        const $platform = $this.data("platform");
        const $proxyName = $this.data("proxy-name");
        const $dateTime = $this.data("date-time");
        const $retentionDate = $this.data("retention-date");
        const $seleniumLogFile = $this.data("selenium-log");
        const $browserDriverLogFile = $this.data("browser-driver");
        const $screenDimension = $this.data("screen-dimension");
        const $timeZone = $this.data("time-zone");
        const $build = $this.data("test-build");
        const $testStatus = $this.data("test-status");

        $('.active').removeClass("active");
        $this.toggleClass("active");

        // Set test info to be displayed
        setTestInformation($testName, $browser, $browserVersion, $platform, $proxyName, $dateTime,
            $screenDimension, $timeZone, $build, $testStatus, $retentionDate);

        // Pass clicked link element to another function
        playVideo($video);

        // Load logs
        loadLogs($seleniumLogFile, $browserDriverLogFile);

        // Select first tab
        $("#testTabs").find("a:first").tab("show");
    });

    $("#search").on("keyup", function () {
        searchTestsList();
    });

    $(function() {
        const url = new URL(window.location);
        const params = url.searchParams;
        const q = params.get('q');
        if (q !== null && q !== '') {
            $('#search').val(decodeURIComponent(q));
            searchTestsList();
        }
    });

    $("#cleanupButton").click(function () {
        $("#cleanupModal").modal("show");
    });

    $("#resetButton").click(function () {
        $("#resetModal").modal("show");
    });

    $("#cleanupModalConfirm").click(function () {
        $("#cleanupModal").modal("hide");
        blockUi();

        const targetUrl = [location.protocol, "//", location.host, location.pathname].join("") + "cleanup?action=doCleanup";

        $.ajax({
            type: "POST",
            url: targetUrl,
            statusCode: {
                200: function(response){
                    unblockUi();
                    window.location.reload();
                }
            }
        });
    });

    $("#resetModalConfirm").click(function () {
        $("#resetModal").modal("hide");
        blockUi();

        const targetUrl = [location.protocol, "//", location.host, location.pathname].join("") + "cleanup?action=doReset";

        $.ajax({
            type: "POST",
            url: targetUrl,
            statusCode: {
                200: function(response){
                    unblockUi();
                    window.location.reload();
                }
            }
        });
    });
});
