package de.zalando.ep.zalenium.container;

import java.io.File;
import java.util.function.Supplier;

import com.google.common.annotations.VisibleForTesting;

import de.zalando.ep.zalenium.container.kubernetes.KubernetesContainerClient;
import de.zalando.ep.zalenium.util.Environment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;

public class ContainerFactory {

    private static Supplier<ContainerClient> containerClientGenerator = DockerContainerClient::new;
    private static Supplier<Boolean> isKubernetes = () -> new File("/var/run/secrets/kubernetes.io/serviceaccount/token").canRead();

    private static KubernetesContainerClient kubernetesContainerClient;
    
    public static ContainerClient getContainerClient() {

        if (isKubernetes.get()) {
            return createKubernetesContainerClient();
        }
        else {
            return containerClientGenerator.get();
        }
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
    public static void setContainerClientGenerator(Supplier<ContainerClient> containerClientGenerator) {
        ContainerFactory.containerClientGenerator = containerClientGenerator;
    }

    @VisibleForTesting
    public static Supplier<ContainerClient> getContainerClientGenerator() {
        return containerClientGenerator;
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
