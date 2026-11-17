package br.com.balaio.catalog.peca.api;

import java.util.List;

public record PecasRelacionadasResponse(List<PecaResponse> dados, Object erro) { }
