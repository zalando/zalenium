package de.zalando.ep.zalenium.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;

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
    public static final String DASHBOARD_HTML_FILENAME = "dashboard.html";
    public static final String DASHBOARD_DATA_FILENAME = "dashboardData.json";
    public static final String LOCAL_RESOURCES_PATH = new CommonProxyUtilities().currentLocalPath();

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
        this.dashboardFolderPath = testInformation.getVideoFolderPath();
        ensureStaticDashboardResourcesExist();
        dashboardDataFile = new File(dashboardFolderPath, DASHBOARD_DATA_FILENAME);
        TestInformationRepository tiRepo = readTestInformationRepository();
        tiRepo.add(testInformation);
        writeTestInformationRepository(tiRepo);
        return tiRepo;
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
        copyFileIfMissing(RESOURCE_ZALANDO_ICO);
        copyFileIfMissing(DASHBOARD_HTML_FILENAME);
        copyDirectoryIfMissing(RESOURCE_FOLDER_CSS);
        copyDirectoryIfMissing(RESOURCE_FOLDER_JS);
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

    static File localResourceAsFile(String resourceName) {
        return new File(LOCAL_RESOURCES_PATH, resourceName);
    }
}
