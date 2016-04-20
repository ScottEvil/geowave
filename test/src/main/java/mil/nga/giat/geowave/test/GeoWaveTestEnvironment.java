package mil.nga.giat.geowave.test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.minicluster.impl.MiniAccumuloClusterImpl;
import org.apache.accumulo.minicluster.impl.MiniAccumuloConfigImpl;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.util.VersionInfo;
import org.apache.hadoop.util.VersionUtil;
import org.apache.log4j.Logger;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.opengis.feature.simple.SimpleFeature;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import mil.nga.giat.geowave.core.cli.parser.ManualOperationParams;
import mil.nga.giat.geowave.core.geotime.ingest.SpatialDimensionalityTypeProvider;
import mil.nga.giat.geowave.core.geotime.ingest.SpatialDimensionalityTypeProvider.SpatialIndexBuilder;
import mil.nga.giat.geowave.core.geotime.ingest.SpatialTemporalDimensionalityTypeProvider;
import mil.nga.giat.geowave.core.geotime.store.query.SpatialQuery;
import mil.nga.giat.geowave.core.geotime.store.query.SpatialTemporalQuery;
import mil.nga.giat.geowave.core.ingest.operations.LocalToGeowaveCommand;
import mil.nga.giat.geowave.core.ingest.operations.options.IngestFormatPluginOptions;
import mil.nga.giat.geowave.core.store.CloseableIterator;
import mil.nga.giat.geowave.core.store.index.PrimaryIndex;
import mil.nga.giat.geowave.core.store.operations.remote.ListStatsCommand;
import mil.nga.giat.geowave.core.store.operations.remote.options.DataStorePluginOptions;
import mil.nga.giat.geowave.core.store.operations.remote.options.IndexPluginOptions;
import mil.nga.giat.geowave.core.store.query.DistributableQuery;
import mil.nga.giat.geowave.datastore.accumulo.AccumuloDataStoreFactory;
import mil.nga.giat.geowave.datastore.accumulo.AccumuloOperations;
import mil.nga.giat.geowave.datastore.accumulo.BasicAccumuloOperations;
import mil.nga.giat.geowave.datastore.accumulo.minicluster.MiniAccumuloClusterFactory;
import mil.nga.giat.geowave.datastore.accumulo.operations.config.AccumuloRequiredOptions;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;

abstract public class GeoWaveTestEnvironment
{
	private final static Logger LOGGER = Logger.getLogger(GeoWaveTestEnvironment.class);

	protected static enum DimensionalityType {
		SPATIAL(
				"spatial"),
		SPATIAL_TEMPORAL(
				"spatial_temporal"),
		ALL(
				"spatial,spatial_temporal");
		private final String dimensionalityArg;

		private DimensionalityType(
				final String dimensionalityArg ) {
			this.dimensionalityArg = dimensionalityArg;
		}

		public String getDimensionalityArg() {
			return dimensionalityArg;
		}
	}

	protected static final String TEST_FILTER_START_TIME_ATTRIBUTE_NAME = "StartTime";
	protected static final String TEST_FILTER_END_TIME_ATTRIBUTE_NAME = "EndTime";
	protected static final String TEST_NAMESPACE = "mil_nga_giat_geowave_test";
	protected static final String TEST_RESOURCE_PACKAGE = "mil/nga/giat/geowave/test/";
	protected static final String TEST_CASE_BASE = "data/";
	protected static final String DEFAULT_MINI_ACCUMULO_PASSWORD = "Ge0wave";
	protected static final String HADOOP_WINDOWS_UTIL = "winutils.exe";
	protected static final Object MUTEX = new Object();
	protected static AccumuloOperations accumuloOperations;
	protected static String zookeeper = "z";
	protected static String accumuloInstance = "i";
	protected static String accumuloUser = "u";
	protected static String accumuloPassword = "p";
	protected static MiniAccumuloClusterImpl miniAccumulo;
	protected static File TEMP_DIR = new File(
			"./target/accumulo_temp"); // breaks on windows if temp directory
										// isn't on same drive as project

	protected static final PrimaryIndex DEFAULT_SPATIAL_INDEX = new SpatialDimensionalityTypeProvider().createPrimaryIndex();
	protected static final PrimaryIndex DEFAULT_ALLTIER_SPATIAL_INDEX = new SpatialIndexBuilder().setAllTiers(
			true).createIndex();

	protected static final PrimaryIndex DEFAULT_SPATIAL_TEMPORAL_INDEX = new SpatialTemporalDimensionalityTypeProvider().createPrimaryIndex();

	protected static final AtomicBoolean DEFER_CLEANUP = new AtomicBoolean(
			false);

	protected static boolean isYarn() {
		return VersionUtil.compareVersions(
				VersionInfo.getVersion(),
				"2.2.0") >= 0;
	}

