# Kafka Security - Guia Completo

## Visão Geral

Este documento explica como funciona a segurança do Kafka implementada neste projeto, cobrindo cada componente, propriedade e conceito envolvido.

---

## 1. Camadas de Segurança do Kafka

O Kafka suporta 3 camadas de segurança independentes:

| Camada | O que protege | Protocolo |
|--------|---------------|-----------|
| **Autenticação (SASL)** | Quem pode conectar ao broker | SCRAM-SHA-256, PLAIN, GSSAPI, OAUTHBEARER |
| **Criptografia (SSL/TLS)** | Tráfego entre cliente e broker | SSL, TLS |
| **Autorização (ACL)** | Quem pode ler/escrever em quais tópicos | ACLs do Kafka |

**Este projeto usa apenas Autenticação (SASL/SCRAM-SHA-256).** SSL/TLS e ACLs podem ser adicionados posteriormente.

---

## 2. O que é SASL?

**SASL** (Simple Authentication and Security Layer) é um framework que separa a autenticação do protocolo de transporte. O Kafka usa SASL para autenticar clientes (producers, consumers) e entre brokers.

### Como funciona na prática:

```
Cliente (Spring Boot)                    Kafka Broker
        |                                       |
        |---- Conexão TCP (porta 9092) -------->|
        |                                       |
        |---- Handshake SASL ------------------>|
        |                                       |
        |---- "Sou o usuário X com senha Y" --->|
        |                                       |
        |<--- "Autenticado com sucesso" --------|
        |                                       |
        |---- Troca de mensagens Kafka -------->|
```

---

## 3. O que é SCRAM-SHA-256?

**SCRAM** (Salted Challenge Response Authentication Mechanism) é um mecanismo de autenticação que:

1. **Nunca envia a senha em texto plano** - usa um protocolo de challenge-response
2. **Usa salt** - cada usuário tem um salt único que previne rainbow tables
3. **SHA-256** - algoritmo de hash usado para criar o salt e o hash da senha

### Fluxo SCRAM:

```
Cliente                              Broker
   |                                    |
   |---- "Quero me autenticar" -------->|
   |                                    |
   |<---- Salt + ServerNonce -----------|
   |                                    |
   |---- ClientProof (hash da senha) -->|
   |                                    |
   |<---- ServerSignature --------------|
   |                                    |
   |  ( ambos verificam o proof )       |
```

**Vantagem sobre PLAIN:** A senha nunca trafega em texto plano, mesmo sem SSL/TLS.

---

## 4. O que é JAAS?

**JAAS** (Java Authentication and Authorization Service) é o framework do Java para autenticação. O Kafka broker usa JAAS para:

1. **Configurar o mecanismo de autenticação** (SCRAM-SHA-256)
2. **Definir as credenciais do broker**
3. **Configurar como o broker se autentica**

### Arquivo JAAS (`kafka_server_jaas.conf`):

```
KafkaServer {
   org.apache.kafka.common.security.scram.ScramLoginModule required
   username="kafka-admin"
   password="kafka-admin-secret";
};
```

**Explicação linha a linha:**

| Componente | Significado |
|------------|-------------|
| `KafkaServer` | Nome do contexto JAAS (fixo para Kafka) |
| `org.apache.kafka.common.security.scram.ScramLoginModule` | Classe Java que implementa SCRAM |
| `required` | O login module é obrigatório (falha se não autenticar) |
| `username="kafka-admin"` | Usuário do broker para autenticação interna |
| `password="kafka-admin-secret"` | Senha do broker |

---

## 5. Propriedades do Docker Compose - Kafka Broker

```yaml
kafka:
  environment:
    # Listener interbroker - como brokers se comunicam entre si
    KAFKA_INTER_BROKER_LISTENER_NAME: SASL_PLAINTEXT
    
    # Mapeamento de protocolos por listener
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: >-
      SASL_PLAINTEXT:SASL_PLAINTEXT,        # Listener interno (docker network)
      SASL_PLAINTEXT_HOST:SASL_PLAINTEXT     # Listener externo (host machine)
    
    # Mecanismo de autenticação entre brokers
    KAFKA_SASL_MECHANISM_INTER_BROKER_PROTOCOL: SCRAM-SHA-256
    
    # Configuração JAAS via variável de ambiente
    KAFKA_OPTS: -Djava.security.auth.login.config=/etc/kafka/kafka_server_jaas.conf
  
  volumes:
    # Monta o arquivo JAAS dentro do container
    - ./config/kafka/kafka_server_jaas.conf:/etc/kafka/kafka_server_jaas.conf
```

