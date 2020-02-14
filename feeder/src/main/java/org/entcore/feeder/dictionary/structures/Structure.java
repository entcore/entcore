/* Copyright © "Open Digital Education", 2014
 *
 * This program is published by "Open Digital Education".
 * You must indicate the name of the software and the company in any production /contribution
 * using the software and indicate on the home page of the software industry in question,
 * "powered by Open Digital Education" with a reference to the website: https://opendigitaleducation.com/.
 *
 * This program is free software, licensed under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, version 3 of the License.
 *
 * You can redistribute this application and/or modify it since you respect the terms of the GNU Affero General Public License.
 * If you modify the source code and then use this modified source code in your creation, you must make available the source code of your modifications.
 *
 * You should have received a copy of the GNU Affero General Public License along with the software.
 * If not, please see : <http://www.gnu.org/licenses/>. Full compliance requires reading the terms of this license and following its directives.

 *
 */

package org.entcore.feeder.dictionary.structures;

import org.entcore.common.neo4j.Neo4jUtils;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.feeder.exceptions.TransactionException;
import org.entcore.feeder.utils.ResultMessage;
import org.entcore.feeder.utils.TransactionHelper;
import org.entcore.feeder.utils.TransactionManager;
import org.entcore.feeder.utils.Validator;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.*;

import static fr.wseduc.webutils.Utils.isNotEmpty;

public class Structure {

	private static final Logger log = LoggerFactory.getLogger(Structure.class);
	protected final String id;
	protected final String externalId;
	protected final Importer importer = Importer.getInstance();
	protected JsonObject struct;
	protected final Set<String> classes = Collections.synchronizedSet(new HashSet<String>());
	protected final Set<String> groups = Collections.synchronizedSet(new HashSet<String>());
	protected final Set<String> classesGroups = Collections.synchronizedSet(new HashSet<String>());
	private transient String overrideClass;

	public Structure(JsonObject struct) {
		this(struct.getString("externalId"), struct);
	}

	protected Structure(JsonObject struct, JsonArray groups, JsonArray classes) {
		this(struct);
		if (groups != null) {
			for (Object o : groups) {
				if (!(o instanceof String)) continue;
				this.groups.add((String) o);
			}
		}
		if (classes != null) {
			for (Object o : classes) {
				if (!(o instanceof String)) continue;
				this.classes.add((String) o);
			}
		}
	}

	protected Structure(String externalId, JsonObject struct) {
		if (struct != null && externalId != null && externalId.equals(struct.getString("externalId"))) {
			this.id = struct.getString("id");
		} else {
			throw new IllegalArgumentException("Invalid structure with externalId : " + externalId);
		}
		this.externalId = externalId;
		this.struct = struct;
	}

	private TransactionHelper getTransaction() {
		return importer.getTransaction();
	}

	public void update(JsonObject struct) {
		if (this.struct.equals(struct)) {
			return;
		}
		String query =
				"MATCH (s:Structure { externalId : {externalId}}) " +
				"WITH s " +
				"WHERE s.checksum IS NULL OR s.checksum <> {checksum} " +
				"SET " + Neo4jUtils.nodeSetPropertiesFromJson("s", struct, "id", "externalId", "created");
		getTransaction().add(query, struct);

		if (this.struct.getString("name") != null && !this.struct.getString("name").equals(struct.getString("name"))) {
			String updateGroupsStructureName =
					"MATCH (s:Structure { externalId : {externalId}})<-[:DEPENDS]-(g:Group) " +
					"WHERE s.checksum = {checksum} and has(g.structureName) " +
					"SET g.structureName = {name} ";
			getTransaction().add(updateGroupsStructureName, struct);
			String updateGroupsName =
					"MATCH (s:Structure { externalId : {externalId}})<-[:DEPENDS]-(g:Group) " +
					"WHERE s.checksum = {checksum} and last(split(g.name, '-')) IN " +
					"['Student','Teacher','Personnel','Relative','Guest','AdminLocal','HeadTeacher', 'SCOLARITE'] " +
					"SET g.name = {name} + '-' + last(split(g.name, '-')), g.displayNameSearchField = {sanitizeName} ";
			getTransaction().add(updateGroupsName, struct.copy().put("sanitizeName", Validator.sanitize(struct.getString("name"))));
		}
		this.struct = struct;
	}

