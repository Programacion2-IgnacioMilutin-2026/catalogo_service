package e2e;

import catalogo.CatalogoApplication;
import catalogo.TestcontainersConfiguration;
import catalogo.catalog.CatalogTables;
import catalogo.catedra.application.service.TechnicalSessionService;
import catalogo.catedra.domain.model.CatalogMetadata;
import catalogo.catedra.domain.model.CatalogMetadataResult;
import catalogo.catedra.domain.model.TechnicalIntegration;
import catalogo.catedra.domain.model.TechnicalSession;
import catalogo.catedra.domain.ports.in.GetCatalogMetadataUseCase;
import catalogo.catedra.infrastructure.kafka.CatalogUpdateListener;
import catalogo.catedra.infrastructure.kafka.CatedraKafkaProperties;
import catalogo.catedra.infrastructure.kafka.KafkaCatalogConsumer;
import catalogo.synchronization.application.service.SynchronizationService;
import catalogo.synchronization.domain.model.FullSyncResult;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// La sincronización incremental contra la cátedra real (API, Redis y Kafka), con un consumer group de pruebas.
@Tag("e2e")
@EnabledIf(value = "e2e.E2ECredentials#kafkaAvailable",
        disabledReason = "No catedra credentials or CATALOGO_E2E_KAFKA_CONSUMER_GROUP in the local .env: skipped")
