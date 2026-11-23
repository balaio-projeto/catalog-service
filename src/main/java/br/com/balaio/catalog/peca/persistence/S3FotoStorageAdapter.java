package br.com.balaio.catalog.peca.persistence;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.balaio.catalog.peca.application.port.out.FotoStoragePort;
import br.com.balaio.catalog.peca.application.FotoStorageIndisponivelException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.core.sync.RequestBody;

public class S3FotoStorageAdapter implements FotoStoragePort {
    private static final Logger log = LoggerFactory.getLogger(S3FotoStorageAdapter.class);
    private final S3Client client;
    private final String bucket;

    public S3FotoStorageAdapter(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void guardar(String chave, String formato, byte[] conteudo) {
        try {
            client.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(chave)
                    .contentType("image/" + formato)
                    .build(), RequestBody.fromBytes(conteudo));
        } catch (SdkException falha) {
            throw indisponivel("gravar", falha);
        }
    }

    @Override
    public Optional<byte[]> obter(String chave) {
        try {
            ResponseBytes<GetObjectResponse> resposta = client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(chave).build());
            return Optional.of(resposta.asByteArray());
        } catch (NoSuchKeyException ausente) {
            return Optional.empty();
        } catch (SdkException falha) {
            throw indisponivel("ler", falha);
        }
    }

    @Override
    public void remover(String chave) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(chave).build());
        } catch (SdkException falha) {
            throw indisponivel("remover", falha);
        }
    }

    private FotoStorageIndisponivelException indisponivel(String operacao, SdkException falha) {
        // Do not log credentials, requests or SDK messages; retain safe diagnostics.
        String codigo = falha instanceof S3Exception s3 && s3.awsErrorDetails() != null
                ? s3.awsErrorDetails().errorCode() : falha.getClass().getSimpleName();
        int status = falha instanceof S3Exception s3 ? s3.statusCode() : 0;
        log.warn("Falha ao {} foto no S3: status={}, codigo={}", operacao, status, codigo);
        return new FotoStorageIndisponivelException();
    }
}