	public void create() {
		String query =
				"CREATE (s:Structure {props}) " +
				"WITH s " +
				"MATCH (p:Profile) " +
				"CREATE p<-[:HAS_PROFILE]-(g:Group:ProfileGroup {name : s.name+'-'+p.name, displayNameSearchField: {groupSearchField}, filter: p.name})-[:DEPENDS]->s " +
				"SET g.id = id(g)+'-'+timestamp() ";
		JsonObject params = new JsonObject()
				.put("id", id)
				.put("externalId", externalId)
				.put("groupSearchField", Validator.sanitize(struct.getString("name")))
				.put("props", struct);
		getTransaction().add(query, params);
	}


	public synchronized Object[] addJointure(String externalId) {
		if (struct != null) {
			JsonArray joinKey = struct.getJsonArray("joinKey");
			if (joinKey == null) {
				joinKey = new fr.wseduc.webutils.collections.JsonArray();
				struct.put("joinKey", joinKey);
			}
			joinKey.add(externalId);
			String query =
					"MATCH (s:Structure {externalId: {externalId}}) " +
					"SET s.joinKey = {joinKey} ";
			JsonObject params = new JsonObject().put("joinKey", joinKey).put("externalId", getExternalId());
			getTransaction().add(query, params);
			return joinKey.getList().toArray();
		}
		return null;
	}

	public void addAttachment() {
		JsonArray functionalAttachment = struct.getJsonArray("functionalAttachment");
		if (functionalAttachment != null && functionalAttachment.size() > 0 &&
				!externalId.equals(functionalAttachment.getString(0))) {
			JsonObject params = new JsonObject().put("externalId", externalId);
			String query;
			if (functionalAttachment.size() == 1) {
				query =
						"MATCH (s:Structure { externalId : {externalId}}), " +
						"(ps:Structure { externalId : {functionalAttachment}}) " +
						"CREATE UNIQUE s-[:HAS_ATTACHMENT]->ps";
				params.put("functionalAttachment", functionalAttachment.getString(0));
			} else {
				query =
						"MATCH (s:Structure { externalId : {externalId}}), (ps:Structure) " +
						"WHERE ps.externalId IN {functionalAttachment} " +
						"CREATE UNIQUE s-[:HAS_ATTACHMENT]->ps";
				params.put("functionalAttachment", functionalAttachment);
			}
			getTransaction().add(query, params);
		}
	}

	public void createClassIfAbsent(String classExternalId, String name) {
		if (classes.add(classExternalId)) {
			String query =
					"MATCH (s:Structure { externalId : {structureExternalId}}) " +
					"CREATE s<-[:BELONGS]-(c:Class {props})" +
					"WITH s, c " +
					"MATCH s<-[:DEPENDS]-(g:ProfileGroup)-[:HAS_PROFILE]->(p:Profile) " +
					"CREATE c<-[:DEPENDS]-(pg:Group:ProfileGroup {name : c.name+'-'+p.name, displayNameSearchField: {groupSearchField}, filter: p.name})-[:DEPENDS]->g " +
					"SET pg.id = id(pg)+'-'+timestamp() ";
			JsonObject params = new JsonObject()
					.put("structureExternalId", externalId)
					.put("groupSearchField", Validator.sanitize(name))
					.put("props", new JsonObject()
							.put("externalId", classExternalId)
							.put("id", UUID.randomUUID().toString())
							.put("name", name)
					);
			getTransaction().add(query, params);
		}
	}

	public void updateClassName(String classExternalId, String name) {
		if (classes.contains(classExternalId)) {
			String query =
					"MATCH (c:Class { externalId : {externalId}})<-[:DEPENDS]-(g:ProfileGroup) " +
					"WHERE c.name <> {name} " +
					"SET c.name = {name}, g.name = {name} +'-'+ g.filter, g.displayNameSearchField = {groupSearchField} ";
			JsonObject params = new JsonObject()
					.put("externalId", classExternalId)
					.put("name", name)
					.put("groupSearchField", Validator.sanitize(name));
			getTransaction().add(query, params);
		}
	}

