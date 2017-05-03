package de.zalando.ep.zalenium.util;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

public class TestInformationRepositoryTest {
    public static final String TESTING_JSON_DEFAULT = "{\"seleniumSessionId\":\"seleniumSessionIdFROMJSON\","
            + "\"testName\":\"testNameFROMJSON\",\"proxyName\":\"proxyName\","
            + "\"browser\":\"browser\",\"browserVersion\":\"browserVersion\","
            + "\"platform\":\"platform\",\"platformVersion\":\"\","
            + "\"fileName\":\"proxyname_testName_browser_platform_20170428140524\","
            + "\"fileExtension\":\"\",\"videoUrl\":\"\",\"logUrls\":[],"
            + "\"videoFolderPath\":\"/home/seluser/videos\","
            + "\"logsFolderPath\":\"/home/seluser/videos/logs/proxyname_testName_browser_platform_20170428140524\","
            + "\"testNameNoExtension\":\"proxyname_testName_browser_platform_20170428140524\",\"videoRecorded\":true,"
            + "\"recordingTimeMillis\":1493632208545,\"displayDateAndTime\":\"01-Mai 11:50:08\","
            + "\"browserAndPlatform\":\"browser browserVersion, platform\"}";

    public static final TestInformation TEST_INFORMATION_DEFAULT = new TestInformation("seleniumSessionId", "testName",
            "proxyName", "browser", "browserVersion", "platform");

    @Test
    public void emptyJsonNull() {
        TestInformationRepository emptyRepo = TestInformationRepository.fromJsonString(null);
        assertEmptyNotNullRepo(emptyRepo);
    }

    @Test
    public void emptyJson() {
        TestInformationRepository emptyRepo = TestInformationRepository.fromJsonString("");
        assertEmptyNotNullRepo(emptyRepo);
    }

    @Test
    public void addFirstTestInformation() {
        TestInformationRepository tiRepo = oneElementRepo();
        Assert.assertEquals(1, tiRepo.size());
    }

    @Test
    public void toJson() {
        TestInformationRepository tiRepo = new TestInformationRepository();
        tiRepo.add(TEST_INFORMATION_DEFAULT);
        String tiRepoJson = tiRepo.toJson();
        Assert.assertThat(tiRepoJson, CoreMatchers.containsString("proxyname_testName_browser_platform_"));
    }

    @Test
    public void oneElementFromJson() {
        String oneElemJson = "[" + TESTING_JSON_DEFAULT + "]";
        TestInformationRepository tiRepo = TestInformationRepository.fromJsonString(oneElemJson);
        Assert.assertEquals(1, tiRepo.size());
        Assert.assertThat(tiRepo.get(0).getTestNameNoExtension(),
                CoreMatchers.containsString("proxyname_testName_browser_platform_"));
    }

    private void assertEmptyNotNullRepo(TestInformationRepository emptyRepo) {
        Assert.assertNotNull(emptyRepo);
        Assert.assertEquals(0, emptyRepo.size());
    }

    private TestInformationRepository oneElementRepo() {
        TestInformationRepository tiRepo = TestInformationRepository.fromJsonString(null);
        tiRepo.add(TEST_INFORMATION_DEFAULT);
        return tiRepo;
    }

    @Test
    public void emptyVideoFolderPathWhenEmptyRepository() {
        TestInformationRepository tiRepo = new TestInformationRepository();
        Assert.assertEquals("", tiRepo.getVideoFolderPath());
    }

    @Test
    public void videoFolderPathFromFirstElement() {
        TestInformationRepository tiRepo = new TestInformationRepository();
        tiRepo.add(TEST_INFORMATION_DEFAULT);
        Assert.assertEquals(TEST_INFORMATION_DEFAULT.getVideoFolderPath(), tiRepo.getVideoFolderPath());
    }

    @Test
    public void displayDateAndTimeExistInJson() {
        TEST_INFORMATION_DEFAULT.setRecordingTimeMillis(1493624356398L);
        TestInformationRepository tiRepo = new TestInformationRepository();
        tiRepo.add(TEST_INFORMATION_DEFAULT);
        String tiRepoJson = tiRepo.toJson();

        Assert.assertThat(tiRepoJson, CoreMatchers.containsString("displayDateAndTime"));
        Assert.assertThat(tiRepoJson, CoreMatchers.containsString(":39:16"));
    }

    @Test
    public void browserAndPlatformExistInJson() {
        TestInformationRepository tiRepo = new TestInformationRepository();
        tiRepo.add(TEST_INFORMATION_DEFAULT);
        String tiRepoJson = tiRepo.toJson();

        Assert.assertThat(tiRepoJson, CoreMatchers.containsString("browserAndPlatform"));
        Assert.assertThat(tiRepoJson, CoreMatchers.containsString("browser browserVersion, platform"));
    }
}
