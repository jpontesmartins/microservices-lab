# Microservices (Eureka + Gateway) - Java 17

Este repositorio contem 5 aplicacoes Spring Boot independentes, para estudo de Service Discovery (Netflix Eureka) e API Gateway (Spring Cloud Gateway).

## Projetos

### `discovery-server`

Funcao: servidor Eureka (service registry).

- Mantem um "catalogo" de instancias registradas (quem esta no ar e onde).
- Os outros projetos usam este servidor para se registrar e para descobrir outros servicos via nome (serviceId).
- Porta (DEV/TST): `8761`
- Console: `http://localhost:8761`

### `api-gateway`

Funcao: ponto de entrada unico (reverse proxy) para os microservicos.

- Recebe chamadas HTTP do cliente e roteia para os servicos usando Discovery + load-balancing (`lb://...`).
- Ajuda a concentrar concerns comuns: roteamento, autenticacao, rate limit, logging, etc (neste projeto estamos focando em roteamento).
- Porta (DEV/TST): `8080`
- Rotas configuradas:
  - `GET http://localhost:8080/vendas` -> `lb://vendas-service/vendas`
  - `GET http://localhost:8080/estoque/itens` -> `lb://estoque-service/itens`
  - `GET http://localhost:8080/pagamento/status` -> `lb://pagamento-service/status`

### `vendas-service`

Funcao: microservico A (exemplo de "vendas").

- Fornece um endpoint simples para listar vendas.
- Registra-se no Eureka com o nome `vendas-service` (definido em `spring.application.name`).
- Porta (DEV/TST): `8081`
- Endpoint principal:
  - `GET http://localhost:8081/vendas`

### `estoque-service`

Funcao: microservico B (exemplo de "estoque").

- Fornece um endpoint simples para listar itens em estoque.
- Registra-se no Eureka com o nome `estoque-service`.
- Porta (DEV/TST): `8082`
- Endpoint principal:
  - `GET http://localhost:8082/itens`

### `pagamento-service`

Funcao: microservico C (exemplo de "pagamentos").

- Fornece um endpoint simples de status do servico de pagamentos.
- Registra-se no Eureka com o nome `pagamento-service`.
- Porta (DEV/TST): `8083`
- Endpoint principal:
  - `GET http://localhost:8083/status`

## Observabilidade (Actuator)

Os servicos expõem endpoints Actuator basicos para facilitar diagnostico e integracao com o Eureka UI:

- `GET /actuator/health`
- `GET /actuator/info`

No gateway, o Actuator tambem esta presente e os endpoints expostos incluem `health` e `info`.

## Configuracao do Eureka (DEV vs TST)

Todos os clientes Eureka (gateway + microservicos) usam a URL do registry via:

- `EUREKA_URI` (variavel de ambiente), com default para DEV: `http://localhost:8761/eureka`

No `docker-compose.yml`, cada container recebe:

- `EUREKA_URI=http://discovery:8761/eureka`

## Como executar

### DEV (rodar um a um)

1. Suba o `discovery-server`.
2. Suba o `api-gateway` e os microservicos que quiser (em terminais separados).
3. Teste via gateway:
   - `http://localhost:8080/vendas`
   - `http://localhost:8080/estoque/itens`
   - `http://localhost:8080/pagamento/status`

### TST (subir tudo de uma vez)

Na raiz do repositorio:

- `docker compose up --build`

Eureka:

- `http://localhost:8761`

Gateway:

- `http://localhost:8080/vendas`
- `http://localhost:8080/estoque/itens`
- `http://localhost:8080/pagamento/status`

