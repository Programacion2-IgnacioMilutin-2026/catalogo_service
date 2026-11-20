package catalogo.catedra.infrastructure.kafka;

import catalogo.catedra.domain.model.TechnicalIntegration;
import catalogo.catedra.domain.ports.out.TechnicalIntegrationListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

// Arma el consumo del topic del catálogo de kafka.
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaCatalogConsumer implements TechnicalIntegrationListener, DisposableBean {

    private static final String CONSUMER_GROUP_SUFFIX = "-catalog";
    private static final int MAX_RECONNECT_BACKOFF_MS = 30_000;

    private final CatedraKafkaProperties catedraKafkaProperties;
    private final ObjectProvider<CatalogUpdateListener> catalogUpdateListener;

    private TechnicalIntegration connectedWith;
    private KafkaMessageListenerContainer<String, String> container;

    @Override
    public synchronized void integrationEnabled(TechnicalIntegration integration) {
        if (!catedraKafkaProperties.enabled()) {
            log.debug("Kafka consumption of the catalog topic is disabled");
            return;
        }
        if (connectedWith != null) {
            if (!sameKafka(connectedWith, integration)) {
                log.warn("A later technical login reported other Kafka settings; the catalog topic keeps being "
                        + "consumed with the first ones until the service restarts");
            }
            return;
        }
        connectedWith = integration;
        container = createContainer(integration);
        KafkaMessageListenerContainer<String, String> starting = container;
        Thread.ofVirtual().name("catalog-kafka-start").start(() -> start(starting, integration));
    }

    private synchronized void start(KafkaMessageListenerContainer<String, String> starting,
                                    TechnicalIntegration integration) {
        if (container != starting) {
            return;
        }
        try {
            starting.start();
            log.info("Consuming the catalog topic {} with consumer group {}", integration.kafkaCatalogTopic(),
                    consumerGroup(integration));
        } catch (RuntimeException e) {
            log.error("Kafka consumption of the catalog topic could not start: {}", e.getClass().getSimpleName());
            connectedWith = null;
            container = null;
        }
    }

    @Override
    public synchronized void destroy() {
        if (container != null) {
            container.stop();
        }
    }

    synchronized boolean consuming() {
        return container != null && container.isRunning();
    }

    Map<String, Object> consumerConfiguration(TechnicalIntegration integration) {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, integration.kafkaBootstrapServers());
        configuration.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup(integration));
        configuration.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        configuration.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configuration.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
        configuration.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG,
                (int) catedraKafkaProperties.maxPollInterval().toMillis());
        configuration.put(CommonClientConfigs.RECONNECT_BACKOFF_MAX_MS_CONFIG, MAX_RECONNECT_BACKOFF_MS);
        configuration.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configuration.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return configuration;
    }

    String consumerGroup(TechnicalIntegration integration) {
        return catedraKafkaProperties.consumerGroup() != null
                ? catedraKafkaProperties.consumerGroup()
                : integration.kafkaConsumerGroupId() + CONSUMER_GROUP_SUFFIX;
    }

    private KafkaMessageListenerContainer<String, String> createContainer(TechnicalIntegration integration) {
        ContainerProperties containerProperties = new ContainerProperties(integration.kafkaCatalogTopic());
        containerProperties.setAckMode(ContainerProperties.AckMode.RECORD);
        containerProperties.setMessageListener(
                (MessageListener<String, String>) catalogUpdateListener.getObject()::onMessage);
        KafkaMessageListenerContainer<String, String> created = new KafkaMessageListenerContainer<>(
                new DefaultKafkaConsumerFactory<>(consumerConfiguration(integration)), containerProperties);
        created.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(0L, 0L)));
        created.setBeanName("catalog-updates");
        return created;
    }

    private static boolean sameKafka(TechnicalIntegration first, TechnicalIntegration later) {
        return Objects.equals(first.kafkaBootstrapServers(), later.kafkaBootstrapServers())
                && Objects.equals(first.kafkaCatalogTopic(), later.kafkaCatalogTopic())
                && Objects.equals(first.kafkaConsumerGroupId(), later.kafkaConsumerGroupId());
    }
}