	protected void testLocalIngest(
			final DimensionalityType dimensionalityType,
			final String ingestFilePath,
			final int nthreads ) {
		testLocalIngest(
				dimensionalityType,
				ingestFilePath,
				"geotools-vector",
				nthreads);

	}

	protected void testLocalIngest(
			final DimensionalityType dimensionalityType,
			final String ingestFilePath,
			final String format,
			final int nthreads ) {

		// ingest a shapefile (geotools type) directly into GeoWave using the
		// ingest framework's main method and pre-defined commandline arguments

		// Ingest Formats
		IngestFormatPluginOptions ingestFormatOptions = new IngestFormatPluginOptions();
		ingestFormatOptions.selectPlugin(format);

		// Indexes
		IndexPluginOptions indexOption = new IndexPluginOptions();
		indexOption.selectPlugin(dimensionalityType.getDimensionalityArg());

		// Create the command and execute.
		LocalToGeowaveCommand localIngester = new LocalToGeowaveCommand();
		localIngester.setPluginFormats(ingestFormatOptions);
		localIngester.setInputIndexOptions(Arrays.asList(indexOption));
		localIngester.setInputStoreOptions(getAccumuloStorePluginOptions(TEST_NAMESPACE));
		localIngester.setParameters(
				ingestFilePath,
				null,
				null);
		localIngester.setThreads(nthreads);
		localIngester.execute(new ManualOperationParams());

		verifyStats();

	}

	protected DataStorePluginOptions getAccumuloStorePluginOptions(
			String namespace ) {
		DataStorePluginOptions pluginOptions = new DataStorePluginOptions();
		AccumuloRequiredOptions opts = new AccumuloRequiredOptions();
		opts.setGeowaveNamespace(namespace);
		opts.setUser(accumuloUser);
		opts.setPassword(accumuloPassword);
		opts.setInstance(accumuloInstance);
		opts.setZookeeper(zookeeper);
		pluginOptions.selectPlugin(new AccumuloDataStoreFactory().getName());
		pluginOptions.setFactoryOptions(opts);
		return pluginOptions;
	}

	private void verifyStats() {
		ListStatsCommand listStats = new ListStatsCommand();
		listStats.setInputStoreOptions(getAccumuloStorePluginOptions(TEST_NAMESPACE));
		listStats.setParameters(
				null,
				null);
		try {
			listStats.execute(new ManualOperationParams());
		}
		catch (Exception e) {
			throw new RuntimeException(
					e);
		}
	}

	@BeforeClass
	public static void setup()
			throws IOException {
		synchronized (MUTEX) {
			TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
			if (accumuloOperations == null) {
				zookeeper = System.getProperty("zookeeperUrl");
				accumuloInstance = System.getProperty("instance");
				accumuloUser = System.getProperty("username");
				accumuloPassword = System.getProperty("password");
				if (!isSet(zookeeper) || !isSet(accumuloInstance) || !isSet(accumuloUser) || !isSet(accumuloPassword)) {
					try {

						// TEMP_DIR = Files.createTempDir();
						if (!TEMP_DIR.exists()) {
							if (!TEMP_DIR.mkdirs()) {
								throw new IOException(
										"Could not create temporary directory");
							}
						}
						TEMP_DIR.deleteOnExit();
						final MiniAccumuloConfigImpl config = new MiniAccumuloConfigImpl(
								TEMP_DIR,
								DEFAULT_MINI_ACCUMULO_PASSWORD);
						config.setNumTservers(2);

						miniAccumulo = MiniAccumuloClusterFactory.newAccumuloCluster(
								config,
								GeoWaveTestEnvironment.class);

						miniAccumulo.start();
						zookeeper = miniAccumulo.getZooKeepers();
						accumuloInstance = miniAccumulo.getInstanceName();
						accumuloUser = "root";
						accumuloPassword = DEFAULT_MINI_ACCUMULO_PASSWORD;
					}
					catch (IOException | InterruptedException e) {
						LOGGER.warn(
								"Unable to start mini accumulo instance",
								e);
						LOGGER.info("Check '" + TEMP_DIR.getAbsolutePath() + File.separator + "logs' for more info");
						if (SystemUtils.IS_OS_WINDOWS) {
							LOGGER.warn("For windows, make sure that Cygwin is installed and set a CYGPATH environment variable to %CYGWIN_HOME%/bin/cygpath to successfully run a mini accumulo cluster");
						}
						Assert.fail("Unable to start mini accumulo instance: '" + e.getLocalizedMessage() + "'");
					}
				}
				try {
					accumuloOperations = new BasicAccumuloOperations(
							zookeeper,
							accumuloInstance,
							accumuloUser,
							accumuloPassword,
							TEST_NAMESPACE);
				}
				catch (AccumuloException | AccumuloSecurityException e) {
					LOGGER.warn(
							"Unable to connect to Accumulo",
							e);
					Assert.fail("Could not connect to Accumulo instance: '" + e.getLocalizedMessage() + "'");
				}
			}
		}
	}

