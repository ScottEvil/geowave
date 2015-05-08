package mil.nga.giat.geowave.format.landsat8;

import mil.nga.giat.geowave.adapter.raster.adapter.RasterDataAdapter;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

public class Landsat8IngestCommandLineOptions
{
	private static final String DEFAULT_COVERAGE_NAME = "${entityId}";
	private static final String COVERAGE_NAME_OPTION = "coveragename";
	private static final String CREATE_HISTOGRAM_OPTION = "histogram";
	private static final String CREATE_PYRAMID_OPTION = "pyramid";
	private static final String COVERAGE_PER_BAND_OPTION = "coverageperband";
	private static final String RETAIN_IMAGES_OPTION = "retainimages";
	private static final String TILE_SIZE_OPTION = "tilesize";
	private final boolean createHistogram;
	private final boolean createPyramid;
	private final boolean coveragePerBand;
	private final boolean retainImages;
	private final int tileSize;
	private final String coverageName;

	public Landsat8IngestCommandLineOptions(
			final String coverageName,
			final boolean createHistogram,
			final boolean createPyramid,
			final boolean coveragePerBand,
			final boolean retainImages,
			final int tileSize ) {
		this.coverageName = coverageName;
		this.createHistogram = createHistogram;
		this.createPyramid = createPyramid;
		this.coveragePerBand = coveragePerBand;
		this.retainImages = retainImages;
		this.tileSize = tileSize;
	}

	public static Landsat8IngestCommandLineOptions parseOptions(
			final CommandLine commandLine ) {
		String coverageName;
		if (commandLine.hasOption(COVERAGE_NAME_OPTION)) {
			coverageName = commandLine.getOptionValue(COVERAGE_NAME_OPTION);
		}
		else {
			coverageName = DEFAULT_COVERAGE_NAME;
		}
		final boolean createHistogram = commandLine.hasOption(CREATE_HISTOGRAM_OPTION);
		final boolean createPyramid = commandLine.hasOption(CREATE_PYRAMID_OPTION);
		final boolean coveragePerBand = commandLine.hasOption(COVERAGE_PER_BAND_OPTION);
		final boolean retainImageFiles = commandLine.hasOption(RETAIN_IMAGES_OPTION);
		final int tileSize;
		if (commandLine.hasOption(TILE_SIZE_OPTION)) {
			tileSize = Integer.parseInt(commandLine.getOptionValue(TILE_SIZE_OPTION));
		}
		else {
			tileSize = RasterDataAdapter.
		}
		return new Landsat8IngestCommandLineOptions(
				coverageName,
				createHistogram,
				createPyramid,
				coveragePerBand,
				retainImageFiles,tileSize);
	}

	public static void applyOptions(
			final Options allOptions ) {
		final Option coverageName = new Option(
				COVERAGE_NAME_OPTION,
				true,
				"The name to give to each unique coverage. Freemarker templating can be used for variable substition based on the same attributes used for filtering.  The default coverage name is '${entityId}'");
		coverageName.setRequired(false);
		allOptions.addOption(coverageName);

		final Option createHistogram = new Option(
				CREATE_HISTOGRAM_OPTION,
				false,
				"An option to store the histogram of the values of the coverage so that histogram equalization will be performed");
		createHistogram.setRequired(false);
		allOptions.addOption(createHistogram);

		final Option createPyramid = new Option(
				CREATE_PYRAMID_OPTION,
				false,
				"An option to store an image pyramid for the coverage");
		createPyramid.setRequired(false);
		allOptions.addOption(createPyramid);

		final Option coveragePerBand = new Option(
				COVERAGE_PER_BAND_OPTION,
				false,
				"An option to isolate each coverage per band.  The default behavior will be to merge all the bands of a scene into a single coverage");
		coveragePerBand.setRequired(false);
		allOptions.addOption(coveragePerBand);

		final Option retainImageFiles = new Option(
				RETAIN_IMAGES_OPTION,
				false,
				"An option to keep the images that are ingested in the local workspace directory.  By default it will delete the local file after it is ingested successfully.");
		retainImageFiles.setRequired(false);
		allOptions.addOption(retainImageFiles);
	}

	public boolean isCreateHistogram() {
		return createHistogram;
	}

	public boolean isCreatePyramid() {
		return createPyramid;
	}

	public boolean isCoveragePerBand() {
		return coveragePerBand;
	}

	public boolean isRetainImages() {
		return retainImages;
	}

	public String getCoverageName() {
		return coverageName;
	}
}