	public void createFunctionalGroupIfAbsent(String groupExternalId, String name) {
		if (groups.add(groupExternalId)) {
			String query =
					"MATCH (s:Structure { externalId : {structureExternalId}}) " +
					"WHERE (NOT(HAS(s.timetable)) OR s.timetable = '') " +
					"CREATE s<-[:DEPENDS]-(c:Group:FunctionalGroup {props}) ";
			JsonObject params = new JsonObject()
					.put("structureExternalId", externalId)
					.put("props", new JsonObject()
							.put("externalId", groupExternalId)
							.put("id", UUID.randomUUID().toString())
							.put("displayNameSearchField", Validator.sanitize(name))
							.put("structureName", struct.getString("name"))
							.put("name", name)
					);
			getTransaction().add(query, params);
		}
	}

	public void createFunctionGroupIfAbsent(String groupExternalId, String name, String label) {
		if (isNotEmpty(label) && groups.add(groupExternalId)) {
			String query =
					"MATCH (s:Structure { externalId : {structureExternalId}}) " +
					"WHERE (NOT(HAS(s.timetable)) OR s.timetable = '') " +
					"CREATE s<-[:DEPENDS]-(c:Group:FunctionGroup:" + label + "Group {props}) ";
			JsonObject params = new JsonObject()
					.put("structureExternalId", externalId)
					.put("props", new JsonObject()
									.put("externalId", groupExternalId)
									.put("id", UUID.randomUUID().toString())
									.put("displayNameSearchField", Validator.sanitize(name))
									.put("structureName", struct.getString("name"))
									.put("name", name + "-" + label)
									.put("filter", name)
					);
			getTransaction().add(query, params);
		}
	}

	public void createHeadTeacherGroupIfAbsent(String structureGroupExternalId, String classGroupExternalId, String name) {
		if (groups.add(structureGroupExternalId)) {
			String query =
					"MATCH (s:Structure { externalId : {structureExternalId}}) " +
					"CREATE s<-[:DEPENDS]-(:Group:HTGroup {props}) ";
			JsonObject params = new JsonObject()
					.put("structureExternalId", externalId)
					.put("props", new JsonObject()
							.put("externalId", structureGroupExternalId)
							.put("id", UUID.randomUUID().toString())
							.put("displayNameSearchField", Validator.sanitize(struct.getString("name")))
							.put("name", struct.getString("name") + "-HeadTeacher")
							.put("filter", "HeadTeacher")
					);
			getTransaction().add(query, params);
		}
		if (classesGroups.add(classGroupExternalId)) {
			String query =
					"MATCH (c:Class { externalId : {classExternalId}}) " +
					"MERGE c<-[:DEPENDS]-(cg:Group:HTGroup {externalId: {externalId}}) " +
					"ON CREATE SET cg.id = {id}, cg.displayNameSearchField = {displayNameSearchField}, " +
					"cg.name = {name}, cg.structureName = {structureName} ";
			JsonObject params = new JsonObject()
					.put("classExternalId", classGroupExternalId.substring(0, classGroupExternalId.length() - 3))
					.put("externalId", classGroupExternalId)
					.put("id", UUID.randomUUID().toString())
					.put("displayNameSearchField", Validator.sanitize(name))
					.put("structureName", struct.getString("name"))
					.put("name", name + "-HeadTeacher")
					.put("filter", "HeadTeacher");
			getTransaction().add(query, params);
			String linkParent =
					"MATCH (sg:HTGroup {externalId: {structureGroupExternalId}}), (cg:HTGroup {externalId: {classGroupExternalId}}) " +
					"MERGE sg<-[:DEPENDS]-cg ";
			JsonObject pl = new JsonObject()
					.put("structureGroupExternalId", structureGroupExternalId)
					.put("classGroupExternalId", classGroupExternalId);
			getTransaction().add(linkParent, pl);
		}
	}

	public void linkModules(String moduleExternalId) {
		String query =
				"MATCH (s:Structure { externalId : {externalId}}), " +
				"(m:Module { externalId : {moduleExternalId}}) " +
				"CREATE UNIQUE s-[:OFFERS]->m";
		JsonObject params = new JsonObject()
				.put("externalId", externalId)
				.put("moduleExternalId", moduleExternalId);
		getTransaction().add(query, params);
	}

	public String getExternalId() {
		return externalId;
	}

