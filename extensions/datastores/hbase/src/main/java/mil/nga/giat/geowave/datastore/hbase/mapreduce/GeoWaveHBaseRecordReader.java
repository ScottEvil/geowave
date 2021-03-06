package mil.nga.giat.geowave.datastore.hbase.mapreduce;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.log4j.Logger;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import mil.nga.giat.geowave.core.store.CloseableIterator;
import mil.nga.giat.geowave.core.store.CloseableIteratorWrapper;
import mil.nga.giat.geowave.core.store.adapter.AdapterStore;
import mil.nga.giat.geowave.core.store.filter.QueryFilter;
import mil.nga.giat.geowave.core.store.index.PrimaryIndex;
import mil.nga.giat.geowave.core.store.query.DistributableQuery;
import mil.nga.giat.geowave.core.store.query.QueryOptions;
import mil.nga.giat.geowave.datastore.hbase.mapreduce.input.RangeLocationPair;
import mil.nga.giat.geowave.datastore.hbase.operations.BasicHBaseOperations;
import mil.nga.giat.geowave.datastore.hbase.query.InputFormatHBaseRangeQuery;
import mil.nga.giat.geowave.mapreduce.input.GeoWaveInputFormat;
import mil.nga.giat.geowave.mapreduce.input.GeoWaveInputKey;

/**
 * This class is used by the GeoWaveInputFormat to read data from an Accumulo
 * data store.
 * 
 * @param <T>
 *            The native type for the reader
 */
