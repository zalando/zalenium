package de.zalando.ep.zalenium.container;

import de.zalando.ep.zalenium.util.DockerContainerMock;
import java.util.HashMap;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;


public class DockerContainerClientTest {

  private DockerContainerClient containerClient;

  @BeforeTest
  public void prepare() {
    containerClient =
        DockerContainerMock.getMockedDockerContainerClient();
  }


  @Test(threadPoolSize = 999, invocationCount = 999, timeOut = 10000)
  @Parameters()
  public void createContainer() {
    HashMap<String, String> envVars = new HashMap<>();
    envVars.put("NOVNC_PORT", "50000");
    containerClient
        .createContainer(
            "zalenium",
            "elgalu/selenium",
            envVars,
            "40000");
  }
}