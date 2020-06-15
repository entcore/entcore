/*
 * Copyright © "Open Digital Education", 2016
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

 */

package org.entcore.feeder.timetable.edt;

import org.entcore.feeder.Feed;
import org.entcore.feeder.dictionary.structures.Importer;
import org.entcore.feeder.dictionary.structures.Structure;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class EDTFeeder implements Feed, EDTReader
{
  protected static final Logger log = LoggerFactory.getLogger(EDTFeeder.class);

  private EDTUtils edtUtils;
  private String mode;

  public EDTFeeder(EDTUtils edtUtils, String mode)
  {
    this.edtUtils = edtUtils;
    this.mode = mode;
  }

  @Override
  public void launch(Importer importer, Handler<Message<JsonObject>> handler) throws Exception
  {
		throw new UnsupportedOperationException();
  }

  @Override
  public void launch(Importer importer, String path, Handler<Message<JsonObject>> handler) throws Exception
  {
  }

  @Override
  public void launch(Importer importer, String path, JsonObject mappings, Handler<Message<JsonObject>> handler) throws Exception
  {
		this.launch(importer, path, handler);
  }

  @Override
  public String getFeederSource()
  {
    return "EDT";
  }

  @Override
  public void addProfesseur(JsonObject currentEntity)
  {
  }

  @Override
  public void addPersonnel(JsonObject currentEntity)
  {
  }

  @Override
  public void addEleve(JsonObject currentEntity)
  {
  }

  @Override
  public void addResponsable(JsonObject currentEntity)
  {
  }

  @Override
  public void addSubject(JsonObject currentEntity)
  {
  }

  @Override
  public void addClasse(JsonObject currentEntity)
  {
  }

  @Override
  public void addGroup(JsonObject currentEntity)
  {
  }

  @Override
  public void addCourse(JsonObject currentEntity)
  {
  }

  @Override
  public void addRoom(JsonObject currentEntity)
  {
  }

  @Override
  public void addEquipment(JsonObject currentEntity)
  {
  }

  @Override
  public void initSchedule(JsonObject currentEntity)
  {
  }

  @Override
  public void initSchoolYear(JsonObject currentEntity)
  {
  }

}