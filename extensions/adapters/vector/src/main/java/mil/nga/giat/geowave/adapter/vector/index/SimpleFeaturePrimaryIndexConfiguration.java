package mil.nga.giat.geowave.adapter.vector.index;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import mil.nga.giat.geowave.adapter.vector.utils.SimpleFeatureUserDataConfiguration;

import org.opengis.feature.simple.SimpleFeatureType;

public class SimpleFeaturePrimaryIndexConfiguration implements
		SimpleFeatureUserDataConfiguration,
		java.io.Serializable
{
	private static final long serialVersionUID = -7425830022998223202L;
	public static final String INDEX_NAME = "PrimaryIndexName";
	private List<String> indexNames = null;

	public static final List<String> getIndexNames(
			final SimpleFeatureType type ) {
		Object obj = type.getUserData().get(
				INDEX_NAME);
		if (obj != null) {
			return Arrays.asList(obj.toString().split(
					","));
		}
		return Collections.emptyList();
	}

	@Override
	public void updateType(
			final SimpleFeatureType type ) {
		type.getUserData().put(
				INDEX_NAME,
				indexNames);
	}

	@Override
	public void configureFromType(
			final SimpleFeatureType type ) {
		this.indexNames = getIndexNames(type);
	}

	public List<String> getIndexNames() {
		return indexNames;
	}

	public void setIndexNames(
			List<String> indexNames ) {
		this.indexNames = indexNames;
	}

}
