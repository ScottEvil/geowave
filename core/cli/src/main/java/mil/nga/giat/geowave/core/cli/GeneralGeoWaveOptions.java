package mil.nga.giat.geowave.core.cli;

import com.beust.jcommander.Parameter;

public class GeneralGeoWaveOptions
{
	@Parameter(names = "--help", description = "Display help", help = true)
	private final boolean help = false;

	@Parameter(names = "--list-datastores", description = "List the available data stores", help = true)
	private final boolean listDataStores = false;

	public boolean isHelp() {
		return help;
	}

	public boolean isListDataStores() {
		return listDataStores;
	}
}
