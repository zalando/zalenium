package de.zalando.ep.zalenium.it;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.safari.SafariDriver;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import de.zalando.ep.zalenium.util.DashboardDataHandler;
import de.zalando.ep.zalenium.util.DashboardTempFileBase;
import de.zalando.ep.zalenium.util.TestInformation;

public class DashboardTestIntegration extends DashboardTempFileBase {

    private WireMockServer wireMockServer;
    private WebDriver webDriver;

    private void configureAndStartWireMock() throws IOException {
        /*
         * Not using JUnit rule for WireMock as we otherwise may introduce a race condition in conjunction with
         * TemporaryFolder rule
         */
        // WireMockConfiguration.options().dynamicPort()
        WireMockConfiguration wmConfig = WireMockConfiguration.options().port(7070)
                .usingFilesUnderDirectory(tempDashboardPath);
        wireMockServer = new WireMockServer(wmConfig);

        // need to put dashboard files under __files for WireMock
        generateTempDashboardPath();

        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
        DashboardDataHandler.clearRecordedVideosAndLogs(tempDashboardPath + "/" + TestInformation.VIDEOS_FOLDER_NAME);
    }

    private void generateTempDashboardPath() throws IOException {
        tempDashboardPath = temporaryFolder.newFolder("__files").getAbsolutePath();
        changeDashboardPathTo(tempDashboardPath);

    }

    @After
    public void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
        if (webDriver != null) {
            webDriver.quit();
        }

    }

    @Test
    public void cleanupButtonHiddenWhenNoData() throws InterruptedException, IOException {
        configureAndStartWireMock();
        loadDashboardHtml();

        Assert.assertFalse("Expected invisible cleanup button", getCleanupButton().isDisplayed());
    }

    private WebElement getCleanupButton() {
        return webDriver.findElement(By.id("cleanupButton"));
    }

    @Test
    public void cleanupButtonDisplayed() throws InterruptedException, IOException {
        configureAndStartWireMock();
        TestInformation toBeAdded = createTestInformation(1);
        DashboardDataHandler.addNewTest(toBeAdded);

        loadDashboardHtml();

        Assert.assertTrue("Expected cleanup button to be displayed", getCleanupButton().isDisplayed());
    }

    @Test
    public void filterOneOfTwoElements() throws InterruptedException, IOException {
        configureAndStartWireMock();
        DashboardDataHandler.addNewTest(createTestInformation(1, "Safari"));
        DashboardDataHandler.addNewTest(createTestInformation(2, "Firefox"));

        loadDashboardHtml();
        Assert.assertEquals("Expected number of displayed tests not seen", 2, numberOfTestDisplayed());
        filterBy("Safari");

        Assert.assertEquals("Expected number of displayed tests not seen", 1, numberOfTestDisplayed());
    }

    private int numberOfTestDisplayed() {
        WebElement listDiv =  webDriver.findElement(By.id("testListTarget"));
        return listDiv.findElements(By.className("nav-item")).size();
    }

    private void filterBy(String string) {
        webDriver.findElement(By.id("filterInput")).sendKeys(string);
    }

    private void takeScreenshot() throws IOException {
        File scrFile = ((TakesScreenshot) webDriver).getScreenshotAs(OutputType.FILE);
        System.err.println(scrFile.getAbsolutePath());
        File targetFile = new File("screenshot.png");
        System.err.println(targetFile.getAbsolutePath());
        FileUtils.copyFile(scrFile, targetFile);
    }

    // helper during development time - maybe removed when dashboard is finished
    private void debug() throws InterruptedException, IOException {
        if (webDriver != null) {
            takeScreenshot();
        }
        System.err.println(tempDashboardPath);
        System.err.println(mockedUrl());
        Thread.sleep(30000);
    }

    private void loadDashboardHtml() {
        initSafariDriver();
        webDriver.get(mockedUrl());
    }

    private void initSafariDriver() {
        webDriver = new SafariDriver();
        webDriver.manage().window().maximize();
    }

    private String mockedUrl() {
        return "http://localhost:" + wireMockServer.port() + "/" + TestInformation.VIDEOS_FOLDER_NAME
                + "/dashboard.html";
    }
}
