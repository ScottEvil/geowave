package mil.nga.giat.geowave.core.cli;

import com.beust.jcommander.JCommander;

public interface CLIOperation extends
		CommandObject
{
	public boolean doOperation(
			JCommander commander );
}
