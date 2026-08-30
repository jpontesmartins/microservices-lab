package com.example.vendas.pedido.domain.port;

/**
 * Porta de saida para integracoes com servicos externos (estoque, frete, pagamento).
 * Define o contrato que a camada de infraestrutura deve implementar.
 */
public interface IntegracoesPort {

    ReservaEstoqueResult reservarEstoque(String pedidoId, String sku, int quantidade);

    FreteResult calcularFrete(String pedidoId, String sku, int quantidade, String cepDestino);

    PagamentoResult processarPagamento(String pedidoId, double valor);

    void cancelarReservaBestEffort(String reservaId);

    void cancelarFreteBestEffort(String freteId);

    record ReservaEstoqueResult(String reservaId, String status) {
    }

    record FreteResult(String freteId, String status, double valorFrete, String prazoEntrega) {
    }

    record PagamentoResult(String transacaoId, String status, double valor) {
    }
}
