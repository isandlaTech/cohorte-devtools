package org.cohorte.eclipse.runner.basic.jython;

import java.io.IOException;
import java.util.List;

public interface IPythonFactory {
	public void addPythonPath(final String aPythonPath);

	public void addPythonPaths(final List<String> aPythonPath);

	public void clear();

	public Object newInstance(final Class<?> aInterface, final Object... aArgs)
			throws IOException;
}