	protected static boolean isSet(
			final String str ) {
		return (str != null) && !str.isEmpty();
	}

	@SuppressFBWarnings(value = {
		"SWL_SLEEP_WITH_LOCK_HELD"
	}, justification = "Sleep in lock while waiting for external resources")
	@AfterClass
	public static void cleanup() {
		synchronized (MUTEX) {
			if (!DEFER_CLEANUP.get()) {

				if (accumuloOperations == null) {
					Assert.fail("Invalid state <null> for accumulo operations during CLEANUP phase");
				}
				try {
					accumuloOperations.deleteAll();
				}
				catch (TableNotFoundException | AccumuloSecurityException | AccumuloException ex) {
					LOGGER.error(
							"Unable to clear accumulo namespace",
							ex);
					Assert.fail("Index not deleted successfully");
				}

				accumuloOperations = null;
				zookeeper = null;
				accumuloInstance = null;
				accumuloUser = null;
				accumuloPassword = null;
				if (miniAccumulo != null) {
					try {
						miniAccumulo.stop();
						miniAccumulo = null;
					}
					catch (IOException | InterruptedException e) {
						LOGGER.warn(
								"Unable to stop mini accumulo instance",
								e);
					}
				}
				if (TEMP_DIR != null) {
					try {
						// sleep because mini accumulo processes still have a
						// hold on the log files and there is no hook to get
						// notified when it is completely stopped

						Thread.sleep(2000);
						FileUtils.deleteDirectory(TEMP_DIR);

						TEMP_DIR = null;
					}
					catch (final IOException | InterruptedException e) {
						LOGGER.warn(
								"Unable to delete mini Accumulo temporary directory",
								e);
					}
				}
			}
		}
	}

	public static void addAuthorization(
			final String auth,
			final BasicAccumuloOperations accumuloOperations ) {
		try {
			accumuloOperations.insureAuthorization(auth);
		}
		catch (AccumuloException | AccumuloSecurityException e) {
			LOGGER.warn(
					"Unable to alter authorization for Accumulo user",
					e);
			Assert.fail("Unable to alter authorization for Accumulo user: '" + e.getLocalizedMessage() + "'");
		}
	}

	protected static long hashCentroid(
			final Geometry geometry ) {
		final Point centroid = geometry.getCentroid();
		return Double.doubleToLongBits(centroid.getX()) + Double.doubleToLongBits(centroid.getY() * 31);
	}

	protected static class ExpectedResults
	{
		public Set<Long> hashedCentroids;
		public int count;

		@SuppressFBWarnings({
			"URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"
		})
		public ExpectedResults(
				final Set<Long> hashedCentroids,
				final int count ) {
			this.hashedCentroids = hashedCentroids;
			this.count = count;
		}
	}

	protected static ExpectedResults getExpectedResults(
			final CloseableIterator<?> results )
			throws IOException {
		final Set<Long> hashedCentroids = new HashSet<Long>();
		int expectedResultCount = 0;
		try {
			while (results.hasNext()) {
				final Object obj = results.next();
				if (obj instanceof SimpleFeature) {
					expectedResultCount++;
					final SimpleFeature feature = (SimpleFeature) obj;
					hashedCentroids.add(hashCentroid((Geometry) feature.getDefaultGeometry()));
				}
			}
		}
		finally {
			results.close();
		}
		return new ExpectedResults(
				hashedCentroids,
				expectedResultCount);
	}

	protected static ExpectedResults getExpectedResults(
			final URL[] expectedResultsResources )
			throws IOException {
		final Map<String, Object> map = new HashMap<String, Object>();
		DataStore dataStore = null;
		final Set<Long> hashedCentroids = new HashSet<Long>();
		int expectedResultCount = 0;
		for (final URL expectedResultsResource : expectedResultsResources) {
			map.put(
					"url",
					expectedResultsResource);
			SimpleFeatureIterator featureIterator = null;
			try {
				dataStore = DataStoreFinder.getDataStore(map);
				if (dataStore == null) {
					LOGGER.error("Could not get dataStore instance, getDataStore returned null");
					throw new IOException(
							"Could not get dataStore instance, getDataStore returned null");
				}
				final SimpleFeatureCollection expectedResults = dataStore.getFeatureSource(
						dataStore.getNames().get(
								0)).getFeatures();

				expectedResultCount += expectedResults.size();
				// unwrap the expected results into a set of features IDs so its
				// easy to check against
				featureIterator = expectedResults.features();
				while (featureIterator.hasNext()) {
					hashedCentroids.add(hashCentroid((Geometry) featureIterator.next().getDefaultGeometry()));
				}
			}
			finally {
				IOUtils.closeQuietly(featureIterator);
				if (dataStore != null) {
					dataStore.dispose();
				}
			}
		}
		return new ExpectedResults(
				hashedCentroids,
				expectedResultCount);
	}

