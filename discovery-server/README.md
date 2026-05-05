discovery-server
===

## Explicação das propriedades

```
server:
    port: 8761

eureka:
    client:
        register-with-eureka: false 
        fetch-registry: false # define se deve buscar a lista de serviços cadastrados no servidor para mante-la em cache local.
    server:
        enable-self-preservation: false @
```

`register-with-eureka`: Todo projeto que tem a biblioteca do Eureka no classpath tenta se registrar em um servidor, Como este projeto é o servidor, ele não precisa se registrar em si.  

`fetch-registry`: Define se deve buscar a lista de serviços cadastrados no servidor para mante-la em cache local.

`enable-self-preservation`: Em ambiente de desenvolvimento ou testes, nós ligamos e desligamos os microserviços o tempo todo. Se a auto-preservação estiver ativa, o Eureka manterá serviços "mortos" na lista para sempre. Desativá-la garante que, se você derrubar um serviço, ele saia da lista imediatamente.
- O que faz: Se o servidor parar de receber "batidas de coração" (heartbeats) de muitos microserviços ao mesmo tempo, ele assume que houve um problema de rede local e não remove as instâncias da lista, mesmo que elas pareçam fora do ar. Isso evita que o sistema inteiro colapse por um erro temporário de conexão.
