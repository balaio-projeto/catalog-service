package br.com.balaio.catalog.peca.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.balaio.catalog.peca.application.port.out.FotoStoragePort;

@Component
@EnableScheduling
public class LimpezaFotosStorage {
    private static final Logger log = LoggerFactory.getLogger(LimpezaFotosStorage.class);
    private final JdbcTemplate jdbc;
    private final ObjectProvider<FotoStoragePort> storage;
    private final TransactionTemplate independente;

    public LimpezaFotosStorage(JdbcTemplate jdbc, ObjectProvider<FotoStoragePort> storage,
            PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.storage = storage;
        independente = new TransactionTemplate(transactions);
        independente.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Called in the same transaction that removes the photo reference. */
    public void agendar(String chave) {
        jdbc.update("INSERT INTO limpeza_fotos_storage (storage_key) SELECT ? "
                + "WHERE NOT EXISTS (SELECT 1 FROM limpeza_fotos_storage WHERE storage_key = ?)", chave, chave);
    }

    public void limparSeRollback(String chave) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Gravação de fotos exige uma transação ativa.");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        independente.executeWithoutResult(transaction -> agendar(chave));
                    } catch (RuntimeException falha) {
                        log.error("Não foi possível registrar limpeza de upload revertido: {}", chave);
                    }
                }
            }
        });
    }

    @Scheduled(initialDelayString = "${balaio.catalog.storage.cleanup-delay-ms:2000}",
            fixedDelayString = "${balaio.catalog.storage.cleanup-delay-ms:2000}")
    public void executar() {
        var arquivos = storage.getIfAvailable();
        if (arquivos == null) return;
        var pendentes = jdbc.query("SELECT storage_key FROM limpeza_fotos_storage ORDER BY criada_em LIMIT 10",
                (rs, row) -> rs.getString(1));
        for (String chave : pendentes) {
            try {
                var referencias = jdbc.queryForObject("SELECT count(*) FROM peca_fotos WHERE storage_key = ?",
                        Integer.class, chave);
                if (referencias != null && referencias > 0) continue;
                arquivos.remover(chave);
                jdbc.update("DELETE FROM limpeza_fotos_storage WHERE storage_key = ?", chave);
            } catch (RuntimeException falha) {
                // Keep the durable item and retry on the next execution. Never log SDK credentials.
                log.warn("Limpeza de foto pendente; nova tentativa agendada: {}", chave);
                break;
            }
        }
    }
}
