package catalogo.catedra;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

// Un topic del catálogo propio de un test, en el Kafka de Testcontainers.
public record CatalogTopic(String topic, String suggestedConsumerGroup) {

    public static CatalogTopic random(String name) {
        String suffix = name + "-" + UUID.randomUUID();
        return new CatalogTopic("catedra.catalog." + suffix, "alumnos-" + suffix);
    }

    // El consumer group con el que lee el servicio.
    public String serviceConsumerGroup() {
        return suggestedConsumerGroup + "-catalog";
    }

    public void create() {
        try (Admin admin = admin()) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void publish(String key, String value) {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, CatedraKafka.bootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.send(new ProducerRecord<>(topic, key, value)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // Un aviso con el formato real del topic.
    public void publishNotice(String eventId, long newVersion) {
        publish(String.valueOf(newVersion), notice(eventId, newVersion));
    }

    public static String notice(String eventId, long newVersion) {
        return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"CatalogUpdated\",\"newVersion\":" + newVersion
                + ",\"occurredAt\":\"2026-11-19T15:18:49.018998134Z\",\"source\":\"SCHEDULED\",\"schemaVersion\":1}";
    }

    // La posición que confirmó el consumer group.
    public long committedOffset(String consumerGroup) {
        try (Admin admin = admin()) {
            OffsetAndMetadata offset = admin.listConsumerGroupOffsets(consumerGroup).partitionsToOffsetAndMetadata()
                    .get(10, TimeUnit.SECONDS).get(partition());
            return offset == null ? -1 : offset.offset();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // Cuántos mensajes se publicaron en el topic.
    public long endOffset() {
        try (Admin admin = admin()) {
            return admin.listOffsets(Map.of(partition(), OffsetSpec.latest())).partitionResult(partition())
                    .get(10, TimeUnit.SECONDS).offset();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // Si algún miembro del consumer group tiene asignado el topic.
    public boolean isAssignedTo(String consumerGroup) {
        try (Admin admin = admin()) {
            ConsumerGroupDescription group = admin.describeConsumerGroups(List.of(consumerGroup)).all()
                    .get(10, TimeUnit.SECONDS).get(consumerGroup);
            return group.members().stream()
                    .anyMatch(member -> member.assignment().topicPartitions().contains(partition()));
        } catch (ExecutionException e) {
            if (e.getCause() instanceof GroupIdNotFoundException) {
                return false;
            }
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private TopicPartition partition() {
        return new TopicPartition(topic, 0);
    }

    private static Admin admin() {
        return Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, CatedraKafka.bootstrapServers()));
    }
}
