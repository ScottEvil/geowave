package mil.nga.giat.geowave.core.cli;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ServiceLoader;

import mil.nga.giat.geowave.core.index.StringUtils;
import mil.nga.giat.geowave.core.store.DataStoreFactorySpi;
import mil.nga.giat.geowave.core.store.GeoWaveStoreFinder;

import org.apache.commons.cli.HelpFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

/**
 * This is the primary entry point for command line tools. When run it will
 * expect an operation is specified, and will use the appropriate command-line
 * driver for the chosen operation.
 *
 */
public class GeoWaveMain
{
	private final static Logger LOGGER = LoggerFactory.getLogger(GeoWaveMain.class);

	public static void main(
			final String[] args ) {
		System.exit(run(args));
	}

	public static int run(
			final String[] args ) {
		final GeneralGeoWaveOptions opts = new GeneralGeoWaveOptions();
		final JCommander commander = new JCommander(
				opts);
		final List<CLIOperation> operations = getRegisteredOperations();
		final Map<String, CLIOperation> operationMap = new HashMap<String, CLIOperation>();
		for (final CLIOperation op : operations) {
			final String opName = op.getOperationName();
			try {
				addCommand(
						commander,
						opName,
						op);
			}
			catch (final ParameterException pe) {
				LOGGER.error(
						"Multiple GeoWave CLI Operations cannot be registered with the same name",
						pe);
				commander.usage();
				return -1;
			}
			operationMap.put(
					opName,
					op);
		}
		commander.setAcceptUnknownOptions(true);
		commander.parse(args);
		boolean help = false;
		if (opts.isHelp()) {
			commander.usage();
			help = true;
		}
		if (opts.isListDataStores()) {
			listDataStores();
			help = true;
		}
		if (!help) {
			final String parsedCommand = commander.getParsedCommand();
			CLIOperation selectedOp = null;

			if (parsedCommand != null) {
				selectedOp = operationMap.get(parsedCommand);
			}
			if (selectedOp == null) {
				JCommander.getConsole().println(
						"Unable to parse command");
				commander.usage();
				return -1;
			}
			if (!selectedOp.doOperation(commander)) {
				return -1;
			}
		}
		return 0;
	}

	private static void addCommand(
			final JCommander parentCommand,
			final String commandName,
			final CLIOperation operation ) {
		parentCommand.addCommand(
				commandName,
				operation);
		final JCommander commander = parentCommand.getCommands().get(
				commandName);
		operation.init(commander);
	}

	private static void listDataStores() {
		final HelpFormatter formatter = new HelpFormatter();
		final PrintWriter pw = new PrintWriter(
				new OutputStreamWriter(
						System.out,
						StringUtils.UTF8_CHAR_SET));

		pw.println("Available datastores currently registered:\n");
		final Map<String, DataStoreFactorySpi> dataStoreFactories = GeoWaveStoreFinder.getRegisteredDataStoreFactories();
		for (final Entry<String, DataStoreFactorySpi> dataStoreFactoryEntry : dataStoreFactories.entrySet()) {
			final DataStoreFactorySpi dataStoreFactory = dataStoreFactoryEntry.getValue();
			final String desc = dataStoreFactory.getDescription() == null ? "no description" : dataStoreFactory.getDescription();
			final String text = dataStoreFactory.getName() + ":\n" + desc;

			formatter.printWrapped(
					pw,
					formatter.getWidth(),
					5,
					text);
			pw.println();
		}
		pw.flush();
	}

	private static List<CLIOperation> operationRegistry = null;

	private static synchronized List<CLIOperation> getRegisteredOperations() {
		if (operationRegistry == null) {
			operationRegistry = new ArrayList<CLIOperation>();
			final Iterator<CLIOperationProviderSpi> operationProviders = ServiceLoader.load(
					CLIOperationProviderSpi.class).iterator();
			while (operationProviders.hasNext()) {
				final CLIOperationProviderSpi operationProvider = operationProviders.next();
				if (operationProvider != null) {
					final CLIOperation[] operations = operationProvider.createOperations();
					if ((operations != null) && (operations.length > 0)) {
						for (final CLIOperation op : operations) {
							operationRegistry.add(op);
						}
					}
				}
			}
		}
		return operationRegistry;
	}
}
