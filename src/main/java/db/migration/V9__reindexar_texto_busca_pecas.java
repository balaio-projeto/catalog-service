package db.migration;

import java.text.Normalizer;
import java.util.Locale;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Reconstrói os dados existentes com a normalização Unicode usada na escrita. */
public class V9__reindexar_texto_busca_pecas extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        // Mantém a transformação versionada independente da evolução das entidades da aplicação.
        try (var consulta = context.getConnection().prepareStatement("""
                SELECT slug, nome, artesao, territorio, tecnica, categoria, tipo,
                    coalesce(array_to_string(historia, ' '), '') AS narrativa
                FROM pecas
                """);
                var atualizar = context.getConnection().prepareStatement(
                        "UPDATE pecas SET texto_busca = ? WHERE slug = ?")) {
            consulta.setFetchSize(200);
            try (var linhas = consulta.executeQuery()) {
                int pendentes = 0;
                while (linhas.next()) {
                    String texto = String.join(" ", linhas.getString("nome"), linhas.getString("artesao"),
                            linhas.getString("territorio"), linhas.getString("tecnica"),
                            linhas.getString("categoria"), linhas.getString("tipo"), linhas.getString("narrativa"));
                    atualizar.setString(1, Normalizer.normalize(texto, Normalizer.Form.NFD)
                            .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT));
                    atualizar.setString(2, linhas.getString("slug"));
                    atualizar.addBatch();
                    if (++pendentes == 200) {
                        atualizar.executeBatch();
                        pendentes = 0;
                    }
                }
                if (pendentes > 0) atualizar.executeBatch();
            }
        }
    }
}
