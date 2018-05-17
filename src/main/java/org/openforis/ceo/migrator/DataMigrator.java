package org.openforis.ceo.migrator;

import static java.lang.String.format;

import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openforis.ceo.migrator.util.JsonUtils;
import org.openforis.collect.manager.RecordManager;
import org.openforis.collect.manager.SurveyManager;
import org.openforis.collect.manager.UserManager;
import org.openforis.collect.model.CollectRecord;
import org.openforis.collect.model.CollectSurvey;
import org.openforis.collect.model.RecordGenerator;
import org.openforis.collect.model.RecordGenerator.NewRecordParameters;
import org.openforis.collect.model.User;
import org.openforis.idm.metamodel.CodeAttributeDefinition;
import org.openforis.idm.metamodel.CodeList;
import org.openforis.idm.metamodel.CodeListItem;
import org.openforis.idm.metamodel.CodeListLabel.Type;
import org.openforis.idm.metamodel.EntityDefinition;
import org.openforis.idm.metamodel.NodeDefinition;
import org.openforis.idm.metamodel.Survey;
import org.openforis.idm.model.Code;
import org.openforis.idm.model.CodeAttribute;
import org.openforis.idm.model.Entity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

@Component
public class DataMigrator extends BaseMigrator {

	private static final Logger LOGGER = Logger.getAnonymousLogger();
	
	@Autowired
	private SurveyManager surveyManager;
	@Autowired
	private RecordManager recordManager;
	@Autowired
	private RecordGenerator recordGenerator;
	@Autowired
	private UserManager userManager;
	
	public void migrate() {
		LOGGER.info("Starting data migration...");
		List<CollectSurvey> publishedSurveys = surveyManager.getAll();
		for (CollectSurvey s : publishedSurveys) {
			Integer surveyId = s.getId();
			LOGGER.info(format("Migrating data of project %d...", surveyId));
			JsonArray plotLocations = readSourceFileAsArray(String.format("plot-data-%d.json", surveyId));
			AtomicInteger analysedPlotCount = new AtomicInteger();
			if (plotLocations != null) {
				AtomicInteger plotIndex = new AtomicInteger();
				plotLocations.forEach(pEl -> {
					int plotId = plotIndex.incrementAndGet();
					JsonObject p = pEl.getAsJsonObject();
					int analyses = p.get("analyses").getAsInt();
					if (analyses > 0) {
						LOGGER.info(format("Migrating data of plot %d...", plotId));
						analysedPlotCount.incrementAndGet();
						try {
							List<String> recordKeyValues = Arrays.asList(String.valueOf(plotId));
							NewRecordParameters newRecordParams = new NewRecordParameters();
							newRecordParams.setAddSecondLevelEntities(true);
							String username = JsonUtils.getMemberValue(p, "user", String.class);
							User user = username == null ? userManager.loadAdminUser() : userManager.loadByUserName(username);
							newRecordParams.setUserId(user == null ? null : user.getId());
							CollectRecord record = recordGenerator.generate(surveyId, newRecordParams, recordKeyValues);
							
							JsonArray samples = p.get("samples").getAsJsonArray();
							AtomicInteger sampleIndex = new AtomicInteger();
							samples.forEach(sampleEl -> {
								JsonObject sampleObj = sampleEl.getAsJsonObject();
								int sampleId = sampleIndex.incrementAndGet();
								Entity recordSample = record.getRootEntity().findSingleChildEntityByKeys("subplot", String.valueOf(sampleId));
								JsonElement valueEl = sampleObj.get("value");
								if (valueEl != null && !valueEl.isJsonNull()) {
									setValueInRecord(recordSample, valueEl);
								}
							});
							recordManager.save(record);
							LOGGER.info(format("Data of plot %d migrated successfully into record with id %d", plotId, record.getId()));
						} catch(Exception e) {
							LOGGER.log(Level.SEVERE, format("Error migrating data for plot %d: %s", plotId, e.getMessage()), e);
						}
					}
				});
			}
			LOGGER.info(format("Project data migrated successfully. %d analysed plots have been migrated.", analysedPlotCount.get()));
		}
	}

	private void setValueInRecord(Entity recordSample, JsonElement valueEl) {
		if (valueEl.isJsonObject()) {
			JsonObject valueObj = valueEl.getAsJsonObject();
			Set<Entry<String, JsonElement>> valueEntrySet = valueObj.entrySet();
			for (Entry<String, JsonElement> valueEntry : valueEntrySet) {
				String codeListLabel = valueEntry.getKey();
				String codeItemLabel = valueEntry.getValue().getAsString();
				CodeList codeList = getCodeListByLabel(recordSample.getSurvey(), codeListLabel);
				String codeListItemCode = getCodeListItemCode(codeList, codeItemLabel);
				CodeAttributeDefinition codeDef = getValueAttribute(codeList);
				CodeAttribute codeAttr = recordSample.getChild(codeDef);
				codeAttr.setValue(new Code(codeListItemCode));
				codeAttr.updateSummaryInfo();
			}
		} else {
			CodeList codeList = recordSample.getSurvey().getCodeList("values_1");
			CodeListItem item = codeList.getItem(valueEl.getAsString());
			CodeAttributeDefinition codeDef = getValueAttribute(codeList);
			CodeAttribute codeAttr = recordSample.getChild(codeDef);
			codeAttr.setValue(new Code(item.getCode()));
			codeAttr.updateSummaryInfo();
		}
	}
	
	private CodeAttributeDefinition getValueAttribute(CodeList list) {
		EntityDefinition subplotDef = (EntityDefinition) list.getSurvey().getSchema().getDefinitionByPath("/plot/subplot");
		for (NodeDefinition subplotChildDef : subplotDef.getChildDefinitions()) {
			if (subplotChildDef instanceof CodeAttributeDefinition) {
				CodeAttributeDefinition codeDef = (CodeAttributeDefinition) subplotChildDef;
				if (codeDef.getList() == list) {
					return codeDef;
				}
			}
		}
		throw new IllegalStateException(format("Sample value attribute for code list %s not found", list.getName()));
	}
	
	private CodeList getCodeListByLabel(Survey survey, String label) {
		List<CodeList> lists = survey.getCodeLists();
		for (CodeList list : lists) {
			if (label.equals(list.getLabel(Type.ITEM))) {
				return list;
			}
		}
		return null;
	}
	
	private String getCodeListItemCode(CodeList list, String label) {
		List<CodeListItem> items = list.getItems();
		for (CodeListItem item : items) {
			if (label.equals(item.getLabel())) {
				return item.getCode();
			}
		}
		return null;
	}
	
}
