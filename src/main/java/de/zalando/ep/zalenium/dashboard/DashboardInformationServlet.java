package de.zalando.ep.zalenium.dashboard;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.nio.charset.StandardCharsets.UTF_8;

public class DashboardInformationServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        process(request, response);
    }

    @SuppressWarnings("unused")
    protected void process(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String lastDateAddedToDashboardParameter = request.getParameter("lastDateAddedToDashboard");
        Date lastDateAdded = null;
        if (!Strings.isNullOrEmpty(lastDateAddedToDashboardParameter)) {
            long lastDateAddedLong = Long.parseLong(lastDateAddedToDashboardParameter);
            lastDateAdded = new Date(lastDateAddedLong);
        }
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type,Authorization,Access-Control-Allow-Origin,*");
        List<TestInformation> executedTestsInformation = Dashboard.loadTestInformationFromFile();
        executedTestsInformation.sort(Comparator.comparing(TestInformation::getAddedToDashboardTime));
        if (lastDateAdded != null) {
            Date finalLastDateAdded = lastDateAdded;
            executedTestsInformation = executedTestsInformation.stream()
                    .filter(testInformation -> new Date(testInformation.getAddedToDashboardTime()).compareTo(finalLastDateAdded) > 0)
                    .collect(Collectors.toList());
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        byte[] testInformation = gson.toJson(executedTestsInformation).getBytes(UTF_8);
        response.setStatus(HTTP_OK);
        response.setContentType(JSON_UTF_8.toString());
        response.setContentLength(testInformation.length);
        try (ServletOutputStream out = response.getOutputStream()) {
            out.write(testInformation);
        }
    }
}
