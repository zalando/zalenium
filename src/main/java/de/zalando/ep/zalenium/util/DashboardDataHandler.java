package de.zalando.ep.zalenium.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;

/**
 * Maintain a <code>TestInformationRepository</code> stored as a file to be used for the dashboard UI.
 * 
 * @author robertzimmermann
 */
public final class DashboardDataHandler {

    public static final String RESOURCE_FOLDER_JS = "js";
    public static final String RESOURCE_FOLDER_CSS = "css";
    public static final String RESOURCE_ZALANDO_ICO = "zalando.ico";
    public static final String RESOURCE_INDEX_HTML = "index.html";
    public static final String DASHBOARD_HTML_FILENAME = "dashboard.html";
    public static final String DASHBOARD_DATA_FILENAME = "dashboardData.json";
    public static final String LOCAL_RESOURCES_PATH = new CommonProxyUtilities().currentLocalPath();

    private static final Logger LOGGER = Logger.getLogger(DashboardDataHandler.class.getName());

    private String dashboardFolderPath;
    private File dashboardDataFile;

    private DashboardDataHandler() {
        // please use static factory addNewTest()
    }

    public static synchronized TestInformationRepository addNewTest(TestInformation testInformation)
            throws IOException {
        DashboardDataHandler dashboardDataHandler = new DashboardDataHandler();
        return dashboardDataHandler.doAddNewTest(testInformation);
    }

    private TestInformationRepository doAddNewTest(TestInformation testInformation) throws IOException {
        setDashboardFolderPath(testInformation.getVideoFolderPath());
        ensureStaticDashboardResourcesExist();
        dashboardDataFile = new File(dashboardFolderPath, DASHBOARD_DATA_FILENAME);
        TestInformationRepository tiRepo = readTestInformationRepository();
        tiRepo.add(testInformation);
        writeTestInformationRepository(tiRepo);
        return tiRepo;
    }

    private void setDashboardFolderPath(String dashboardFolderPath) throws IOException {
        File checkPath = new File(dashboardFolderPath);
        if (checkPath.exists() && !checkPath.isDirectory()) {
            throw new IOException(
                    "Expected dashboardFolderPath to be a directory. dashboardFolderPath=" + dashboardFolderPath);
        }
        this.dashboardFolderPath = dashboardFolderPath;
    }

    private TestInformationRepository readTestInformationRepository() throws IOException {
        String dashboardDataJson = "";
        if (dashboardDataFile.exists()) {
            dashboardDataJson = FileUtils.readFileToString(dashboardDataFile, UTF_8);
        }
        return TestInformationRepository.fromJsonString(dashboardDataJson);
    }

    private void writeTestInformationRepository(TestInformationRepository tiRepo) throws IOException {
        FileUtils.write(dashboardDataFile, tiRepo.toJson(), UTF_8);
    }

    private void ensureStaticDashboardResourcesExist() throws IOException {
        // dashboard.html should be always copied as otherwise existing old versions would not be updated automatically
        copyFileAlways(DASHBOARD_HTML_FILENAME);
        copyFileIfMissing(RESOURCE_INDEX_HTML);
        copyFileIfMissing(RESOURCE_ZALANDO_ICO);
        copyDirectoryIfMissing(RESOURCE_FOLDER_CSS);
        copyDirectoryIfMissing(RESOURCE_FOLDER_JS);
    }

    private void copyFileAlways(String fileToCheck) throws IOException {
        File fileResource = new File(dashboardFolderPath, fileToCheck);
        FileUtils.copyFile(localResourceAsFile(fileToCheck), fileResource);
    }

    private void copyFileIfMissing(String fileToCheck) throws IOException {
        File fileResource = new File(dashboardFolderPath, fileToCheck);
        if (!fileResource.exists()) {
            FileUtils.copyFile(localResourceAsFile(fileToCheck), fileResource);
        }
    }

    private void copyDirectoryIfMissing(String dirToCheck) throws IOException {
        File dirResource = new File(dashboardFolderPath, dirToCheck);
        if (!dirResource.exists()) {
            FileUtils.copyDirectory(localResourceAsFile(dirToCheck), dirResource);
        }
    }

    private File localResourceAsFile(String resourceName) {
        return new File(LOCAL_RESOURCES_PATH, resourceName);
    }

    public static synchronized void clearRecordedVideosAndLogs(String dashboardFolderPath) throws IOException {
        if (dashboardFolderPath == null || "".equals(dashboardFolderPath)) {
            LOGGER.log(Level.WARNING, "Invalid dashboardFolderPath given");
            return;
        }
        DashboardDataHandler dashboardDataHandler = new DashboardDataHandler();
        dashboardDataHandler.setDashboardFolderPath(dashboardFolderPath);
        // we may want to delete only videos and logs here in order to not do extra copy work
        deleteAllContentsOf(new File(dashboardFolderPath));
        dashboardDataHandler.ensureStaticDashboardResourcesExist();
    }

    private static void deleteAllContentsOf(File dashboardFolder) {
        File[] allFiles = dashboardFolder.listFiles();
        if (allFiles != null) {
            for (File file : allFiles) {
                FileUtils.deleteQuietly(file);
            }
        }
    }
}
