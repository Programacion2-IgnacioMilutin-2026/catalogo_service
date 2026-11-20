package catalogo.catedra.infrastructure.kafka;

import catalogo.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "catalogo.catedra.kafka.enabled=true")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class KafkaStartupIntegrationTest {

    @Autowired
    private KafkaCatalogConsumer kafkaCatalogConsumer;

    @Test
    void theServiceStartsWithoutConnectingToADefaultKafkaAndWithoutSessionDoesNotConsume(CapturedOutput output)
            throws InterruptedException {
        Thread.sleep(1000);

        assertThat(kafkaCatalogConsumer.consuming()).isFalse();
        assertThat(output.getOut())
                .doesNotContain("localhost:9092")
                .doesNotContain("Connection to node")
                .doesNotContain("Consuming the catalog topic");
    }
}
