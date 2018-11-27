package de.zalando.ep.zalenium.dashboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

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
        List<TestInformation> executedTestsInformation = Dashboard.loadTestInformationFromFile();
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
