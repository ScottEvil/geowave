package mil.nga.giat.geowave.core.cli;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ServiceLoader;

import javax.swing.text.html.Option;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.log4j.Logger;

/**
 * This class encapsulates the option for selecting the operation and parses the
 * value.
 */
public class OperationCommandLineOptions
{
	private static class CategoryKey
	{
		private final String key;
		private final CLIOperationCategory category;

		public CategoryKey(
				final CLIOperationCategory category ) {
			this.category = category;
			key = category.getCategoryId();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = (prime * result) + ((key == null) ? 0 : key.hashCode());
			return result;
		}

		@Override
		public boolean equals(
				final Object obj ) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final CategoryKey other = (CategoryKey) obj;
			if (key == null) {
				if (other.key != null) {
					return false;
				}
			}
			else if (!key.equals(other.key)) {
				return false;
			}
			return true;
		}

	}

	private final static Logger LOGGER = Logger.getLogger(OperationCommandLineOptions.class);

	private final CLIOperation operation;

	public OperationCommandLineOptions(
			final CLIOperation operation ) {
		this.operation = operation;
	}

	public CLIOperation getOperation() {
		return operation;
	}

	public static OperationCommandLineOptions parseOptions(
			final CommandLine commandLine )
			throws IllegalArgumentException {
		CLIOperation operation = null;
		final Map<CategoryKey, CLIOperation[]> internalOperationsRegistry = getRegisteredOperations();
		for (final CLIOperation[] operations : internalOperationsRegistry.values()) {
			for (final CLIOperation o : operations) {
				if (commandLine.hasOption(o.getCommandlineOptionValue())) {
					operation = o;
					break;
				}
			}
		}
		if (operation == null) {
			final StringBuffer str = new StringBuffer();
			for (final Entry<CategoryKey, CLIOperation[]> entry : internalOperationsRegistry.entrySet()) {
				final CLIOperation[] operations = entry.getValue();
				for (int i = 0; i < operations.length; i++) {
					final CLIOperation o = operations[i];
					str.append(
							"'").append(
							o.getCommandlineOptionValue()).append(
							"'");
					if (i != (operations.length - 1)) {
						str.append(", ");
						if (i == (operations.length - 2)) {
							str.append("and ");
						}
					}
				}
			}
			LOGGER.fatal("Operation not set.  One of " + str.toString() + " must be provided");
			throw new IllegalArgumentException(
					"Operation not set.  One of " + str.toString() + " must be provided");
		}
		return new OperationCommandLineOptions(
				operation);
	}

	public static void setOptions() {
		final List<CLIOperation> internalOperationsRegistry = getRegisteredOperations();
		for (final CLIOperation[] operations : internalOperationsRegistry.values()) {
			for (final CLIOperation o : operations) {
				operationChoice.addOption(new Option(
						o.getCommandlineOptionValue(),
						o.getDescription()));
			}
		}
		allOptions.addOptionGroup(operationChoice);
	}
}
