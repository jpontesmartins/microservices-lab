api-gateway
===


## Dependências

`spring-cloud-starter-gateway`: O motor principal. Ele roteia as requisições que chegam para os serviços corretos.

`spring-cloud-starter-netflix-eureka-client`: Faz o Gateway se cadastrar no Eureka para que ele saiba onde os outros serviços (vendas, estoque, etc.) estão morando, sem precisar de IPs fixos.

`spring-boot-starter-actuator`: Expõe pontos de verificação (endpoints) para monitorar a "saúde" da aplicação (ex: /actuator/health).

`micrometer-registry-prometheus`: Formata as métricas do Actuator para que o Prometheus (uma ferramenta de monitoramento) consiga lê-las.

`micrometer-tracing-bridge-brave`: Gera IDs rastreáveis para as requisições. Se um erro acontece no microserviço de pagamentos, você consegue rastrear toda a jornada daquela requisição desde o Gateway usando o traceId.


## Propriedades no application.yml

```
spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
      routes:
        - id: vendas-service
          uri: lb://vendas-service
          predicates:
            - Path=/vendas/**
        - id: estoque-service
          uri: lb://estoque-service
          predicates:
            - Path=/estoque/**
        - id: pagamento-service
          uri: lb://pagamento-service
          predicates:
            - Path=/pagamento/**

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  tracing:
    sampling:
      probability: 1.0

logging:
  pattern:
    level: "%5p [%X{traceId:-},%X{spanId:-}]"

server:
  port: 8080

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URI:http://localhost:8761/eureka}
```



## Blocos

### Spring Cloud Gateway (Spring > Cloud > Gateway)  
`discovery.locator.enabled: true`: Permite que o Gateway crie rotas automaticamente baseadas nos nomes dos serviços registrados no Eureka.  

`lower-case-service-id: true`: Garante que ele procure os nomes dos serviços em letras minúsculas (evita erros de digitação entre VENDAS-SERVICE e vendas-service).  

`routes:` Define regras manuais.  
- `id`: Nome da rota.  
- `uri`: lb://vendas-service: O prefixo lb:// significa Load Balancer. Ele diz: "Não vá para um IP fixo, peça ao Eureka o endereço do serviço 'vendas-service' e faça balanceamento de carga se houver mais de um".  
- `predicates`: A condição. ` - Path=/vendas/` significa: "Se a URL que o usuário chamou começar com /vendas, esta rota é a escolhida".  

---
### Management
`exposure.include`: Libera os endpoints do Actuator. O prometheus é o mais importante aqui para coletar dados de performance.

`tracing.sampling.probability: 1.0`: Diz ao Brave para rastrear 100% das requisições. Em produção com tráfego gigante, costuma-se baixar para 0.1 (10%) para não sobrecarregar o sistema.  

---

### Logging
`pattern.level`: Customiza o log. O trecho [%X{traceId:-},%X{spanId:-}] é fundamental para o rastreio distribuído. Ele faz com que cada linha de log mostre o ID da transação atual. Se você copiar esse ID, pode buscá-lo nos logs de todos os outros microserviços para ver o caminho completo da requisição.


### Eureka Client

`defaultZone`: O endereço do servidor de registro que você configurou anteriormente.  
${EUREKA_URI:...}: Isso é uma variável de ambiente. Se você rodar no Docker e definir a variável EUREKA_URI, ele usa o valor dela. Se não definir, ele usa o localhost:8761 como padrão (fallback).  

