package com.ecopedia.core.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecopedia.core.shared.StaleVersionException;
import com.ecopedia.core.shared.VersionCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * La lectura del {@code If-Match} y lo que pasa cuando dos escrituras chocan (QA-30), sin levantar
 * nada: lo que se prueba es la regla, no el transporte.
 */
class EntityTagsTest {

    @Test
    @DisplayName("Sin cabecera, en blanco o con *, cualquier versión sirve")
    void noCondition() {
        for (String header : new String[] {null, "", "   ", "*", "\"3\", *"}) {
            VersionCondition condition = EntityTags.ifMatch(header);
            assertThatCode(() -> condition.check(42, "x")).doesNotThrowAnyException();
        }
        assertThat(EntityTags.ifMatch(null).isConditional()).isFalse();
        assertThat(EntityTags.ifMatch("*").isConditional()).isFalse();
    }

    @Test
    @DisplayName("Una etiqueta fuerte pasa solo con su versión")
    void strongTag() {
        VersionCondition condition = EntityTags.ifMatch("\"7\"");

        assertThat(condition.isConditional()).isTrue();
        assertThatCode(() -> condition.check(7, "x")).doesNotThrowAnyException();
        assertThatThrownBy(() -> condition.check(8, "x")).isInstanceOf(StaleVersionException.class);
        assertThatThrownBy(() -> condition.check(6, "x")).isInstanceOf(StaleVersionException.class);
    }

    @Test
    @DisplayName("Débil, sin comillas, con letras o desbordada: no coincide con ninguna versión")
    void tagsThatNeverMatch() {
        for (String header :
                new String[] {"W/\"7\"", "7", "\"\"", "\"7a\"", "\"-7\"", "\"٧\"", "\"99999999999999999999\"", "\"7"}) {
            VersionCondition condition = EntityTags.ifMatch(header);
            assertThat(condition.isConditional()).as(header).isTrue();
            assertThatThrownBy(() -> condition.check(7, "x")).as(header).isInstanceOf(StaleVersionException.class);
        }
    }

    @Test
    @DisplayName("El ETag que reescribe Caddy al comprimir (\"7-gzip\") es la misma versión")
    void encodingSuffixFromTheProxy() {
        for (String header : new String[] {"\"7-gzip\"", "\"7-zstd\"", "\"7-br\""}) {
            assertThatCode(() -> EntityTags.ifMatch(header).check(7, "x"))
                    .as(header)
                    .doesNotThrowAnyException();
        }
        for (String header :
                new String[] {"\"7-gzip\"", "\"-gzip\"", "\"7-gzipx\"", "W/\"7-gzip\"", "\"7-gzip-gzip\""}) {
            assertThatThrownBy(() -> EntityTags.ifMatch(header).check(header.equals("\"7-gzip\"") ? 8 : 7, "x"))
                    .as(header)
                    .isInstanceOf(StaleVersionException.class);
        }
    }

    @Test
    @DisplayName("En una lista, alcanza con que una coincida")
    void list() {
        VersionCondition condition = EntityTags.ifMatch("\"1\", W/\"2\", \"3\"");

        assertThatCode(() -> condition.check(1, "x")).doesNotThrowAnyException();
        assertThatCode(() -> condition.check(3, "x")).doesNotThrowAnyException();
        assertThatThrownBy(() -> condition.check(2, "x")).isInstanceOf(StaleVersionException.class);
    }

    @Test
    @DisplayName("El ETag es la versión entre comillas")
    void format() {
        assertThat(EntityTags.of(0)).isEqualTo("\"0\"");
        assertThat(EntityTags.of(15)).isEqualTo("\"15\"");
    }

    @Test
    @DisplayName("Un choque de escrituras con If-Match es el mismo 412; sin la cabecera, sigue siendo un choque")
    void concurrentWrite() {
        ObjectOptimisticLockingFailureException concurrent = new ObjectOptimisticLockingFailureException("Station", 1L);

        assertThatThrownBy(() -> EntityTags.write(EntityTags.ifMatch("\"1\""), () -> {
                    throw concurrent;
                }))
                .isInstanceOf(StaleVersionException.class);
        assertThatThrownBy(() -> EntityTags.write(EntityTags.ifMatch(null), () -> {
                    throw concurrent;
                }))
                .isSameAs(concurrent);
        assertThat(EntityTags.write(EntityTags.ifMatch("\"1\""), () -> "ok")).isEqualTo("ok");
    }

    @Test
    @DisplayName("El choque sin If-Match sale 409 CONCURRENT_UPDATE, no 500")
    void concurrentWriteIsAConflict() {
        ProblemDetail problem = new ApiExceptionHandler()
                .handleConcurrentUpdate(new ObjectOptimisticLockingFailureException("Station", 1L));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getProperties()).containsEntry("code", "CONCURRENT_UPDATE");
        assertThat(problem.getDetail()).doesNotContain("Station");
    }
}
