package de.zalando.ep.zalenium.container;

import java.io.File;

public class ContainerFactory {

    public static ContainerClient getContainerClient() {
        /*
            Here we can start writing some logic that will decide which type of client will be used to
            create more docker-selenium containers.
            When the KubernetesClient is implemented, some IFs need to be added and then just an invocation to
            something like: "return new KubernetesClient();"
         */
        File kubernetesServiceAccountFile = new File("/var/run/secrets/kubernetes.io/serviceaccount/token");
        if (kubernetesServiceAccountFile.canRead()) {
            return KubernetesContainerClient.getInstance();
        }
        else {
            return new DockerContainerClient();
        }
    }

}
