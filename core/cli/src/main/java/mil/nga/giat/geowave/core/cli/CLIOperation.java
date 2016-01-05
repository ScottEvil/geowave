package mil.nga.giat.geowave.core.cli;

import com.beust.jcommander.JCommander;

public interface CLIOperation
{
	public boolean doOperation(
			JCommander commander );

	public void init(
			JCommander commander );

	public String getOperationName();
}
