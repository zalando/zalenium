package de.zalando.ep.zalenium.dashboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.FileUtils;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.net.MediaType.*;
import static de.zalando.ep.zalenium.dashboard.Dashboard.getLocalVideosPath;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.nio.charset.StandardCharsets.UTF_8;

public class DashboardInformationServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        process(request, response);
    }

    @SuppressWarnings("unused")
    protected void process(HttpServletRequest request, HttpServletResponse response) throws IOException {
        List<TestInformation> executedTestsInformation = loadTestInformationFromFile();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        byte[] testInformation = gson.toJson(executedTestsInformation).getBytes(UTF_8);
        response.setStatus(HTTP_OK);
        response.setContentType(JSON_UTF_8.toString());
        response.setContentLength(testInformation.length);
        try (ServletOutputStream out = response.getOutputStream()) {
            out.write(testInformation);
        }
    }

    private List<TestInformation> loadTestInformationFromFile() throws IOException {
        String testInformationFileName = "testInformation.json";
        File testInformationFile = new File(getLocalVideosPath(), testInformationFileName);
        if (testInformationFile.exists()) {
            String testInformationContents = FileUtils.readFileToString(testInformationFile, UTF_8);
            Type collectionType = new TypeToken<ArrayList<TestInformation>>(){}.getType();
            return new Gson().fromJson(testInformationContents, collectionType);
        } else {
            return new ArrayList<>();
        }
    }

}
