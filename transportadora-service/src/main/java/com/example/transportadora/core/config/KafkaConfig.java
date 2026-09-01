package com.example.transportadora.core.config;

import com.example.transportadora.core.dto.PedidoCriadoEvent;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuracao Kafka do transportadora-service.
 * Configura o ErrorHandlingDeserializer com o tipo alvo correto,
 * evitando problemas de deserializacao com type headers de outros servicos.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PedidoCriadoEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PedidoCriadoEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setCommonErrorHandler(errorHandler());
        return factory;
    }

    private ConsumerFactory<String, PedidoCriadoEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("group.id", "transportadora-group");
        props.put("auto.offset.reset", "earliest");
        props.put("key.deserializer", StringDeserializer.class);
        props.put("schema.registry.url", System.getenv().getOrDefault("SCHEMA_REGISTRY_URL", "http://localhost:8087"));
        props.put("json.value.type", PedidoCriadoEvent.class.getName());

        KafkaJsonSchemaDeserializer jsonDeserializer =
                new KafkaJsonSchemaDeserializer();

        return new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(jsonDeserializer));
    }

    private DefaultErrorHandler errorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(0L, 0L));
    }
}
