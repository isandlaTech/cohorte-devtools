package org.cohorte.eclipse.runner.basic.jython;

import java.util.List;

public interface IPythonBridge {

	public IPythonFactory getPythonObjectFactory(String aId);

	public IPythonFactory getPythonObjectFactory(final String aId,
			final List<String> aPythonPath);

	public void remove(String aId);
}
