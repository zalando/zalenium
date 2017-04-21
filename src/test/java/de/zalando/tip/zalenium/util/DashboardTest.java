package de.zalando.tip.zalenium.util;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DashboardTest {
    private static final List<String> REQUIRED_FILES_FOR_TESTING = Arrays
            .asList(("list_template.html,dashboard_template.html,zalando.ico").split(","));
    private static final List<String> REQUIRED_DIRECTORIES_FOR_TESTING = Arrays.asList(("css,js").split(","));

    @Before
    public void initDashboard() throws IOException {
        Dashboard.setExecutedTests(0);
        removeTestingVideosFolder();
        ensureRequiredInputFilesExist();
    }

    @After
    public void cleanCreatedEmptyResources() throws IOException {
        for (String requiredFile : REQUIRED_FILES_FOR_TESTING) {
            removeEmptyFile(requiredFile);
        }
        for (String requiredDir : REQUIRED_DIRECTORIES_FOR_TESTING) {
            removeEmptyDir(requiredDir);
        }
    }

    @After
    public void removeTestingVideosFolder() throws IOException {
        File videos = fileWithLocalPath(Dashboard.VIDEOS_FOLDER_NAME);
        FileUtils.deleteQuietly(videos);
    }

    @Test
    public void testCountOne() throws IOException {
        TestInformation ti = createDefaultTestInformation();

        Dashboard.updateDashboard(ti);

        Assert.assertEquals(1, Dashboard.getExecutedTests());
    }

    @Test
    public void testCountTwo() throws IOException {
        TestInformation ti = createDefaultTestInformation();
        Dashboard.updateDashboard(ti);

        Dashboard.updateDashboard(ti);

        Assert.assertEquals(2, Dashboard.getExecutedTests());
    }

    @Test
    public void missingExecutedTestsFile() throws IOException {
        TestInformation ti = createDefaultTestInformation();
        Dashboard.updateDashboard(ti);
        removeTestingVideosFolder();

        Dashboard.updateDashboard(ti);

        Assert.assertEquals(1, Dashboard.getExecutedTests());
    }

    private TestInformation createDefaultTestInformation() {
        return new TestInformation("seleniumSessionId", "testName", "proxyName", "browser", "browserVersion",
                "platform");
    }

    private void ensureRequiredInputFilesExist() throws IOException {
        for (String requiredFile : REQUIRED_FILES_FOR_TESTING) {
            createEmptyFileIfMissing(requiredFile);
        }
        for (String requiredDir : REQUIRED_DIRECTORIES_FOR_TESTING) {
            createEmptyDirIfMissing(requiredDir);
        }
    }

    private void createEmptyDirIfMissing(String dirname) {
        File toBeChecked = fileWithLocalPath(dirname);
        if (!toBeChecked.exists()) {
            toBeChecked.mkdir();
        }
    }

    private File fileWithLocalPath(String name) {
        return new File(new CommonProxyUtilities().currentLocalPath() + "/" + name);
    }

    private void createEmptyFileIfMissing(String filename) throws IOException {
        File toBeChecked = fileWithLocalPath(filename);
        if (!toBeChecked.exists()) {
            toBeChecked.createNewFile();
        }
    }

    private void removeEmptyDir(String dirname) {
        File toBeChecked = fileWithLocalPath(dirname);
        if (toBeChecked.exists() && toBeChecked.isDirectory() && toBeChecked.listFiles().length == 0) {
            toBeChecked.delete();
        }
    }

    private void removeEmptyFile(String filename) {
        File toBeChecked = fileWithLocalPath(filename);
        if (toBeChecked.exists() && toBeChecked.length() == 0) {
            toBeChecked.delete();
        }
    }
}
