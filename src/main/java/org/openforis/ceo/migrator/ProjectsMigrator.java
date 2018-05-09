package org.openforis.ceo.migrator;

import static org.openforis.ceo.migrator.util.JsonUtils.getMemberValue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.openforis.ceo.migrator.util.JsonUtils;
import org.openforis.collect.manager.SamplingDesignManager;
import org.openforis.collect.manager.SamplingDesignManager.SamplingDesignItemBatchInserter;
import org.openforis.collect.manager.SurveyManager;
import org.openforis.collect.manager.UserManager;
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
import org.openforis.collect.model.CollectSurvey.Availability;
import org.openforis.collect.model.CollectSurvey.PrivacyLevel;
import org.openforis.collect.model.SamplingDesignItem;
import org.openforis.collect.model.User;
import org.openforis.collect.utils.SurveyObjects;
import org.openforis.idm.metamodel.SpatialReferenceSystem;
import org.openforis.idm.model.Coordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Component
public class ProjectsMigrator extends BaseMigrator {

	private static final Logger LOGGER = Logger.getAnonymousLogger();
	private static final int SAMPLE_POINT_WIDTH_M = 10;

	@Autowired
	private SamplingDesignManager samplingDesignManager;
	@Autowired
	private SurveyManager surveyManager;
	@Autowired
	private UserManager userManager;

	public void migrate() {
		LOGGER.log(Level.INFO, "Starting projects migration...");
		JsonArray projects = readSourceFileAsArray("project-list.json");
		JsonUtils.toStream(projects).filter(p -> p.get("id").getAsInt() != 0).forEach(p -> {
			JsonObject ceoProject = p.getAsJsonObject();
			int projectId = ceoProject.get("id").getAsInt();
			LOGGER.log(Level.INFO, String.format("Migrating project %d...", projectId));
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
			
			List<SimpleCodeList> simpleCodeLists = extractCodeLists(ceoProject);
			
			surveyCreationParameters.setCodeLists(simpleCodeLists);
			
			SimpleSurveyCreator surveyCreator = new SimpleSurveyCreator(surveyManager);
			CollectSurvey s = surveyCreator.createTemporarySimpleSurvey(SurveyObjects.adjustInternalName(projectName),
					simpleCodeLists);
			s.setUserGroupId(ceoProject.get("institution").getAsInt());
			s.setId(projectId);
			
			Availability availability = Availability.valueOf(ceoProject.get("availability").getAsString().toUpperCase());
			
			saveGeneratedSurvey(s, availability);
			
			migrateSamplingDesign(s, projectId);
			
			LOGGER.log(Level.INFO, String.format("Project %d migrated successfully.", projectId));
		});
		LOGGER.log(Level.INFO, "Projects migration completed.");
	}

	private void saveGeneratedSurvey(CollectSurvey s, Availability availability) {
		try {
			surveyManager.save(s);
			
			User adminUser = userManager.loadAdminUser();
			
			if (availability != Availability.UNPUBLISHED) {
				surveyManager.publish(s, adminUser);
				s.setAvailability(availability);
				surveyManager.save(s);
			}
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	private List<SimpleCodeList> extractCodeLists(JsonObject ceoProject) {
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
		return simpleCodeLists;
	}

	private void migrateSamplingDesign(CollectSurvey s, int projectId) {
		try (SamplingDesignItemBatchInserter batchInserter = new SamplingDesignItemBatchInserter(samplingDesignManager, s)) {
			JsonArray plotLocations = readSourceFileAsArray(String.format("plot-data-%d.json", projectId));
			AtomicInteger plotIndex = new AtomicInteger();
			plotLocations.forEach(l -> {
				JsonObject plotObj = l.getAsJsonObject();
				SamplingDesignItem plotItem = new SamplingDesignItem();
				plotItem.setSurveyId(s.getId());
				String plotLevelCode = String.valueOf(plotIndex.incrementAndGet());
				plotItem.addLevelCode(plotLevelCode);
				plotItem.setCoordinate(extractCoordinateFromPoint(plotObj.get("center").getAsString()));
				plotItem.addInfoAttribute(plotObj.get("flagged").getAsString());
				
				batchInserter.process(plotItem);
				
				JsonArray samplesArray = plotObj.get("samples").getAsJsonArray();
				AtomicInteger sampleIndex = new AtomicInteger();
				samplesArray.forEach(sampleEl -> {
					JsonObject sampleObj = sampleEl.getAsJsonObject();
					SamplingDesignItem sampleItem = new SamplingDesignItem();
					sampleItem.setSurveyId(s.getId());
					String sampleLevelCode = String.valueOf(sampleIndex.incrementAndGet());
					sampleItem.addLevelCode(plotLevelCode);
					sampleItem.addLevelCode(sampleLevelCode);
					sampleItem.setCoordinate(extractCoordinateFromPoint(sampleObj.get("point").getAsString()));
					batchInserter.process(sampleItem);
				});
			});
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	private Coordinate extractCoordinateFromPoint(String pointStr) {
		JsonObject pointObj = JsonUtils.parseJson(pointStr).getAsJsonObject();
		JsonArray coordinatesArr = pointObj.get("coordinates").getAsJsonArray();
		Coordinate c = new Coordinate(coordinatesArr.get(0).getAsDouble(), coordinatesArr.get(1).getAsDouble(), SpatialReferenceSystem.LAT_LON_SRS_ID);
		return c;
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
