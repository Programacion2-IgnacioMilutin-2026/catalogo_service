package catalogo.catedra.infrastructure.kafka;

import catalogo.catedra.application.service.CatalogUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

// Recibe cada mensaje del topic del catálogo. Lo lee y lo entrega a catedra.
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogUpdateListener {

    private final CatalogUpdateReader catalogUpdateReader;
    private final CatalogUpdateService catalogUpdateService;

    public void onMessage(ConsumerRecord<String, String> message) {
        try {
            switch (catalogUpdateReader.read(message.value())) {
                case CatalogUpdateReading.Valid valid -> catalogUpdateService.receiveCatalogUpdate(valid.notice());
                case CatalogUpdateReading.Invalid invalid -> log.warn(
                        "Catalog notice at partition {} offset {} discarded (eventId={}): {}",
                        message.partition(), message.offset(), invalid.eventId(), invalid.reason());
            }
        } catch (RuntimeException e) {
            log.error("Catalog notice at partition {} offset {} could not be processed: {}",
                    message.partition(), message.offset(), e.getClass().getSimpleName());
        }
    }
}
