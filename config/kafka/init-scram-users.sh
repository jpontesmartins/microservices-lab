#!/bin/bash
set -e

echo "Aguardando Kafka ficar pronto..."
sleep 15

echo "Criando usuarios SCRAM-SHA-256..."

kafka-configs --bootstrap-server localhost:9092 \
  --alter --add-config 'SCRAM-SHA-256=[password=kafka-admin-secret]' \
  --entity-type users --entity-name kafka-admin 2>/dev/null || \
  echo "User kafka-admin ja existe ou erro ao criar"

kafka-configs --bootstrap-server localhost:9092 \
  --alter --add-config 'SCRAM-SHA-256=[password=vendas-secret]' \
  --entity-type users --entity-name vendas-service 2>/dev/null || \
  echo "User vendas-service ja existe ou erro ao criar"

kafka-configs --bootstrap-server localhost:9092 \
  --alter --add-config 'SCRAM-SHA-256=[password=client-secret]' \
  --entity-type users --entity-name client-group 2>/dev/null || \
  echo "User client-group ja existe ou erro ao criar"

echo "Usuarios SCRAM criados com sucesso!"
