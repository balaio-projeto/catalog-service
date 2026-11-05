package br.com.balaio.catalog.peca.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import br.com.balaio.catalog.peca.application.ErroPecaException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ErroPecaException.class)
    public ResponseEntity<ApiErrorResponse> tratarRegra(ErroPecaException exception) {
        return ResponseEntity.status(exception.status()).body(new ApiErrorResponse(null,
                new ErroDetalheResponse(exception.codigo(), exception.getMessage(), exception.campo())));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> tratarConflito() {
        return criarResposta(HttpStatus.CONFLICT, "CONFLITO", "Peça já cadastrada; tente novamente.");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> tratarBancoIndisponivel() {
        return criarResposta(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO", "Não foi possível acessar os dados.");
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> tratarParametroInvalido(
            MethodArgumentTypeMismatchException exception) {

        return criarResposta(
                HttpStatus.BAD_REQUEST,
                "REQUISICAO_INVALIDA",
                "Dados inválidos."
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> tratarValidacao(
            MethodArgumentNotValidException exception) {

        return criarResposta(
                HttpStatus.BAD_REQUEST,
                "REQUISICAO_INVALIDA",
                "Dados inválidos."
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> tratarCorpoInvalido(
            HttpMessageNotReadableException exception) {
        String detalhe = exception.getMostSpecificCause().getMessage();
        String campo = detalhe != null && detalhe.contains("preco") ? "preco"
                : detalhe != null && detalhe.contains("prazoProducaoDias") ? "prazoProducaoDias" : null;
        return ResponseEntity.badRequest().body(new ApiErrorResponse(null,
                new ErroDetalheResponse("REQUISICAO_INVALIDA", "Dados inválidos.", campo)));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> tratarRotaNaoEncontrada(
            NoResourceFoundException exception) {

        return criarResposta(
                HttpStatus.NOT_FOUND,
                "RECURSO_NAO_ENCONTRADO",
                "Recurso não encontrado."
        );
    }

    private ResponseEntity<ApiErrorResponse> criarResposta(
            HttpStatus status,
            String codigo,
            String mensagem) {

        var erro = new ErroDetalheResponse(codigo, mensagem);
        var resposta = new ApiErrorResponse(null, erro);

        return ResponseEntity.status(status).body(resposta);
    }
}
