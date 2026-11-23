package br.com.balaio.catalog.peca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import br.com.balaio.catalog.peca.persistence.S3FotoStorageAdapter;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.core.exception.SdkClientException;
import br.com.balaio.catalog.peca.application.FotoStorageIndisponivelException;

class S3FotoStorageAdapterTest {
    @Test
    void distingueObjetoAusenteDeStorageIndisponivelSemExporDetalhesDoSdk() {
        var client = mock(S3Client.class);
        var storage = new S3FotoStorageAdapter(client, "balaio-media");
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());
        assertThat(storage.obter("catalog/ausente.png")).isEmpty();
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(SdkClientException.create("detalhe interno do SDK"));
        assertThatThrownBy(() -> storage.obter("catalog/foto.png"))
                .isInstanceOf(FotoStorageIndisponivelException.class)
                .hasMessageNotContaining("SDK");
    }

    @Test
    void gravaLeEApagaObjetoNoBucketConfigurado() {
        var client = mock(S3Client.class);
        var storage = new S3FotoStorageAdapter(client, "balaio-media");
        String chave = "catalog/vaso/2d6b0f4c-a20f-44b5-b603-d3982995dd99.png";
        byte[] imagem = { (byte) 137, 80, 78, 71 };

        storage.guardar(chave, "png", imagem);
        verify(client).putObject(org.mockito.ArgumentMatchers.<PutObjectRequest>argThat(request ->
                request.bucket().equals("balaio-media") && request.key().equals(chave)
                        && request.contentType().equals("image/png")), any(RequestBody.class));

        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), imagem));
        assertThat(storage.obter(chave)).hasValueSatisfying(bytes -> assertThat(bytes).containsExactly(imagem));

        storage.remover(chave);
        verify(client).deleteObject(org.mockito.ArgumentMatchers.argThat((DeleteObjectRequest request) ->
                request.bucket().equals("balaio-media") && request.key().equals(chave)));
    }
}
