package de.zalando.ep.zalenium.container;

import java.io.File;
import java.util.function.Supplier;

import com.google.common.annotations.VisibleForTesting;

import de.zalando.ep.zalenium.container.kubernetes.KubernetesContainerClient;
import de.zalando.ep.zalenium.util.Environment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;

public class ContainerFactory {

    private static Supplier<Boolean> isKubernetes = () -> new File("/var/run/secrets/kubernetes.io/serviceaccount/token").canRead();

    private static KubernetesContainerClient kubernetesContainerClient;
    private static DockerContainerClient dockerContainerClient;
    
    public static ContainerClient getContainerClient() {

        if (isKubernetes.get()) {
            return createKubernetesContainerClient();
        }
        else {
            return createDockerContainerClient();
        }
    }

    private static DockerContainerClient createDockerContainerClient() {
        // It should be enough to have a single client for docker as well.
        if (dockerContainerClient == null) {
            synchronized (ContainerFactory.class) {
                if (dockerContainerClient == null) {
                    dockerContainerClient = new DockerContainerClient();
                    dockerContainerClient.initialiseContainerEnvironment();
                }
            }
        }
        return dockerContainerClient;
    }
    
    private static KubernetesContainerClient createKubernetesContainerClient() {
        // We only want one kubernetes client because it creates lots of thread pools and such things
        // so lets cache a copy of it in this factory.
        if (kubernetesContainerClient == null) {
            synchronized (ContainerFactory.class) {
                if (kubernetesContainerClient == null) {
                    kubernetesContainerClient = new KubernetesContainerClient(new Environment(),
                            KubernetesContainerClient::createDoneablePodDefaultImpl,
                            new DefaultKubernetesClient());
                    kubernetesContainerClient.initialiseContainerEnvironment();
                }
            }
        }
        return kubernetesContainerClient;
    }

    @VisibleForTesting
    public static void setDockerContainerClient(DockerContainerClient dockerContainerClient) {
        ContainerFactory.dockerContainerClient = dockerContainerClient;
    }

    @VisibleForTesting
    public static DockerContainerClient getDockerContainerClient() {
        return dockerContainerClient;
    }

    @VisibleForTesting
    public static Supplier<Boolean> getIsKubernetes() {
        return isKubernetes;
    }

    @VisibleForTesting
    public static void setIsKubernetes(Supplier<Boolean> isKubernetes) {
        ContainerFactory.isKubernetes = isKubernetes;
    }

    @VisibleForTesting
    public static KubernetesContainerClient getKubernetesContainerClient() {
        return kubernetesContainerClient;
    }

    @VisibleForTesting
    public static void setKubernetesContainerClient(KubernetesContainerClient kubernetesContainerClient) {
        ContainerFactory.kubernetesContainerClient = kubernetesContainerClient;
    }

}
