package catalogo.catedra.infrastructure.kafka;

import catalogo.catedra.application.service.CatalogUpdateService;
import catalogo.catedra.domain.model.ProvisioningStatus;
import catalogo.catedra.domain.model.TechnicalIntegration;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// El consumidor del topic del catálogo, sin Kafka.
@ExtendWith(OutputCaptureExtension.class)
class KafkaCatalogConsumerTest {

    // Un puerto donde no escucha nadie. El consumo arranca y el cliente intenta conectarse, sin Kafka de verdad.
    private static final TechnicalIntegration FIRST = integration("localhost:1", "catedra.catalog.proyecto-de-prueba");
    private static final TechnicalIntegration OTHER = integration("localhost:2", "catedra.catalog.otro");

    private KafkaCatalogConsumer consumer;

    @AfterEach
    void stopConsumption() {
        if (consumer != null) {
            consumer.destroy();
        }
    }

    @Test
    void theConsumerGroupIsTheSuggestedOneWithTheCatalogSuffix() {
        consumer = consumer(new CatedraKafkaProperties(true, Duration.ofMinutes(10), null));

        Map<String, Object> configuration = consumer.consumerConfiguration(FIRST);

        assertThat(configuration)
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1")
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "alumnos-proyecto-de-prueba-catalog")
                .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
                .containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
                .containsEntry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1)
                .containsEntry(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 600_000)
                .containsEntry(CommonClientConfigs.RECONNECT_BACKOFF_MAX_MS_CONFIG, 30_000);
    }

    @Test
    void theMaximumIntervalBetweenReadsIsConfigurable() {
        consumer = consumer(new CatedraKafkaProperties(true, Duration.ofMinutes(3), null));

        assertThat(consumer.consumerConfiguration(FIRST))
                .containsEntry(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 180_000);
    }

    @Test
    void aConsumerGroupForTheTestsReplacesTheServiceOne() {
        consumer = consumer(new CatedraKafkaProperties(true, Duration.ofMinutes(10), "alumnos-proyecto-de-prueba-e2e"));

        assertThat(consumer.consumerConfiguration(FIRST))
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "alumnos-proyecto-de-prueba-e2e");
    }

    @Test
    void emptyPropertiesKeepTheirDefaults() {
        CatedraKafkaProperties properties = new CatedraKafkaProperties(null, null, " ");

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.maxPollInterval()).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.consumerGroup()).isNull();
    }

    @Test
    void withConsumptionDisabledAnEnabledIntegrationStartsNothing() throws InterruptedException {
        consumer = consumer(new CatedraKafkaProperties(false, Duration.ofMinutes(10), null));

        consumer.integrationEnabled(FIRST);
        Thread.sleep(300);

        assertThat(consumer.consuming()).isFalse();
    }

    @Test
    void theFirstIntegrationStartsTheConsumptionAndALaterOneWithOtherDataOnlyWarns(CapturedOutput output)
            throws InterruptedException {
        consumer = consumer(new CatedraKafkaProperties(true, Duration.ofMinutes(10), null));

        consumer.integrationEnabled(FIRST);
        awaitConsuming();
        consumer.integrationEnabled(OTHER);
        consumer.integrationEnabled(FIRST);

        assertThat(consumer.consuming()).isTrue();
        assertThat(output)
                .contains("Consuming the catalog topic catedra.catalog.proyecto-de-prueba with consumer group "
                        + "alumnos-proyecto-de-prueba-catalog")
                .doesNotContain("catedra.catalog.otro");
        assertThat(output.getOut().split("A later technical login reported other Kafka settings", -1)).hasSize(2);

        consumer.destroy();
        assertThat(consumer.consuming()).isFalse();
    }

    // Con Kafka rechazando la conexión, el cliente reintenta con esperas que crecen hasta 30 s: en 15 s dejó 9
    // warnings. Con la espera máxima por defecto (1 s) dejó 15.
    @Test
    void withKafkaRefusingTheConnectionTheReconnectionWarningsAreSpacedOut(CapturedOutput output)
            throws InterruptedException {
        consumer = consumer(new CatedraKafkaProperties(true, Duration.ofMinutes(10), null));

        consumer.integrationEnabled(FIRST);
        awaitConsuming();
        Thread.sleep(15_000);

        long warnings = output.getOut().lines()
                .filter(line -> line.contains("Connection to node") && line.contains("could not be established"))
                .count();
        System.out.println("Reconnection warnings in 15 s: " + warnings);
        assertThat(warnings).isPositive().isLessThanOrEqualTo(11);
    }

    private void awaitConsuming() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!consumer.consuming() && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertThat(consumer.consuming()).isTrue();
    }

    private static KafkaCatalogConsumer consumer(CatedraKafkaProperties properties) {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("catalogUpdateListener",
                new CatalogUpdateListener(new CatalogUpdateReader(), new CatalogUpdateService(notice -> {
                })));
        ObjectProvider<CatalogUpdateListener> listener = beans.getBeanProvider(CatalogUpdateListener.class);
        return new KafkaCatalogConsumer(properties, listener);
    }

    private static TechnicalIntegration integration(String bootstrapServers, String catalogTopic) {
        return new TechnicalIntegration("proyecto-de-prueba", "redis.catedra.internal", 6379,
                "grp_proyecto-de-prueba", "test-redis-password", bootstrapServers, "alumnos-proyecto-de-prueba",
                catalogTopic, ProvisioningStatus.PROVISIONED);
    }
}
