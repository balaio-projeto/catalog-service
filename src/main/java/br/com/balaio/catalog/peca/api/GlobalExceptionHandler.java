package br.com.balaio.catalog.peca.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
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

        return criarResposta(
                HttpStatus.BAD_REQUEST,
                "REQUISICAO_INVALIDA",
                "Dados inválidos."
        );
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