public class GeoWaveHBaseRecordReader<T> extends
		RecordReader<GeoWaveInputKey, T>
{

	protected static class ProgressPerRange
	{
		private final float startProgress;
		private final float deltaProgress;

		public ProgressPerRange(
				final float startProgress,
				final float endProgress ) {
			this.startProgress = startProgress;
			this.deltaProgress = endProgress - startProgress;
		}

		public float getOverallProgress(
				final float rangeProgress ) {
			return startProgress + (rangeProgress * deltaProgress);
		}
	}

	protected static final Logger LOGGER = Logger.getLogger(GeoWaveHBaseRecordReader.class);
	protected long numKeysRead;
	protected CloseableIterator<?> iterator;
	// protected Key currentAccumuloKey = null;
	protected Map<RangeLocationPair, ProgressPerRange> progressPerRange;
	protected GeoWaveInputKey currentGeoWaveKey = null;
	protected RangeLocationPair currentGeoWaveRangeIndexPair = null;
	protected T currentValue = null;
	protected GeoWaveHBaseInputSplit split;
	protected DistributableQuery query;
	protected QueryOptions queryOptions;
	protected boolean isOutputWritable;
	protected AdapterStore adapterStore;
	protected BasicHBaseOperations operations;

	public GeoWaveHBaseRecordReader(
			final DistributableQuery query,
			final QueryOptions queryOptions,
			final boolean isOutputWritable,
			final AdapterStore adapterStore,
			final BasicHBaseOperations operations ) {
		this.query = query;
		this.queryOptions = queryOptions;
		this.isOutputWritable = isOutputWritable;
		this.adapterStore = adapterStore;
		this.operations = operations;
	}

	/**
	 * Initialize a scanner over the given input split using this task attempt
	 * configuration.
	 */
	@Override
	public void initialize(
			final InputSplit inSplit,
			final TaskAttemptContext attempt )
			throws IOException {
		split = (GeoWaveHBaseInputSplit) inSplit;

		numKeysRead = 0;

		final Map<RangeLocationPair, CloseableIterator<?>> iteratorsPerRange = new LinkedHashMap<RangeLocationPair, CloseableIterator<?>>();

		final Set<PrimaryIndex> indices = split.getIndices();
		BigDecimal sum = BigDecimal.ZERO;

		final Map<RangeLocationPair, BigDecimal> incrementalRangeSums = new LinkedHashMap<RangeLocationPair, BigDecimal>();

		for (final PrimaryIndex i : indices) {
			final List<RangeLocationPair> ranges = split.getRanges(i);
			List<QueryFilter> queryFilters = null;
			if (query != null) {
				queryFilters = query.createFilters(i.getIndexModel());
			}
			for (final RangeLocationPair r : ranges) {
				final QueryOptions rangeQueryOptions = new QueryOptions(
						queryOptions);
				rangeQueryOptions.setIndex(i);
				iteratorsPerRange.put(
						r,
						new InputFormatHBaseRangeQuery(
								adapterStore,
								i,
								r.getRange(),
								queryFilters,
								isOutputWritable,
								queryOptions).query(
								operations,
								adapterStore,
								// TODO Support subsampling
								// queryOptions.getMaxResolutionSubsamplingPerDimension(),
								queryOptions.getLimit()));
				incrementalRangeSums.put(
						r,
						sum);
				sum = sum.add(BigDecimal.valueOf(r.getCardinality()));
			}
		}

		// finally we can compute percent progress
		progressPerRange = new LinkedHashMap<RangeLocationPair, ProgressPerRange>();
		RangeLocationPair prevRangeIndex = null;
		float prevProgress = 0f;
		for (final Entry<RangeLocationPair, BigDecimal> entry : incrementalRangeSums.entrySet()) {
			final BigDecimal value = entry.getValue();
			final float progress = value.divide(
					sum,
					RoundingMode.HALF_UP).floatValue();
			if (prevRangeIndex != null) {
				progressPerRange.put(
						prevRangeIndex,
						new ProgressPerRange(
								prevProgress,
								progress));
			}
			prevRangeIndex = entry.getKey();
			prevProgress = progress;
		}
		progressPerRange.put(
				prevRangeIndex,
				new ProgressPerRange(
						prevProgress,
						1f));
		// concatenate iterators
		iterator = new CloseableIteratorWrapper<Object>(
				new Closeable() {
					@Override
					public void close()
							throws IOException {
						for (final CloseableIterator<?> it : iteratorsPerRange.values()) {
							it.close();
						}
					}
				},
				concatenateWithCallback(
						iteratorsPerRange.entrySet().iterator(),
						new NextRangeCallback() {

							@Override
							public void setRange(
									final RangeLocationPair indexPair ) {
								currentGeoWaveRangeIndexPair = indexPair;
							}
						}));

	}

	@Override
	public void close() {
		if (iterator != null) {
			try {
				iterator.close();
			}
			catch (final IOException e) {
				LOGGER.warn(
						"Unable to close iterator",
						e);
			}
		}
	}

	@Override
	public boolean nextKeyValue()
			throws IOException,
			InterruptedException {
		if (iterator != null) {
			if (iterator.hasNext()) {
				++numKeysRead;
				final Object value = iterator.next();
				if (value instanceof Entry) {
					final Entry<GeoWaveInputKey, T> entry = (Entry<GeoWaveInputKey, T>) value;
					currentGeoWaveKey = entry.getKey();
					// TODO implement progress reporting
					// if (currentGeoWaveKey == null) {
					// currentAccumuloKey = null;
					// }
					// else if (currentGeoWaveKey.getInsertionId() != null) {
					// // just use the insertion ID for progress
					// currentAccumuloKey = new Key(
					// new Text(
					// currentGeoWaveKey.getInsertionId().getBytes()));
					// }
					currentValue = entry.getValue();
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public float getProgress()
			throws IOException,
			InterruptedException {
		// TODO implement progress reporting
		return 0;
	}

	@Override
	public GeoWaveInputKey getCurrentKey()
			throws IOException,
			InterruptedException {
		return currentGeoWaveKey;
	}

	@Override
	public T getCurrentValue()
			throws IOException,
			InterruptedException {
		return currentValue;
	}

	private static interface NextRangeCallback
	{
		public void setRange(
				RangeLocationPair indexPair );
	}

	/**
	 * Mostly guava's concatenate method, but there is a need for a callback
	 * between iterators
	 */
	private static Iterator<Object> concatenateWithCallback(
			final Iterator<Entry<RangeLocationPair, CloseableIterator<?>>> inputs,
			final NextRangeCallback nextRangeCallback ) {
		Preconditions.checkNotNull(inputs);
		return new Iterator<Object>() {
			Iterator<?> currentIterator = Iterators.emptyIterator();
			Iterator<?> removeFrom;

			@Override
			public boolean hasNext() {
				boolean currentHasNext;
				while (!(currentHasNext = Preconditions.checkNotNull(
						currentIterator).hasNext()) && inputs.hasNext()) {
					final Entry<RangeLocationPair, CloseableIterator<?>> entry = inputs.next();
					nextRangeCallback.setRange(entry.getKey());
					currentIterator = entry.getValue();
				}
				return currentHasNext;
			}

			@Override
			public Object next() {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}
				removeFrom = currentIterator;
				return currentIterator.next();
			}

			@SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH", justification = "Precondition catches null")
			@Override
			public void remove() {
				Preconditions.checkState(
						removeFrom != null,
						"no calls to next() since last call to remove()");
				removeFrom.remove();
				removeFrom = null;
			}
		};
	}

	// TODO implement progress reporting
	// @Override
	// public float getProgress()
	// throws IOException {
	// if ((numKeysRead > 0) && (currentAccumuloKey == null)) {
	// return 1.0f;
	// }
	// if (currentGeoWaveRangeIndexPair == null) {
	// return 0.0f;
	// }
	// final ProgressPerRange progress =
	// progressPerRange.get(currentGeoWaveRangeIndexPair);
	// if (progress == null) {
	// return getProgressForRange(
	// currentGeoWaveRangeIndexPair.getRange(),
	// currentAccumuloKey);
	// }
	// return getOverallProgress(
	// currentGeoWaveRangeIndexPair.getRange(),
	// currentAccumuloKey,
	// progress);
	// }
	//
	// private static float getOverallProgress(
	// final Range range,
	// final Key currentKey,
	// final ProgressPerRange progress ) {
	// final float rangeProgress = getProgressForRange(
	// range,
	// currentKey);
	// return progress.getOverallProgress(rangeProgress);
	// }
	//
	// private static float getProgressForRange(
	// final ByteSequence start,
	// final ByteSequence end,
	// final ByteSequence position ) {
	// final int maxDepth = Math.min(
	// Math.max(
	// end.length(),
	// start.length()),
	// position.length());
	// final BigInteger startBI = new BigInteger(
	// AccumuloMRUtils.extractBytes(
	// start,
	// maxDepth));
	// final BigInteger endBI = new BigInteger(
	// AccumuloMRUtils.extractBytes(
	// end,
	// maxDepth));
	// final BigInteger positionBI = new BigInteger(
	// AccumuloMRUtils.extractBytes(
	// position,
	// maxDepth));
	// return (float) (positionBI.subtract(
	// startBI).doubleValue() / endBI.subtract(
	// startBI).doubleValue());
	// }
	//
	// private static float getProgressForRange(
	// final Range range,
	// final Key currentKey ) {
	// if (currentKey == null) {
	// return 0f;
	// }
	// if ((range.getStartKey() != null) && (range.getEndKey() != null)) {
	// if (!range.getStartKey().equals(
	// range.getEndKey(),
	// PartialKey.ROW)) {
	// // just look at the row progress
	// return getProgressForRange(
	// range.getStartKey().getRowData(),
	// range.getEndKey().getRowData(),
	// currentKey.getRowData());
	// }
	// else if (!range.getStartKey().equals(
	// range.getEndKey(),
	// PartialKey.ROW_COLFAM)) {
	// // just look at the column family progress
	// return getProgressForRange(
	// range.getStartKey().getColumnFamilyData(),
	// range.getEndKey().getColumnFamilyData(),
	// currentKey.getColumnFamilyData());
	// }
	// else if (!range.getStartKey().equals(
	// range.getEndKey(),
	// PartialKey.ROW_COLFAM_COLQUAL)) {
	// // just look at the column qualifier progress
	// return getProgressForRange(
	// range.getStartKey().getColumnQualifierData(),
	// range.getEndKey().getColumnQualifierData(),
	// currentKey.getColumnQualifierData());
	// }
	// }
	// // if we can't figure it out, then claim no progress
	// return 0f;
	// }
}