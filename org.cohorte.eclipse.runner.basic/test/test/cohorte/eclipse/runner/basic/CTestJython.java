package test.cohorte.eclipse.runner.basic;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import junit.framework.TestCase;

import org.cohorte.eclipse.runner.basic.jython.CPythonFactory;
import org.cohorte.eclipse.runner.basic.jython.IFileFinder;
import org.cohorte.eclipse.runner.basic.jython.IFileIncluder;
import org.junit.Test;
import org.python.core.PyObject;
import org.python.core.PyString;

public class CTestJython extends TestCase {

	private static CPythonFactory sFactory;

	private final String sPythonLocation = System.getProperty("user.dir")
			+ File.separatorChar + "files" + File.separatorChar + "test";
	private final String sStdLib = System.getProperty("user.dir")
			+ File.separatorChar + "files" + File.separatorChar + "jython";

	@Override
	public void setUp() throws Exception {
		super.setUp();
		sFactory = new CPythonFactory(
				Arrays.asList(new String[] { sPythonLocation }), sStdLib);

	}

	@Test
	public void testCreateCohortePyton() {
		try {
			IFileFinder wFinder = (IFileFinder) sFactory
					.newInstance(IFileFinder.class);
			// set cohorte base and data to the finder
			wFinder._set_roots(Arrays.asList(new String[] { sPythonLocation,
					sPythonLocation }));

			IFileIncluder wIncluder = (IFileIncluder) sFactory
					.newInstance(IFileIncluder.class);
			wIncluder.set_finder(wFinder);
			System.out.println(wIncluder.get_content("conf/boot-forker.js",
					false));

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Test
	public void testCreateJavaObject() {
		IPythonClass wObject;
		try {
			wObject = (IPythonClass) sFactory.newInstance(IPythonClass.class);

			assertEquals(wObject.method("test"), "test");

			IPythonClassWithParam wObject2 = (IPythonClassWithParam) sFactory
					.newInstance(IPythonClassWithParam.class, "test");

			assertEquals(wObject2.method(), "test");

			IPythonClassWithObject wObject3 = (IPythonClassWithObject) sFactory
					.newInstance(IPythonClassWithObject.class, "test");
			wObject3.set(wObject2);
			assertEquals(wObject3.get().method(), "test");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Test
	public void testCreatePythonObject() {
		try {
			String wModuleName = "pyclass.py";

			// create object without parameter
			PyObject wClass = sFactory.classForName(wModuleName, "TestClass");
			PyObject wObj = sFactory.newInstance(wClass);

			// invoke method
			PyObject wResult = wObj.invoke("method", new PyString("test"));
			assertEquals(wResult.toString(), "test");

			// create object with parameter
			wClass = sFactory.classForName(wModuleName, "TestClassWithParam");
			wObj = sFactory.newInstance(wClass, "test");
			wResult = wObj.invoke("method");
			assertEquals(wResult.toString(), "test");
			/*
			 * invoke static method wResult = wClass.invoke("static", new
			 * PyString("test")); assertEquals(wResult.toString(), "test");
			 */
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