	public void transition(boolean onlyRemoveShare, final Handler<Message<JsonObject>> handler) {
		final TransactionHelper tx = TransactionManager.getInstance().getTransaction("GraphDataUpdate");
		String query =
				"MATCH (s:Structure {id : {id}})<-[:BELONGS]-(c:Class)" +
				"<-[:DEPENDS]-(cpg:Group)<-[:IN]-(u:User) " +
				"OPTIONAL MATCH s<-[:DEPENDS]-(fg:FunctionalGroup) " +
				"RETURN collect(distinct u.id) as users, collect(distinct cpg.id) as profileGroups, " +
				"collect(distinct fg.id) as functionalGroups";
		JsonObject params = new JsonObject().put("id", id);
		tx.getNeo4j().execute(query, params, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				JsonArray r = event.body().getJsonArray("result");
				if ("ok".equals(event.body().getString("status")) && r != null && r.size() == 1) {
					final JsonObject res = r.getJsonObject(0);
					usersInGroups(new Handler<Message<JsonObject>>() {

						@Override
						public void handle(Message<JsonObject> event) {
							if ("ok".equals(event.body().getString("status"))) {
								if (!onlyRemoveShare) {
									for (Object u : res.getJsonArray("users")) {
										User.backupRelationship(u.toString(), tx);
										User.transition(u.toString(), tx);
									}
									transitionClassGroup();
								}
								handler.handle(event);
							} else {
								log.error("Structure " + id + " transition error - useringroups.");
								log.error(event.body().encode());
								handler.handle(event);
							}
						}
					});
				} else {
					log.error("Structure " + id + " transition error.");
					log.error(event.body().encode());
					handler.handle(event);
				}
			}
		});
	}

	private void usersInGroups(Handler<Message<JsonObject>> handler) {
		final TransactionHelper tx;
		try {
			tx = TransactionManager.getInstance().begin();
			final JsonObject params = new JsonObject().put("id", id);
			String query =
					"MATCH (s:Structure {id : {id}})<-[:BELONGS]-(c:Class)" +
					"<-[:DEPENDS]-(cpg:Group) " +
					"OPTIONAL MATCH cpg<-[:IN]-(u:User) " +
					"RETURN cpg.id as group, cpg.name as groupName, collect(u.id) as users " +
					"UNION " +
					"MATCH (s:Structure {id : {id}})<-[r:DEPENDS]-(fg:FunctionalGroup) " +
					"OPTIONAL MATCH fg<-[:IN]-(u:User) " +
					"RETURN fg.id as group, fg.name as groupName, collect(u.id) as users ";
			tx.add(query, params);
			String queryClasses =
					"MATCH (s:Structure {id : {id}})<-[:BELONGS]-(c:Class)" +
					"<-[:DEPENDS]-(cpg:ProfileGroup) " +
					"OPTIONAL MATCH cpg<-[:IN]-(u:User) " +
					"RETURN c.id as classId, c.name as className, collect(u.id) as users ";
			tx.add(queryClasses, params);
			tx.commit(handler);
		} catch (TransactionException e) {
			handler.handle(new ResultMessage().error(e.getMessage()));
		}
	}

	private void transitionClassGroup() {
		TransactionHelper tx = TransactionManager.getInstance().getTransaction("GraphDataUpdate");
		JsonObject params = new JsonObject().put("id", id);
		String query =
				"MATCH (s:Structure {id : {id}})<-[r:BELONGS]-(c:Class)" +
				"<-[r1:DEPENDS]-(cpg:Group)-[r2]-() " +
				"OPTIONAL MATCH c-[r3]-() " +
				"DELETE r, r1, r2, r3, c, cpg ";
		tx.add(query, params);
		query = "MATCH (s:Structure {id : {id}})<-[r:DEPENDS]-(fg:FunctionalGroup) " +
				"OPTIONAL MATCH fg-[r1]-() " +
				"DELETE r, r1, fg";
		tx.add(query, params);
	}

	public static void count(String exportType, TransactionHelper transactionHelper) {
		JsonObject params = new JsonObject();
		String query = "MATCH (s:Structure) ";
		if (isNotEmpty(exportType)) {
			query += "WHERE HAS(s.exports) AND {exportType} IN s.exports ";
			params.put("exportType", exportType);
		}
		query += "RETURN count(distinct s) as nb";
		transactionHelper.add(query, params);
	}

	public static void list(String exportType, JsonArray attributes, Integer skip, Integer limit, TransactionHelper transactionHelper) {
		StringBuilder query = new StringBuilder("MATCH (s:Structure) ");
		JsonObject params = new JsonObject();
		if (isNotEmpty(exportType)) {
			query.append("WHERE HAS(s.exports) AND {exportType} IN s.exports ");
			params.put("exportType", exportType);
		}
		if (attributes != null && attributes.size() > 0) {
			query.append("RETURN DISTINCT");
			for (Object attribute : attributes) {
				query.append(" s.").append(attribute).append(" as ").append(attribute).append(",");
			}
			query.deleteCharAt(query.length() - 1);
			query.append(" ");
		} else {
			query.append("RETURN DISTINCT s ");
		}
		if (skip != null && limit != null) {
			query.append("ORDER BY externalId ASC " +
					"SKIP {skip} " +
					"LIMIT {limit} ");
			params.put("skip", skip);
			params.put("limit", limit);
		}
		transactionHelper.add(query.toString(), params);
	}

	public static void addAttachment(String structureId, String parentStructureId,
			TransactionHelper transactionHelper) {
		String query =
				"MATCH (s:Structure { id : {structureId}}), " +
				"(ps:Structure { id : {parentStructureId}}) " +
				"CREATE UNIQUE s-[r:HAS_ATTACHMENT]->ps " +
				"RETURN id(r) as id";
		String query2 =
				"MATCH (s:Structure { id : {structureId}})-[:HAS_ATTACHMENT*1..]->(ps:Structure)" +
				"<-[:DEPENDS]-(fg:FunctionGroup {name : ps.name + '-AdminLocal'})<-[:IN]-(u:User)" +
				"-[rf:HAS_FUNCTION]-(f:Function {name : 'AdminLocal'}) " +
				"SET rf.scope = coalesce(rf.scope, []) + s.id " +
				"WITH DISTINCT s as n, f, u " +
				"MERGE (fg:Group:FunctionGroup { externalId : n.id + '-ADMIN_LOCAL'}) " +
				"ON CREATE SET fg.id = id(fg) + '-' + timestamp(), fg.name = n.name + '-' + f.name, fg.displayNameSearchField = lower(n.name), fg.filter = f.name " +
				"CREATE UNIQUE n<-[:DEPENDS]-fg " +
				"MERGE fg<-[:IN { source : 'MANUAL'}]-u";
		JsonObject params =  new JsonObject()
				.put("structureId", structureId)
				.put("parentStructureId", parentStructureId);
		transactionHelper.add(query, params);
		transactionHelper.add(query2, params);
	}

	public static void removeAttachment(String structureId, String parentStructureId,
			TransactionHelper transactionHelper) {
		String query =
				"MATCH (s:Structure { id : {structureId}})-[r:HAS_ATTACHMENT]->(ps:Structure { id : {parentStructureId}}) " +
				"DELETE r";
		String query2 =
				"MATCH (s:Structure { id : {structureId}})-[r:HAS_ATTACHMENT*0..]->(s2:Structure)" +
				"<-[:DEPENDS]-(:ProfileGroup)<-[:IN]-(u:User) " +
				"WITH COLLECT(distinct u.id) as ids " +
				"MATCH (s:Structure { id : {structureId}})<-[:DEPENDS]-(:FunctionGroup {externalId: s.id + '-ADMIN_LOCAL'})" +
				"<-[r:IN]-(u:User)-[rf:HAS_FUNCTION]-(:Function {name : 'AdminLocal'}) " +
				"WHERE NOT(u.id IN ids) " +
				"SET rf.scope = FILTER(sId IN rf.scope WHERE sId <> s.id) " +
				"DELETE r";
		JsonObject params = new JsonObject()
				.put("structureId", structureId)
				.put("parentStructureId", parentStructureId);
		transactionHelper.add(query, params);
		transactionHelper.add(query2, params);
	}

	public String getOverrideClass() {
		return overrideClass;
	}

	public void setOverrideClass(String overrideClass) {
		this.overrideClass = overrideClass;
	}

	public JsonObject getStruct() {
		return struct;
	}

}