### Explicação das Propriedades:

| Propriedade | O que faz |
|-------------|-----------|
| `KAFKA_INTER_BROKER_LISTENER_NAME` | Define qual listener os brokers usam para se comunicar |
| `KAFKA_LISTENER_SECURITY_PROTOCOL_MAP` | Mapeia nome do listener → protocolo de segurança |
| `KAFKA_SASL_MECHANISM_INTER_BROKER_PROTOCOL` | Mecanismo SASL usado entre brokers |
| `KAFKA_OPTS` | Opções JVM, incluindo caminho do arquivo JAAS |
| Volume | Monta o arquivo JAAS no container |

---

## 6. Propriedades do Schema Registry

```yaml
schema-registry:
  environment:
    # Bootstrap servers com SASL
    SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: SASL_PLAINTEXT://kafka:9092
    
    # Protocolo de segurança para conectar ao Kafka
    SCHEMA_REGISTRY_KAFKASTORE_SECURITY_PROTOCOL: SASL_PLAINTEXT
    
    # Mecanismo SASL
    SCHEMA_REGISTRY_KAFKASTORE_SASL_MECHANISM: SCRAM-SHA-256
    
    # Credenciais JAAS
    SCHEMA_REGISTRY_KAFKASTORE_SASL_JAAS_CONFIG: >-
      org.apache.kafka.common.security.scram.ScramLoginModule required
      username="kafka-admin" password="kafka-admin-secret";
```

### Explicação:

| Propriedade | O que faz |
|-------------|-----------|
| `SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS` | Lista de brokers com protocolo SASL |
| `SCHEMA_REGISTRY_KAFKASTORE_SECURITY_PROTOCOL` | Protocolo para conectar ao Kafka |
| `SCHEMA_REGISTRY_KAFKASTORE_SASL_MECHANISM` | Mecanismo de autenticação |
| `SCHEMA_REGISTRY_KAFKASTORE_SASL_JAAS_CONFIG` | Credenciais inline (username/password) |

---

## 7. Propriedades do Spring Boot (Producer - vendas-service)

```yaml
spring:
  kafka:
    producer:
      properties:
        # Protocolo de segurança
        security.protocol: ${KAFKA_SECURITY_PROTOCOL:SASL_PLAINTEXT}
        
        # Mecanismo SASL
        sasl.mechanism: ${KAFKA_SASL_MECHANISM:SCRAM-SHA-256}
        
        # Credenciais JAAS
        sasl.jaas.config: >-
          org.apache.kafka.common.security.scram.ScramLoginModule required
          username="vendas-service" password="vendas-secret";
```

### Explicação:

| Propriedade | O que faz |
|-------------|-----------|
| `security.protocol` | Protocolo de segurança (SASL_PLAINTEXT = SASL sem SSL) |
| `sasl.mechanism` | Mecanismo de autenticação (SCRAM-SHA-256) |
| `sasl.jaas.config` | Configuração JAAS com credenciais do cliente |

---

## 8. Propriedades do Spring Boot (Consumer - transportadora/notificacao)

```yaml
spring:
  kafka:
    consumer:
      properties:
        # Mesmas propriedades do producer
        security.protocol: ${KAFKA_SECURITY_PROTOCOL:SASL_PLAINTEXT}
        sasl.mechanism: ${KAFKA_SASL_MECHANISM:SCRAM-SHA-256}
        sasl.jaas.config: >-
          org.apache.kafka.common.security.scram.ScramLoginModule required
          username="client-group" password="client-secret";
```

**Nota:** Consumers compartilham o mesmo usuário (`client-group`) porque usam o mesmo `group.id`.

---

## 9. Criação de Usuários SCRAM

Os usuários SCRAM precisam ser criados no broker Kafka. O script `init-scram-users.sh` faz isso:

