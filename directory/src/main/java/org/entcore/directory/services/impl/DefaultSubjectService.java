package org.entcore.directory.services.impl;

import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.collections.Joiner;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.directory.services.SubjectService;

import static org.entcore.common.neo4j.Neo4jResult.*;

public class DefaultSubjectService implements SubjectService {

    private final Neo4j neo = Neo4j.getInstance();
    private final EventBus eventBus;

    public DefaultSubjectService(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void listAdmin(String structureId, Handler<Either<String, JsonArray>> results) {
        final JsonObject params = new JsonObject().put("structureId", structureId);
        String query = "MATCH (s:Structure {id : {structureId}})<-[:SUBJECT]-(sub:Subject)" +
                "return sub.id as id, sub.code as code, sub.label as label";

        neo.execute(query, params, validResultHandler(results));
    }
}