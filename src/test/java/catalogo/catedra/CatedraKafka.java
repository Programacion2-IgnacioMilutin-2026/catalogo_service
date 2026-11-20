package catalogo.catedra;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.KafkaContainer;

// Un Kafka real en Docker, compartido por toda la suite. Cada test usa su propio topic y su propio consumer group.
public final class CatedraKafka {

    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.1");

    private CatedraKafka() {
    }

    public static synchronized String bootstrapServers() {
        if (!KAFKA.isRunning()) {
            KAFKA.start();
        }
        return KAFKA.getBootstrapServers();
    }

    // Congela el contenedor, como un Kafka que deja de responder.
    public static void pause() {
        DockerClientFactory.instance().client().pauseContainerCmd(KAFKA.getContainerId()).exec();
    }

    public static void unpause() {
        DockerClientFactory.instance().client().unpauseContainerCmd(KAFKA.getContainerId()).exec();
    }
}
