package mil.nga.giat.geowave.types.landsat8;

import java.io.File;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.opengis.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Landsat8CommandLineOptions
{
	private final static Logger LOGGER = LoggerFactory.getLogger(Landsat8CommandLineOptions.class);
	private static final String DEFAULT_WORKSPACE_DIR = "landsat8";
	private static final String WORKSPACE_DIR_OPTION = "wsdir";
	private static final String CQL_FILTER_OPTION = "cql";
	private static final String RETAIN_IMAGES_OPTION = "retain-images";
	private static final String ONLY_SCENES_SINCE_LAST_RUN_OPTION = "since-last-run";

	private final String workspaceDir;
	private final String cqlFilter;
	private final boolean retainImageFiles;
	private final boolean onlyScenesSinceLastRun;

	public Landsat8CommandLineOptions(
			final String workspaceDir,
			final String cqlFilter,
			final boolean retainImageFiles,
			final boolean onlyScenesSinceLastRun ) {
		this.workspaceDir = workspaceDir;
		this.cqlFilter = cqlFilter;
		this.retainImageFiles = retainImageFiles;
		this.onlyScenesSinceLastRun = onlyScenesSinceLastRun;
	}

	public static Landsat8CommandLineOptions parseOptions(
			final CommandLine commandLine ) {
		String workspaceDir;
		if (commandLine.hasOption(WORKSPACE_DIR_OPTION)) {
			workspaceDir = commandLine.getOptionValue(WORKSPACE_DIR_OPTION);
		}
		else {
			workspaceDir = System.getProperty("java.io.tmpdir") + File.separator + DEFAULT_WORKSPACE_DIR;
		}
		final String cqlFilter;
		if (commandLine.hasOption(CQL_FILTER_OPTION)) {
			cqlFilter = commandLine.getOptionValue(CQL_FILTER_OPTION);
		}
		else {
			cqlFilter = null;
		}
		final boolean retainImageFiles = commandLine.hasOption(RETAIN_IMAGES_OPTION);
		final boolean onlyScenesSinceLastRun = commandLine.hasOption(ONLY_SCENES_SINCE_LAST_RUN_OPTION);
		return new Landsat8CommandLineOptions(
				workspaceDir,
				cqlFilter,
				retainImageFiles,
				onlyScenesSinceLastRun);
	}

	public static void applyOptions(
			final Options allOptions ) {
		final Option workspaceDir = new Option(
				WORKSPACE_DIR_OPTION,
				true,
				"A local directory to write temporary files needed for landsat 8 ingest. Default is <TEMP_DIR>/landsat8");
		workspaceDir.setRequired(false);
		allOptions.addOption(workspaceDir);
		final Option cqlFilter = new Option(
				CQL_FILTER_OPTION,
				true,
				"An optional CQL expression to filter the ingested imagery. The feature type for the expression has the following attributes: shape (Geometry), entityId (String), acquisitionDate (Date), cloudCover (double), processingLevel (String), path (int), row (int)");
		cqlFilter.setRequired(false);
		allOptions.addOption(cqlFilter);

		final Option retainImageFiles = new Option(
				RETAIN_IMAGES_OPTION,
				false,
				"An option to keep the images that are ingested in the local workspace directory.  By default it will delete the local file after it is ingested successfully.");
		retainImageFiles.setRequired(false);
		allOptions.addOption(retainImageFiles);

		final Option onlyScenesSinceLastRun = new Option(
				ONLY_SCENES_SINCE_LAST_RUN_OPTION,
				false,
				"An option to check the scenes list from the workspace and if it exists, to only ingest data since the last scene.");
		onlyScenesSinceLastRun.setRequired(false);
		allOptions.addOption(onlyScenesSinceLastRun);
	}

	public String getWorkspaceDir() {
		return workspaceDir;
	}

	public String getCqlExpression() {
		return cqlFilter;
	}

	public Filter getCqlFilter() {
		if (cqlFilter != null) {
			try {
				return ECQL.toFilter(cqlFilter);
			}
			catch (final CQLException e) {
				LOGGER.error(
						"Unable to parse CQL expession",
						e);
				System.exit(-1);
			}
		}
		return null;
	}

	public boolean isRetainImageFiles() {
		return retainImageFiles;
	}

	public boolean isOnlyScenesSinceLastRun() {
		return onlyScenesSinceLastRun;
	}
}
