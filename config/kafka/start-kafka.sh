#!/bin/bash
set -e

echo "Aguardando ZooKeeper..."
sleep 15

# Gera as configs do Kafka (com SASL)
/etc/confluent/docker/configure

# === FASE 1: Inicia broker SEM SASL para criar usuarios ===
echo "=== FASE 1: Iniciando broker sem SASL para criar usuarios ==="
cp /etc/kafka/kafka.properties /etc/kafka/kafka.properties.bak
sed -i 's|^listeners=.*|listeners=PLAINTEXT://0.0.0.0:9092,PLAINTEXT_HOST://0.0.0.0:29092|' /etc/kafka/kafka.properties
sed -i 's|^advertised.listeners=.*|advertised.listeners=PLAINTEXT://kafka:9092,PLAINTEXT_HOST://localhost:29092|' /etc/kafka/kafka.properties
sed -i 's|^listener.security.protocol.map=.*|listener.security.protocol.map=PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT|' /etc/kafka/kafka.properties
sed -i 's|^inter.broker.listener.name=.*|inter.broker.listener.name=PLAINTEXT|' /etc/kafka/kafka.properties
sed -i 's|^sasl.enabled.mechanisms=.*|sasl.enabled.mechanisms=|' /etc/kafka/kafka.properties
sed -i 's|^sasl.mechanism.inter.broker.protocol=.*|sasl.mechanism.inter.broker.protocol=|' /etc/kafka/kafka.properties
unset KAFKA_OPTS

# Inicia broker
/etc/confluent/docker/launch &
KAFKA_PID=$!

echo "Aguardando broker sem SASL ficar pronto..."
for i in $(seq 1 30); do
  if kafka-broker-api-versions --bootstrap-server localhost:9092 > /dev/null 2>&1; then
    echo "Broker pronto (sem SASL)!"
    break
  fi
  echo "  Aguardando... ($i/30)"
  sleep 3
done

# Cria usuarios SCRAM
echo "Criando usuarios SCRAM-SHA-256..."
kafka-configs --bootstrap-server localhost:9092 \
  --alter --add-config 'SCRAM-SHA-256=[password=kafka-admin-secret]' \
  --entity-type users --entity-name kafka-admin 2>/dev/null && echo "  kafka-admin: OK" || echo "  kafka-admin: ja existe"

kafka-configs --bootstrap-server localhost:9092 \
  --alter --add-config 'SCRAM-SHA-256=[password=vendas-secret]' \
  --entity-type users --entity-name vendas-service 2>/dev/null && echo "  vendas-service: OK" || echo "  vendas-service: ja existe"

kafka-configs --bootstrap-server localhost:9092 \
  --alter --add-config 'SCRAM-SHA-256=[password=client-secret]' \
  --entity-type users --entity-name client-group 2>/dev/null && echo "  client-group: OK" || echo "  client-group: ja existe"

echo "Usuarios SCRAM criados!"

# Para o broker sem SASL
echo "Parando broker (sem SASL)..."
kill $KAFKA_PID 2>/dev/null || true
wait $KAFKA_PID 2>/dev/null || true
sleep 10

# === FASE 2: Inicia broker COM SASL ===
echo "=== FASE 2: Iniciando broker com SASL ==="
cp /etc/kafka/kafka.properties.bak /etc/kafka/kafka.properties
export KAFKA_OPTS="-Djava.security.auth.login.config=/etc/kafka/kafka_server_jaas.conf"

exec /etc/confluent/docker/launch
