package org.cohorte.eclipse.runner.basic.jython;

/**
 *
 * Object Factory that is used to coerce python module into a
 * Java class
 */

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.python.core.PyBoolean;
import org.python.core.PyFloat;
import org.python.core.PyInteger;
import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.util.PythonInterpreter;

public class CPythonFactory implements IPythonFactory {

	public static PyObject getPyObject(final Object aJavaObj) {
		if (aJavaObj instanceof List<?>) {
			return new PyList((List<?>) aJavaObj);
		} else if (aJavaObj instanceof Integer) {
			return new PyInteger((Integer) aJavaObj);
		} else if (aJavaObj instanceof String) {
			return new PyString((String) aJavaObj);

		} else if (aJavaObj instanceof Float) {
			return new PyFloat((Float) aJavaObj);

		} else if (aJavaObj instanceof Boolean) {
			return new PyBoolean((Boolean) aJavaObj);

		} else if (aJavaObj instanceof PyObject) {
			return (PyObject) aJavaObj;
		} else {
			// to to get an invocation handler
			InvocationHandler wHandler = Proxy.getInvocationHandler(aJavaObj);
			if (wHandler instanceof CPyObjectHandler) {
				return ((CPyObjectHandler) wHandler).getPyObject();
			}
			return null;
		}
	}

	// base directory where to lookup the python module
	private final List<String> pBaseDirs;
	/**
	 * Create a new PythonInterpreter object, then use it to execute some python
	 * code. In this case, we want to import the python module that we will
	 * coerce.
	 *
	 * Once the module is imported than we obtain a reference to it and assign
	 * the reference to a Java variable
	 */

	private final PythonInterpreter pInterpreter;

	private final List<String> pLoadedFile;

	/**
	 * allow to retrieve from a pyObject the proxy
	 */
	private final Map<Integer, Integer> pMapHashCode;

	private final Map<Integer, WeakReference<Object>> pMapProxy;

	public CPythonFactory() {
		this(null, null);
	}

	public CPythonFactory(final List<String> aPythonPath, final String aLib) {
		pBaseDirs = aPythonPath;
		pLoadedFile = new ArrayList<String>();
		Properties wProps = new Properties();
		wProps.put("python.home", aLib);
		wProps.put("python.console.encoding", "UTF-8");
		wProps.put("python.security.respectJavaAccessibility", "false");
		wProps.put("python.import.site", "false");

		PythonInterpreter.initialize(System.getProperties(), wProps,
				new String[0]);
		pInterpreter = new PythonInterpreter();
		pInterpreter.exec("import sys");
		pInterpreter.exec("import logging");
		pInterpreter.exec("logging.basicConfig(level=logging.DEBUG)");

		if (aPythonPath != null) {
			addPythonPath(aPythonPath);
		}
		pMapHashCode = new HashMap<Integer, Integer>();
		pMapProxy = new HashMap<Integer, WeakReference<Object>>();
	}

	@Override
	public void addPythonPath(final List<String> aPythonPath) {
		for (String wPath : aPythonPath) {
			String wAppend = "sys.path.append('" + wPath + "')";
			pInterpreter.exec(wAppend);
		}
	}

	/**
	 * The create method is responsible for performing the actual coercion of
	 * the referenced python module into Java bytecode
	 *
	 * @throws IOException
	 */

	public PyObject classForName(final String aModuleName,
			final String aClassName, final Object... aArguments)
			throws IOException {
		// import module
		String wFoundFullPath = null;
		// check existance of the file
		for (int i = 0; i < pBaseDirs.size() && wFoundFullPath == null; i++) {
			String wBaseDir = pBaseDirs.get(i);
			String path = wBaseDir + File.separatorChar + aModuleName;
			if (new File(path).exists()) {
				wFoundFullPath = path;
			}
		}
		if (wFoundFullPath != null) {
			if (!pLoadedFile.contains(wFoundFullPath)) {

				pInterpreter.execfile(wFoundFullPath);
				pLoadedFile.add(wFoundFullPath);
			}
			// construct the argument
			return pInterpreter.get(aClassName);
		} else {
			throw new IOException(String.format("can't find module [%s]",
					aModuleName));
		}

	}

	@Override
	public void clear() {
		pMapProxy.clear();
		pMapHashCode.clear();
		pLoadedFile.clear();
		pInterpreter.cleanup();
		pInterpreter.close();
	}

	public PyObject[] convertToPyObject(final Object[] aObjects) {
		if (aObjects != null && aObjects.length > 0) {
			List<PyObject> wListArg = new ArrayList<PyObject>();
			for (Object wArg : aObjects) {
				wListArg.add(getPyObject(wArg));
			}
			// get the class
			PyObject[] wPyObjectArr = new PyObject[wListArg.size()];
			wListArg.toArray(wPyObjectArr);
			return wPyObjectArr;
		}
		return null;
	}

	public Object getObject(final PyObject aPyObject) {
		if (aPyObject instanceof PyInteger) {
			return ((PyInteger) aPyObject).getValue();
		} else if (aPyObject instanceof PyString) {
			return ((PyString) aPyObject).toString();

		} else if (aPyObject instanceof PyFloat) {
			return ((PyFloat) aPyObject).getValue();

		} else if (aPyObject instanceof PyBoolean) {
			return ((PyBoolean) aPyObject).getBooleanValue();

		} else {
			if (pMapHashCode.get(aPyObject.hashCode()) != null) {
				WeakReference<Object> wRef = pMapProxy.get(pMapHashCode
						.get(aPyObject.hashCode()));
				return wRef.get();
			}
			// not supported
			return aPyObject;
		}
	}

	@Override
	public Object newInstance(final Class<?> aInterface, final Object... aArgs)
			throws IOException {
		PythonClass wAnn = aInterface.getAnnotation(PythonClass.class);
		if (wAnn != null) {
			String wmodulePath = wAnn.modulepath().replaceAll("\\.", "/")
					+ ".py";
			String className = wAnn.classname();
			PyObject wPyClass = classForName(wmodulePath, className);
			PyObject wPyObject = newInstance(wPyClass, aArgs);
			Object wInstance = Proxy.newProxyInstance(
					aInterface.getClassLoader(), new Class[] { aInterface },
					new CPyObjectHandler(wPyObject, this));
			// assign the hasCode betwwen proxy and pyObject
			pMapHashCode.put(wPyObject.hashCode(), wInstance.hashCode());
			pMapProxy.put(wInstance.hashCode(), new WeakReference<Object>(
					wInstance));
			return wInstance;
		}
		return null;
	}

	public PyObject newInstance(final PyObject aPythonClass,
			final Object... aArguments) {

		PyObject wPythonObject;
		PyObject[] wPyObjectArr = convertToPyObject(aArguments);
		if (wPyObjectArr != null && wPyObjectArr.length > 0) {
			wPythonObject = aPythonClass.__call__(wPyObjectArr);
		} else {
			wPythonObject = aPythonClass.__call__();
		}

		return wPythonObject;
	}
}