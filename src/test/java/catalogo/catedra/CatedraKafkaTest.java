package catalogo.catedra;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// El Kafka de test y su topic del catálogo.
class CatedraKafkaTest {

    @Test
    void aPublishedNoticeIsReadAndTheCommittedPositionIsReported() {
        CatalogTopic topic = CatalogTopic.random("kafka-test");
        topic.create();
        assertThat(topic.isAssignedTo(topic.serviceConsumerGroup())).isFalse();
        assertThat(topic.committedOffset(topic.serviceConsumerGroup())).isEqualTo(-1);

        topic.publishNotice("event-1", 368);

        try (KafkaConsumer<String, String> consumer = consumer(topic.serviceConsumerGroup())) {
            consumer.subscribe(List.of(topic.topic()));
            List<ConsumerRecord<String, String>> received = new ArrayList<>();
            long giveUpAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (received.isEmpty() && System.nanoTime() < giveUpAt) {
                consumer.poll(Duration.ofMillis(200)).forEach(received::add);
            }
            assertThat(received).singleElement().satisfies(message -> {
                assertThat(message.key()).isEqualTo("368");
                assertThat(message.value()).isEqualTo(CatalogTopic.notice("event-1", 368));
            });
            assertThat(topic.isAssignedTo(topic.serviceConsumerGroup())).isTrue();
            consumer.commitSync();
        }

        assertThat(topic.endOffset()).isEqualTo(1);
        assertThat(topic.committedOffset(topic.serviceConsumerGroup())).isEqualTo(1);
        assertThat(topic.serviceConsumerGroup()).isEqualTo(topic.suggestedConsumerGroup() + "-catalog");
    }

    @Test
    void aPausedKafkaDoesNotAnswerAndAnswersAgainWhenResumed() {
        CatalogTopic topic = CatalogTopic.random("kafka-pause");
        topic.create();

        CatedraKafka.pause();
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                CatedraKafka.bootstrapServers(), AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 2000,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 1000))) {
            assertThatThrownBy(() -> admin.listTopics().names().get(5, TimeUnit.SECONDS)).isNotNull();
        } finally {
            CatedraKafka.unpause();
        }

        topic.publishNotice("event-after-pause", 369);
        assertThat(topic.endOffset()).isEqualTo(1);
    }

    private static KafkaConsumer<String, String> consumer(String consumerGroup) {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, CatedraKafka.bootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, consumerGroup,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }
}
