package mil.nga.giat.geowave.test.mapreduce;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.MRJobConfig;
import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import mil.nga.giat.geowave.core.cli.parser.ManualOperationParams;
import mil.nga.giat.geowave.core.ingest.operations.LocalToMapReduceToGeowaveCommand;
import mil.nga.giat.geowave.core.ingest.operations.MapReduceToGeowaveCommand;
import mil.nga.giat.geowave.core.ingest.operations.options.IngestFormatPluginOptions;
import mil.nga.giat.geowave.core.store.config.ConfigUtils;
import mil.nga.giat.geowave.core.store.operations.remote.options.IndexPluginOptions;
import mil.nga.giat.geowave.datastore.accumulo.operations.config.AccumuloRequiredOptions;
import mil.nga.giat.geowave.test.GeoWaveTestEnvironment;

abstract public class MapReduceTestEnvironment extends
		GeoWaveTestEnvironment
{
	private final static Logger LOGGER = Logger.getLogger(MapReduceTestEnvironment.class);

	protected static final String HDFS_BASE_DIRECTORY = "test_tmp";
	protected static final String DEFAULT_JOB_TRACKER = "local";
	protected static final String EXPECTED_RESULTS_KEY = "EXPECTED_RESULTS";
	protected static final int MIN_INPUT_SPLITS = 3;
	protected static final int MAX_INPUT_SPLITS = 5;
	protected static String jobtracker;
	protected static String hdfs;
	protected static boolean hdfsProtocol;
	protected static String hdfsBaseDirectory;

	protected void testMapReduceIngest(
			final DimensionalityType dimensionalityType,
			final String ingestFilePath ) {
		testMapReduceIngest(
				dimensionalityType,
				"gpx",
				ingestFilePath);
	}

	protected void testMapReduceIngest(
			final DimensionalityType dimensionalityType,
			final String format,
			final String ingestFilePath ) {
		// ingest gpx data directly into GeoWave using the
		// ingest framework's main method and pre-defined commandline arguments
		LOGGER.warn("Ingesting '" + ingestFilePath + "' - this may take several minutes...");
		String[] args = null;

		// Indexes
		String[] indexTypes = dimensionalityType.getDimensionalityArg().split(
				",");
		List<IndexPluginOptions> indexOptions = new ArrayList<IndexPluginOptions>(
				indexTypes.length);
		for (String indexType : indexTypes) {
			IndexPluginOptions indexOption = new IndexPluginOptions();
			indexOption.selectPlugin(indexType);
			indexOptions.add(indexOption);
		}
		// Ingest Formats
		IngestFormatPluginOptions ingestFormatOptions = new IngestFormatPluginOptions();
		ingestFormatOptions.selectPlugin(format);

		LocalToMapReduceToGeowaveCommand mrGw = new LocalToMapReduceToGeowaveCommand();

		mrGw.setInputIndexOptions(indexOptions);
		mrGw.setInputStoreOptions(getAccumuloStorePluginOptions(TEST_NAMESPACE));

		mrGw.setPluginFormats(ingestFormatOptions);
		mrGw.setParameters(
				ingestFilePath,
				hdfs,
				hdfsBaseDirectory,
				null,
				null);
		mrGw.getMapReduceOptions().setJobTrackerHostPort(
				jobtracker);

		mrGw.execute(new ManualOperationParams());
	}

	@BeforeClass
	public static void setVariables()
			throws IOException {
		GeoWaveTestEnvironment.setup();
		hdfs = System.getProperty("hdfs");
		jobtracker = System.getProperty("jobtracker");
		if (!isSet(hdfs)) {
			hdfs = "file:///";

			hdfsBaseDirectory = TEMP_DIR.toURI().toURL().toString() + "/" + HDFS_BASE_DIRECTORY;
			hdfsProtocol = false;
		}
		else {
			hdfsBaseDirectory = HDFS_BASE_DIRECTORY;
			if (!hdfs.contains("://")) {
				hdfs = "hdfs://" + hdfs;
				hdfsProtocol = true;
			}
			else {
				hdfsProtocol = hdfs.toLowerCase().startsWith(
						"hdfs://");
			}
		}
		if (!isSet(jobtracker)) {
			jobtracker = DEFAULT_JOB_TRACKER;
		}
	}

	@AfterClass
	public static void cleanupHdfsFiles() {
		if (hdfsProtocol) {
			final Path tmpDir = new Path(
					hdfsBaseDirectory);
			try {
				final FileSystem fs = FileSystem.get(getConfiguration());
				fs.delete(
						tmpDir,
						true);
			}
			catch (final IOException e) {
				LOGGER.error(
						"Unable to delete HDFS temp directory",
						e);
			}
		}
	}

	public static void filterConfiguration(
			final Configuration conf ) {
		// final parameters, can't be overriden
		conf.unset("mapreduce.job.end-notification.max.retry.interval");
		conf.unset("mapreduce.job.end-notification.max.attempts");

		// deprecated parameters (added in by default since we used the
		// Configuration() constructor (everything is set))
		conf.unset("session.id");
		conf.unset("mapred.jar");
		conf.unset("fs.default.name");
		conf.unset("mapred.map.tasks.speculative.execution");
		conf.unset("mapred.reduce.tasks");
		conf.unset("mapred.reduce.tasks.speculative.execution");
		conf.unset("mapred.mapoutput.value.class");
		conf.unset("mapred.used.genericoptionsparser");
		conf.unset("mapreduce.map.class");
		conf.unset("mapred.job.name");
		conf.unset("mapreduce.inputformat.class");
		conf.unset("mapred.input.dir");
		conf.unset("mapreduce.outputformat.class");
		conf.unset("mapred.map.tasks");
		conf.unset("mapred.mapoutput.key.class");
		conf.unset("mapred.working.dir");
	}

	protected static Configuration getConfiguration() {
		final Configuration conf = new Configuration();
		conf.set(
				"fs.defaultFS",
				hdfs);
		conf.set(
				"fs.hdfs.impl",
				org.apache.hadoop.hdfs.DistributedFileSystem.class.getName());
		conf.set(
				"mapreduce.jobtracker.address",
				jobtracker);
		// for travis-ci to run, we want to limit the memory consumption
		conf.setInt(
				MRJobConfig.IO_SORT_MB,
				10);

		filterConfiguration(conf);

		return conf;

	}

	protected static Map<String, String> getAccumuloConfigOptions() {
		AccumuloRequiredOptions opts = new AccumuloRequiredOptions();
		opts.setUser(accumuloUser);
		opts.setPassword(accumuloPassword);
		opts.setInstance(accumuloInstance);
		opts.setZookeeper(zookeeper);
		opts.setGeowaveNamespace(TEST_NAMESPACE);
		Map<String, String> mapOpts = ConfigUtils.populateListFromOptions(opts);
		return mapOpts;
	}
}
