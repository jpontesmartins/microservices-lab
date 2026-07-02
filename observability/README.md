observability
---

Ferramentas de monitoramento
- Não consegui fazer o PushGateway funcionar.
- Nesse primeiro momento to enviando as métricas apenas do `api-gateway` e `vendas-service`.
- prometheus.yml: Configuração de scrapper do Prometheus
- observability.yml: docker-compose das ferramentas de observabilidade
---

## Prometheus
http://localhost:9090/

## ~~Prometheus Pushgateway~~
http://localhost:9091/

## Grafana
http://localhost:3000/login

## Zipkin
http://localhost:9411/zipkin/

## Integração com os serviços

Quando os microserviços sobem pelo `docker-compose.yml` da raiz, eles já usam `ZIPKIN_BASE_URL=http://host.docker.internal:9411` para enviar spans para este Zipkin.

Se você for subir só os serviços localmente fora do Docker, o default continua sendo `http://localhost:9411`.
