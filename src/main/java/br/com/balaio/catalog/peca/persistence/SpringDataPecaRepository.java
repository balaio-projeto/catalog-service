package br.com.balaio.catalog.peca.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SpringDataPecaRepository
        extends JpaRepository<PecaJpaEntity, String>, JpaSpecificationExecutor<PecaJpaEntity> {
}
