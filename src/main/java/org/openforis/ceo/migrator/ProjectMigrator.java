package org.openforis.ceo.migrator;

import static org.openforis.ceo.migrator.util.JsonUtils.getMemberValue;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.openforis.ceo.migrator.util.JsonUtils;
import org.openforis.collect.manager.SurveyManager;
import org.openforis.collect.metamodel.SimpleSurveyCreationParameters;
import org.openforis.collect.metamodel.SimpleSurveyCreationParameters.CeoSettings;
import org.openforis.collect.metamodel.SimpleSurveyCreationParameters.ListItem;
import org.openforis.collect.metamodel.SimpleSurveyCreationParameters.SimpleCodeList;
import org.openforis.collect.metamodel.SimpleSurveyCreator;
import org.openforis.collect.metamodel.SurveyTarget;
import org.openforis.collect.metamodel.samplingdesign.SamplingPointGenerationSettings;
import org.openforis.collect.metamodel.samplingdesign.SamplingPointLevelGenerationSettings;
import org.openforis.collect.metamodel.samplingdesign.SamplingPointLevelGenerationSettings.Distribution;
import org.openforis.collect.metamodel.samplingdesign.SamplingPointLevelGenerationSettings.Shape;
import org.openforis.collect.model.CollectSurvey;
import org.openforis.collect.model.CollectSurvey.PrivacyLevel;
import org.openforis.collect.utils.SurveyObjects;
import org.openforis.idm.model.Coordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Component
public class ProjectMigrator extends BaseMigrator {

	private static final int SAMPLE_POINT_WIDTH_M = 10;

	@Autowired
	private SurveyManager surveyManager;

	public void migrate() {
		JsonArray projects = readSourceFileAsArray("project-list.json");
		JsonUtils.toStream(projects).filter(p -> p.get("id").getAsInt() != 0).forEach(p -> {
			migrate(p.getAsJsonObject());
		});
	}

	private void migrate(JsonObject ceoProject) {
		String projectName = ceoProject.get("name").getAsString();
		SimpleSurveyCreationParameters surveyCreationParameters = new SimpleSurveyCreationParameters();
		surveyCreationParameters.setTarget(SurveyTarget.COLLECT_EARTH_ONLINE);
		surveyCreationParameters.setProjectName(projectName);
		surveyCreationParameters.setDescription(ceoProject.get("description").getAsString());
		surveyCreationParameters.setUserGroupId(ceoProject.get("institution").getAsInt());
		String ceoPrivacyLevel = ceoProject.get("privacyLevel").getAsString();
		surveyCreationParameters.setPrivacyLevel("institution".equals(ceoPrivacyLevel) ? PrivacyLevel.GROUP
				: PrivacyLevel.valueOf(ceoPrivacyLevel.toUpperCase()));

		CeoSettings ceoSettings = new CeoSettings();
		ceoSettings.setBaseMapSource(getMemberValue(ceoProject, "baseMapSource", String.class));
		surveyCreationParameters.setCeoSettings(ceoSettings);

		SamplingPointGenerationSettings samplingPointGenerationSettings = new SamplingPointGenerationSettings();
		samplingPointGenerationSettings.setAoiBoundary(extractAoiBoundary(ceoProject));

		SamplingPointLevelGenerationSettings plotLevelSettings = new SamplingPointLevelGenerationSettings();
		plotLevelSettings.setNumPoints(ceoProject.get("numPlots").getAsInt());
		plotLevelSettings.setShape(Shape.SQUARE); // AOI shape
		plotLevelSettings.setPointShape(Shape.valueOf(ceoProject.get("plotShape").getAsString().toUpperCase()));
		plotLevelSettings
				.setDistribution(Distribution.valueOf(ceoProject.get("plotDistribution").getAsString().toUpperCase()));
		plotLevelSettings.setResolution(getMemberValue(ceoProject, "plotSpacing", Integer.class));
		plotLevelSettings.setPointWidth(getMemberValue(ceoProject, "plotSize", Integer.class));
		samplingPointGenerationSettings.addLevelSettings(plotLevelSettings);

		SamplingPointLevelGenerationSettings sampleLevelSettings = new SamplingPointLevelGenerationSettings();
		sampleLevelSettings.setNumPoints(getMemberValue(ceoProject, "samplesPerPlot", Integer.class));
		sampleLevelSettings.setShape(Shape.valueOf(ceoProject.get("plotShape").getAsString().toUpperCase()));
		sampleLevelSettings.setPointShape(Shape.CIRCLE); // sample is always a circle?
		sampleLevelSettings.setDistribution(
				Distribution.valueOf(ceoProject.get("sampleDistribution").getAsString().toUpperCase()));
		sampleLevelSettings.setResolution(getMemberValue(ceoProject, "sampleResolution", Integer.class));
		sampleLevelSettings.setPointWidth(SAMPLE_POINT_WIDTH_M);
		samplingPointGenerationSettings.addLevelSettings(sampleLevelSettings);

		surveyCreationParameters.setSamplingPointGenerationSettings(samplingPointGenerationSettings);

		JsonArray sampleValueLists = ceoProject.get("sampleValues").getAsJsonArray();
		List<SimpleCodeList> simpleCodeLists = StreamSupport.stream(sampleValueLists.spliterator(), false).map(el -> {
			JsonObject l = el.getAsJsonObject();
			SimpleCodeList codeList = new SimpleCodeList();
			codeList.setLabel(l.get("name").getAsString());

			JsonArray values = l.get("values").getAsJsonArray();
			values.forEach(valEl -> {
				JsonObject valObj = valEl.getAsJsonObject();
				ListItem item = new ListItem();
				item.setLabel(valObj.get("name").getAsString());
				item.setColor(getMemberValue(valObj, "color", String.class, "#000000").substring(1));
				codeList.addItem(item);
			});
			return codeList;
		}).collect(Collectors.toList());

		surveyCreationParameters.setCodeLists(simpleCodeLists);

		SimpleSurveyCreator surveyCreator = new SimpleSurveyCreator(surveyManager);
		CollectSurvey s = surveyCreator.createTemporarySimpleSurvey(SurveyObjects.adjustInternalName(projectName),
				simpleCodeLists);
		s.setId(ceoProject.get("id").getAsInt());
	}

	private static List<Coordinate> extractAoiBoundary(JsonObject jsonObj) {
		String boundaryStr = jsonObj.get("boundary").getAsString();
		JsonObject boundaryObj = new JsonParser().parse(boundaryStr).getAsJsonObject();
		JsonArray coordinates = boundaryObj.get("coordinates").getAsJsonArray().get(0).getAsJsonArray();
		return JsonUtils.toElementStream(coordinates).map(cEl -> {
			JsonArray c = cEl.getAsJsonArray();
			return new Coordinate(c.get(0).getAsDouble(), c.get(1).getAsDouble(), "EPSG:4326");
		}).collect(Collectors.toList());
	}

}
