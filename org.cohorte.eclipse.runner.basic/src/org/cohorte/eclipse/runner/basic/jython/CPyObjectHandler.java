package org.cohorte.eclipse.runner.basic.jython;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import org.python.core.PyObject;

public class CPyObjectHandler implements InvocationHandler {

	private final CPythonFactory pFactory;
	private final PyObject pPyObject;

	CPyObjectHandler(final PyObject aPyObject, final CPythonFactory aFactory) {
		pPyObject = aPyObject;
		pFactory = aFactory;
	}

	public PyObject getPyObject() {
		return pPyObject;
	}

	@Override
	public Object invoke(final Object proxy, final Method method,
			final Object[] aArgs) throws Throwable {
		if (method.getName().equals("hashCode")) {
			return this.hashCode();
		}
		PyObject[] wArgs = pFactory.convertToPyObject(aArgs);
		PyObject wResult;
		if (wArgs == null || wArgs.length == 0) {
			wResult = pPyObject.invoke(method.getName());
		} else {
			wResult = pPyObject.invoke(method.getName(), wArgs);
		}

		return pFactory.getObject(wResult);
	}
}
