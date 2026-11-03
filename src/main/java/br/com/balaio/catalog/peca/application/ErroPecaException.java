package br.com.balaio.catalog.peca.application;

public class ErroPecaException extends RuntimeException {
    private final int status;
    private final String codigo;
    private final String campo;

    public ErroPecaException(int status, String codigo, String campo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
        this.campo = campo;
    }

    public int status() { return status; }
    public String codigo() { return codigo; }
    public String campo() { return campo; }

    public static ErroPecaException invalido(String campo, String mensagem) {
        return new ErroPecaException(400, "REQUISICAO_INVALIDA", campo, mensagem);
    }
}
