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
    private static Supplier<DockerContainerClient> dockerContainerClient = DockerContainerClient::new;
    
    public static ContainerClient getContainerClient() {

        if (isKubernetes.get()) {
            return createKubernetesContainerClient();
        }
        else {
            return createDockerContainerClient();
        }
    }

    private static DockerContainerClient createDockerContainerClient() {
        // We actually need one client per proxy because sometimes the default size of the connection pool in the
        // DockerClient is not big enough to handle everything when more than 40 proxies are running.
        DockerContainerClient dockerClient = ContainerFactory.dockerContainerClient.get();
        dockerClient.initialiseContainerEnvironment();
        return dockerClient;
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
    public static void setDockerContainerClient(Supplier<DockerContainerClient> dockerContainerClient) {
        ContainerFactory.dockerContainerClient = dockerContainerClient;
    }

    @VisibleForTesting
    public static Supplier<DockerContainerClient> getDockerContainerClient() {
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
