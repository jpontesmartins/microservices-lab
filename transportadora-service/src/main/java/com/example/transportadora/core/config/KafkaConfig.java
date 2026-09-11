package com.example.transportadora.core.config;

import com.example.transportadora.core.dto.PedidoCriadoEvent;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

/**
 * Configuracao Kafka do transportadora-service.
 * Utiliza auto-configuration do Spring Boot para as props basicas
 * (bootstrap-servers, group-id, schema.registry.url) definidas em application.yml.
 * Customiza apenas o deserializer com ErrorHandlingDeserializer para
 * evitar problemas de deserializacao com type headers de outros servicos.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PedidoCriadoEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, PedidoCriadoEvent> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, PedidoCriadoEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler());
        return factory;
    }

    @Bean
    public ConsumerFactory<String, PedidoCriadoEvent> consumerFactory(
            org.springframework.boot.autoconfigure.kafka.KafkaProperties properties) {
        Map<String, Object> props = properties.buildConsumerProperties();

        KafkaJsonSchemaDeserializer<PedidoCriadoEvent> jsonDeserializer =
                new KafkaJsonSchemaDeserializer<>();

        return new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(jsonDeserializer));
    }

    private DefaultErrorHandler errorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(0L, 0L));
    }
}
