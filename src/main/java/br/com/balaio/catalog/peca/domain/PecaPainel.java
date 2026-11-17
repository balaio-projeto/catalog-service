package br.com.balaio.catalog.peca.domain;

/** Estado comercial derivado exclusivamente da peça e de sua reserva vigente. */
public record PecaPainel(Peca peca, Reserva reserva) {
    public enum Estado { DISPONIVEL, RASCUNHO, INATIVADA, RESERVADA, VENDIDA }

    public record Reserva(String pedidoId, boolean consumida) { }

    public Estado estado() {
        if (reserva != null) return reserva.consumida() ? Estado.VENDIDA : Estado.RESERVADA;
        if (peca.inativadoEm() != null) return Estado.INATIVADA;
        return "publicada".equals(peca.situacao()) ? Estado.DISPONIVEL : Estado.RASCUNHO;
    }

    public String vendidaEmPedido() {
        return reserva != null && reserva.consumida() ? reserva.pedidoId() : null;
    }
}