@SpringBootTest(classes = CatalogoApplication.class)
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class IncrementalSynchronizationE2ETest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int BEHIND = 3;

    @Autowired
    private TechnicalSessionService technicalSessionService;

    @Autowired
    private GetCatalogMetadataUseCase getCatalogMetadataUseCase;

    @Autowired
    private SynchronizationService synchronizationService;

    @Autowired
    private KafkaCatalogConsumer serviceConsumer;

    @Autowired
    private CatedraKafkaProperties catedraKafkaProperties;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("catalogo.catedra.kafka.enabled", () -> "true");
        registry.add("catalogo.catedra.kafka.consumer-group", E2ECredentials::kafkaConsumerGroup);
    }

    @Test
    void aRealNoticeAppliesTheVersionsIncrementallyAndTheSameNoticeAgainChangesNothing(CapturedOutput output)
            throws Exception {
        String testGroup = E2ECredentials.kafkaConsumerGroup();
        await().atMost(Duration.ofSeconds(120)).until(() -> technicalSessionService.getTechnicalSession().isPresent());
        TechnicalSession session = technicalSessionService.getTechnicalSession().orElseThrow();
        TechnicalIntegration integration = session.integration();
        serviceConsumer.destroy();
        String serviceGroup = integration.kafkaConsumerGroupId() + "-catalog";
        TopicPartition partition = new TopicPartition(integration.kafkaCatalogTopic(), 0);
        CatalogTables tables = new CatalogTables(jdbcTemplate);

        try (Admin admin = admin(integration)) {
            Map<TopicPartition, OffsetAndMetadata> serviceGroupBefore = committed(admin, serviceGroup);

            assertThat(synchronizationService.synchronizeFullCatalog()).isInstanceOf(FullSyncResult.Applied.class);
            CatalogMetadata metadata = metadata();
            long behind = metadata.currentVersion() - BEHIND;
            assertThat(behind).as("three versions behind is inside the history")
                    .isGreaterThanOrEqualTo(metadata.oldestAvailableVersion());
            jdbcTemplate.update("UPDATE catalog_sync_state SET local_version = ?", behind);

            long lastOffset = endOffset(admin, partition) - 1;
            assertThat(lastOffset).as("the catalog topic has retained notices").isGreaterThanOrEqualTo(0);
            String eventId = eventIdAt(integration, partition, lastOffset);
            System.out.println("E2E C02: Redis currentVersion " + metadata.currentVersion() + ", local version lowered to "
                    + behind + "; delivering the retained notice at offset " + lastOffset);

            KafkaCatalogConsumer first = deliverAgain(admin, serviceConsumer, testGroup, partition, lastOffset,
                    integration);
            await().atMost(Duration.ofSeconds(120)).until(() -> processed(eventId));
            Map<String, Object> afterFirst = tables.syncState();
            System.out.println("E2E C02: first delivery left local version " + afterFirst.get("local_version")
                    + " with " + afterFirst.get("last_sync_type") + ", " + afterFirst.get("last_sync_result"));
            assertThat(afterFirst)
                    .containsEntry("last_sync_type", "INCREMENTAL")
                    .containsEntry("last_sync_result", "SUCCESS");
            assertThat((Long) afterFirst.get("local_version")).isGreaterThanOrEqualTo(metadata.currentVersion());
            assertThat(output.getOut()).contains("Catalog update notice " + eventId + " applied versions " + (behind + 1)
                    + " to");

            KafkaCatalogConsumer second = deliverAgain(admin, first, testGroup, partition, lastOffset, integration);
            try {
                await().atMost(Duration.ofSeconds(120)).until(() -> output.getOut()
                        .contains("Catalog update notice " + eventId + " was already processed: nothing to do"));
                assertThat(tables.syncState()).isEqualTo(afterFirst);
                System.out.println("E2E C02: second delivery of the same notice changed nothing");
            } finally {
                second.destroy();
            }

            assertThat(committed(admin, serviceGroup)).as("the service consumer group is untouched")
                    .isEqualTo(serviceGroupBefore);
        }

        assertThat(output)
                .doesNotContain(session.token())
                .doesNotContain(session.integration().redisPassword())
                .doesNotContain(E2ECredentials.password());
    }

    private KafkaCatalogConsumer deliverAgain(Admin admin, KafkaCatalogConsumer current, String testGroup,
                                              TopicPartition partition, long offset, TechnicalIntegration integration) {
        current.destroy();
        await().atMost(Duration.ofSeconds(60)).until(() -> withoutMembers(admin, testGroup));
        try {
            admin.alterConsumerGroupOffsets(testGroup, Map.of(partition, new OffsetAndMetadata(offset))).all()
                    .get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        KafkaCatalogConsumer next = new KafkaCatalogConsumer(catedraKafkaProperties,
                applicationContext.getBeanProvider(CatalogUpdateListener.class));
        next.integrationEnabled(integration);
        return next;
    }

    private CatalogMetadata metadata() {
        CatalogMetadataResult result = getCatalogMetadataUseCase.getCatalogMetadata();
        assertThat(result).as("metadata read by the test").isInstanceOf(CatalogMetadataResult.Obtained.class);
        return ((CatalogMetadataResult.Obtained) result).metadata();
    }

    private boolean processed(String eventId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class,
                eventId) == 1;
    }

    private static String eventIdAt(TechnicalIntegration integration, TopicPartition partition, long offset) {
        try (KafkaConsumer<String, String> reader = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, integration.kafkaBootstrapServers(),
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            reader.assign(List.of(partition));
            reader.seek(partition, offset);
            long giveUpAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (System.nanoTime() < giveUpAt) {
                for (ConsumerRecord<String, String> message : reader.poll(Duration.ofMillis(500))) {
                    if (message.offset() == offset) {
                        return JSON.readTree(message.value()).path("eventId").asString();
                    }
                }
            }
        }
        throw new IllegalStateException("The retained notice at offset " + offset + " could not be read");
    }

    private static long endOffset(Admin admin, TopicPartition partition) throws Exception {
        return admin.listOffsets(Map.of(partition, OffsetSpec.latest())).partitionResult(partition)
                .get(30, TimeUnit.SECONDS).offset();
    }

    private static Map<TopicPartition, OffsetAndMetadata> committed(Admin admin, String group) throws Exception {
        return admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(30, TimeUnit.SECONDS);
    }

    private static boolean withoutMembers(Admin admin, String group) {
        try {
            ConsumerGroupDescription description = admin.describeConsumerGroups(List.of(group)).all()
                    .get(30, TimeUnit.SECONDS).get(group);
            return description.members().isEmpty();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof GroupIdNotFoundException) {
                return true;
            }
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Admin admin(TechnicalIntegration integration) {
        return Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, integration.kafkaBootstrapServers()));
    }
}