```bash
# Criar usuário kafka-admin (usado pelo broker e Schema Registry)
kafka-configs --bootstrap-server localhost:9092 \
  --alter --add-config 'SCRAM-SHA-256=[password=kafka-admin-secret]' \
  --entity-type users --entity-name kafka-admin

# Criar usuário vendas-service (usado pelo producer)
kafka-configs --bootstrap-server localhost:9092 \
  --alter --add-config 'SCRAM-SHA-256=[password=vendas-secret]' \
  --entity-type users --entity-name vendas-service

# Criar usuário client-group (usado pelos consumers)
kafka-configs --bootstrap-server localhost:9092 \
  --alter --add-config 'SCRAM-SHA-256=[password=client-secret]' \
  entity-type users --entity-name client-group
```

### Explicação:

| Comando | O que faz |
|---------|-----------|
| `kafka-configs` | CLI para gerenciar configurações do Kafka |
| `--alter --add-config` | Adiciona nova configuração (senha SCRAM) |
| `SCRAM-SHA-256=[password=...]` | Define a senha SCRAM para o usuário |
| `--entity-type users` | Tipo de entidade: usuário |
| `--entity-name` | Nome do usuário |

---

## 10. Fluxo Completo de Autenticação

```
1. Kafka Broker inicia com JAAS config
   ↓
2. Cria usuários SCRAM via kafka-configs
   ↓
3. Schema Registry conecta ao Kafka com credenciais
   ↓
4. Spring Boot (vendas) conecta como producer
   ↓
5. Spring Boot (transportadora/notificacao) conecta como consumer
   ↓
6. kafka-ui conecta para visualizar mensagens
```

---

## 11. Usuários e Credenciais

| Usuário | Senha | Usado por |
|---------|-------|-----------|
| `kafka-admin` | `kafka-admin-secret` | Broker, Schema Registry, kafka-ui |
| `vendas-service` | `vendas-secret` | vendas-service (producer) |
| `client-group` | `client-secret` | transportadora-service, notificacao-service (consumers) |

**Nota:** A senha do arquivo JAAS (`kafka_server_jaas.conf`) deve ser igual à criada via `kafka-configs`.

---

## 12. Variáveis de Ambiente no .env

```env
# Credenciais SASL/SCRAM-SHA-256
KAFKA_ADMIN_PASSWORD=kafka-admin-secret
KAFKA_VENDAS_PASSWORD=vendas-secret
KAFKA_CLIENT_PASSWORD=client-secret
```

**Uso no docker-compose.yml:**
- Schema Registry: `${KAFKA_ADMIN_PASSWORD}`
- kafka-ui: `${KAFKA_ADMIN_PASSWORD}`
- vendas-service: `${KAFKA_VENDAS_PASSWORD}`
- transportadora/notificacao: `${KAFKA_CLIENT_PASSWORD}`

---

## 13. Segurança vs. Conveniência

### Com SASL (atual):
- ✅ Autenticação obrigatória
- ✅ Credenciais por serviço
- ✅ Comportamento idêntico ao prod
- ❌ Setup inicial mais complexo
- ❌ Precisa configurar credenciais no IDE para debug local

### Sem SASL (PLAINTEXT):
- ✅ Setup simples
- ✅ Iteração rápida
- ❌ Qualquer um pode conectar
- ❌ Comportamento diferente do prod

---

## 14. Troubleshooting

### Erro: "Authentication failed"
- Verifique se o usuário foi criado via `kafka-configs`
- Verifique se a senha no JAAS conf matches a criada

### Erro: "Could not find a valid JAAS configuration"
- Verifique o caminho do arquivo JAAS no `KAFKA_OPTS`
- Verifique se o volume está montado corretamente

### Erro: "SASL authentication failed"
- Verifique se `security.protocol` está como `SASL_PLAINTEXT`
- Verifique se `sasl.mechanism` está como `SCRAM-SHA-256`

---

## 15. Referências

- [Kafka Security Documentation](https://kafka.apache.org/documentation/#security)
- [SCRAM-SHA-256 Configuration](https://kafka.apache.org/documentation/#security_scram)
- [JAAS Configuration](https://kafka.apache.org/documentation/#security_jaas)
- [Spring Boot Kafka Security](https://docs.spring.io/spring-boot/docs/current/reference/html/application-properties.html#appendix.application-properties.security)
