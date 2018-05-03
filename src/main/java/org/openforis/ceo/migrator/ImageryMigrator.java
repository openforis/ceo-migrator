package org.openforis.ceo.migrator;

import static org.openforis.ceo.migrator.util.JsonUtils.getMemberValue;

import org.openforis.collect.manager.ImageryManager;
import org.openforis.collect.model.Imagery;
import org.openforis.collect.model.Imagery.Visibility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

@Component
public class ImageryMigrator extends BaseMigrator {

	@Autowired
	private ImageryManager imageryManager;
	
	public void migrate() {
		JsonArray array = readSourceFileAsArray("imagery-list.json");

		array.forEach(el -> {
			JsonObject obj = el.getAsJsonObject();
			Imagery i = new Imagery();
			
			i.setId(obj.get("id").getAsInt());
			i.setVisibilityEnum(Visibility.PRIVATE);
			i.setTitle(obj.get("title").getAsString());
			i.setAttribution(obj.get("attribution").getAsString());
			i.setExtent(getMemberValue(obj, "extent", String.class));
			i.setSourceConfig(obj.get("sourceConfig").getAsJsonObject().toString());
			
			imageryManager.save(i, null);
		});
	}

}
