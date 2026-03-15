package com.yas.recommendation.vector.common.document;

import java.util.UUID;
import org.springframework.ai.document.id.IdGenerator;

public class DefaultIdGenerator implements IdGenerator {

    private final Long identity;
    private final String idPrefix;

    public DefaultIdGenerator(String idPrefix, Long identity) {
        this.identity = identity;
        this.idPrefix = idPrefix;
    }

    @Override
    public String generateId(Object... contents) {
        var id = "%s-%s".formatted(idPrefix, identity);
        return UUID.nameUUIDFromBytes(id.getBytes()).toString();
    }
}