	protected static DistributableQuery resourceToQuery(
			final URL filterResource )
			throws IOException {
		return featureToQuery(resourceToFeature(filterResource));
	}

	protected static SimpleFeature resourceToFeature(
			final URL filterResource )
			throws IOException {
		final Map<String, Object> map = new HashMap<String, Object>();
		DataStore dataStore = null;
		map.put(
				"url",
				filterResource);
		final SimpleFeature savedFilter;
		SimpleFeatureIterator sfi = null;
		try {
			dataStore = DataStoreFinder.getDataStore(map);
			if (dataStore == null) {
				LOGGER.error("Could not get dataStore instance, getDataStore returned null");
				throw new IOException(
						"Could not get dataStore instance, getDataStore returned null");
			}
			// just grab the first feature and use it as a filter
			sfi = dataStore.getFeatureSource(
					dataStore.getNames().get(
							0)).getFeatures().features();
			savedFilter = sfi.next();

		}
		finally {
			if (sfi != null) {
				sfi.close();
			}
			if (dataStore != null) {
				dataStore.dispose();
			}
		}
		return savedFilter;
	}

	protected static DistributableQuery featureToQuery(
			final SimpleFeature savedFilter ) {
		final Geometry filterGeometry = (Geometry) savedFilter.getDefaultGeometry();
		final Object startObj = savedFilter.getAttribute(TEST_FILTER_START_TIME_ATTRIBUTE_NAME);
		final Object endObj = savedFilter.getAttribute(TEST_FILTER_END_TIME_ATTRIBUTE_NAME);

		if ((startObj != null) && (endObj != null)) {
			// if we can resolve start and end times, make it a spatial temporal
			// query
			Date startDate = null, endDate = null;
			if (startObj instanceof Calendar) {
				startDate = ((Calendar) startObj).getTime();
			}
			else if (startObj instanceof Date) {
				startDate = (Date) startObj;
			}
			if (endObj instanceof Calendar) {
				endDate = ((Calendar) endObj).getTime();
			}
			else if (endObj instanceof Date) {
				endDate = (Date) endObj;
			}
			if ((startDate != null) && (endDate != null)) {
				return new SpatialTemporalQuery(
						startDate,
						endDate,
						filterGeometry);
			}
		}
		// otherwise just return a spatial query
		return new SpatialQuery(
				filterGeometry);
	}

	public static void addAuthorization(
			final String auth ) {
		try {
			synchronized (MUTEX) {
				accumuloOperations.insureAuthorization(auth);
			}
		}
		catch (AccumuloException | AccumuloSecurityException e) {
			LOGGER.warn(
					"Unable to alter authorization for Accumulo user",
					e);
			Assert.fail("Unable to alter authorization for Accumulo user: '" + e.getLocalizedMessage() + "'");
		}
	}

	/**
	 * Unzips the contents of a zip file to a target output directory, deleting
	 * anything that existed beforehand
	 * 
	 * @param zipInput
	 *            input zip file
	 * @param outputFolder
	 *            zip file output folder
	 */
	protected static void unZipFile(
			final File zipInput,
			final String outputFolder ) {

		try {
			final File of = new File(
					outputFolder);
			if (!of.exists()) {
				if (!of.mkdirs()) {
					throw new IOException(
							"Could not create temporary directory: " + of.toString());
				}
			}
			else {
				FileUtil.fullyDelete(of);
			}
			final ZipFile z = new ZipFile(
					zipInput);
			z.extractAll(outputFolder);
		}
		catch (final ZipException e) {
			LOGGER.warn(
					"Unable to extract test data",
					e);
			Assert.fail("Unable to extract test data: '" + e.getLocalizedMessage() + "'");
		}
		catch (final IOException e) {
			LOGGER.warn(
					"Unable to create temporary directory: " + outputFolder,
					e);
			Assert.fail("Unable to extract test data: '" + e.getLocalizedMessage() + "'");
		}
	}

	static protected void replaceParameters(
			final Map<String, String> values,
			final File file )
			throws IOException {
		{
			String str = FileUtils.readFileToString(file);
			for (final Entry<String, String> entry : values.entrySet()) {
				str = str.replaceAll(
						entry.getKey(),
						entry.getValue());
			}
			FileUtils.deleteQuietly(file);
			FileUtils.write(
					file,
					str);
		}
	}
}
