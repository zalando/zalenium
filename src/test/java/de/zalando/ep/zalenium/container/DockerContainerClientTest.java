package de.zalando.ep.zalenium.container;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.ContainerConfig;
import de.zalando.ep.zalenium.util.DockerContainerMock;
import java.util.HashMap;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;


public class DockerContainerClientTest {

  private DockerClient dockerClient;
  private String zaleniumContainerId;
  private DockerContainerClient containerClient;

  @BeforeTest
  public void prepare() throws Exception {
    dockerClient = new DefaultDockerClient("unix:///var/run/docker.sock");
    dockerClient.pull("dosel/zalenium:latest");
    dockerClient.pull("elgalu/selenium:latest");

    ContainerConfig containerConfig =
        ContainerConfig
            .builder()
            .image("dosel/zalenium")
            .build();

    zaleniumContainerId = dockerClient.createContainer(containerConfig, "zalenium").id();

    containerClient =
        DockerContainerMock.getMockedDockerContainerClient();
  }

  @AfterTest
  public void cleanUp() throws Exception {
    dockerClient.removeContainer(zaleniumContainerId);
    dockerClient.removeImage("dosel/zalenium:latest");
    dockerClient.removeImage("elgalu/selenium:latest");
  }

  @Test(threadPoolSize = 999, invocationCount = 999, timeOut = 10000)
  @Parameters()
  public void createContainer() throws Exception {
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