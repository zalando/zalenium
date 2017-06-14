package de.zalando.ep.zalenium.container;

import java.io.File;
import java.util.function.Supplier;

import com.google.common.annotations.VisibleForTesting;

import de.zalando.ep.zalenium.container.kubernetes.KubernetesContainerClient;
import de.zalando.ep.zalenium.util.Environment;

public class ContainerFactory {

    private static Supplier<ContainerClient> dockerContainerClientGenerator = () -> new DockerContainerClient();
    private static Supplier<ContainerClient> kubernetesContainerClientGenerator =
            () -> KubernetesContainerClient.getInstance(new Environment(),
                                                        KubernetesContainerClient::createDoneablePodDefaultImpl,
                                                        KubernetesContainerClient::createDonableServiceDefaultImpl);
    
    public static ContainerClient getContainerClient() {
        /*
            Here we can start writing some logic that will decide which type of client will be used to
            create more docker-selenium containers.
            When the KubernetesClient is implemented, some IFs need to be added and then just an invocation to
            something like: "return new KubernetesClient();"
         */
        File kubernetesServiceAccountFile = new File("/var/run/secrets/kubernetes.io/serviceaccount/token");
        if (kubernetesServiceAccountFile.canRead()) {
            return kubernetesContainerClientGenerator.get();
        }
        else {
            return dockerContainerClientGenerator.get();
        }
    }

    @VisibleForTesting
    public static void setDockerContainerClientGenerator(Supplier<ContainerClient> dockerContainerClientGenerator) {
        ContainerFactory.dockerContainerClientGenerator = dockerContainerClientGenerator;
    }

    @VisibleForTesting
    public static void setKubernetesContainerClientGenerator(Supplier<ContainerClient> kubernetesContainerClientGenerator) {
        ContainerFactory.kubernetesContainerClientGenerator = kubernetesContainerClientGenerator;
    }

    @VisibleForTesting
    public static Supplier<ContainerClient> getDockerContainerClientGenerator() {
        return dockerContainerClientGenerator;
    }

    @VisibleForTesting
    public static Supplier<ContainerClient> getKubernetesContainerClientGenerator() {
        return kubernetesContainerClientGenerator;
    }
    
    

}
