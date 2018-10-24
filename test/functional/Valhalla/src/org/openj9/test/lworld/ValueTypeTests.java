/*******************************************************************************
 * Copyright (c) 2018, 2018 IBM Corp. and others
 *
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which accompanies this
 * distribution and is available at https://www.eclipse.org/legal/epl-2.0/
 * or the Apache License, Version 2.0 which accompanies this distribution and
 * is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * This Source Code may also be made available under the following
 * Secondary Licenses when the conditions for such availability set
 * forth in the Eclipse Public License, v. 2.0 are satisfied: GNU
 * General Public License, version 2 with the GNU Classpath
 * Exception [1] and GNU General Public License, version 2 with the
 * OpenJDK Assembly Exception [2].
 *
 * [1] https://www.gnu.org/software/classpath/license.html
 * [2] http://openjdk.java.net/legal/assembly-exception.html
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0 OR GPL-2.0 WITH Classpath-exception-2.0 OR LicenseRef-GPL-2.0 WITH Assembly-exception
 *******************************************************************************/
package org.openj9.test.lworld;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

import org.testng.Assert;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/*
 * Instructions to run this test:
 * 
 * 1) recompile the JVM with J9VM_OPT_VALHALLA_VALUE_TYPES flag turned on in j9cfg.h.ftl (or j9cfg.h.in when cmake is turned on)
 * 2) cd [openj9-openjdk-dir]/openj9/test/TestConfig
 * 3) export JAVA_BIN=[openj9-openjdk-dir]/build/linux-x86_64-normal-server-release/images/jdk/bin
 * 4) export PATH=$JAVA_BIN:$PATH
 * 5) export JAVA_VERSION=Valhalla
 * 6) export JDK_VERSION=Valhalla
 * 7) export SPEC=linux_x86-64_cmprssptrs
 * 8) export BUILD_LIST=functional/Valhalla
 * 9) make -f run_configure.mk && make compile && make _sanity
 */

@Test(groups = { "level.sanity" })
public class ValueTypeTests {
	static Lookup lookup = MethodHandles.lookup();
	static Class point2DClass = null;
	static Class line2DClass = null;
	static Class line2DRefClass = null;
	/* Point2D methods */
	static MethodHandle makePoint2D = null;
	static MethodHandle getX = null;
	static MethodHandle getY = null;
	/* Line2DRef methods */
	static MethodHandle makeLine2DRef = null;
	static MethodHandle getStRef = null;
	static MethodHandle getEnRef = null;
	static MethodHandle setStRef = null;
	static MethodHandle setEnRef = null;

	
	/*
	 * Create a value type
	 * 
	 * value Point2D {
	 * 	int x;
	 * 	int y;
	 * }
	 */
	@Test(priority=1)
	static public void testCreatePoint2D() throws Throwable {
		String fields[] = {"x:I", "y:I"};
		Class<?> point2DClass = ValueTypeGenerator.generateValueClass("Point2D", fields);
		
		makePoint2D = lookup.findStatic(point2DClass, "makeValue", MethodType.methodType(point2DClass, int.class, int.class));
		
		getX = generateGetter(point2DClass, "x", int.class);
		MethodHandle setX = generateSetter(point2DClass, "x", int.class);
		getY = generateGetter(point2DClass, "y", int.class);
		MethodHandle setY = generateSetter(point2DClass, "y", int.class);
		
		Object point2D = makePoint2D.invoke(2, 4);
		
		assertEquals(getX.invoke(point2D), 2);
		assertEquals(getY.invoke(point2D), 4);
		setX.invoke(point2D, 1);
		setY.invoke(point2D, 3);
		assertEquals(getX.invoke(point2D), 1);
		assertEquals(getY.invoke(point2D), 3);
	}

	/*
	 * Test with nested values in reference type
	 * 
	 * class Line2D {
	 * 	Point2D st;
	 * 	Point2D en;
	 * }
	 * 
	 */
	@Test(priority=2)
	static public void testCreateLine2DRef() throws Throwable {
		String fields[] = {"st:QPoint2D;:value", "en:QPoint2D;:value"};
		line2DRefClass = ValueTypeGenerator.generateRefClass("Line2DRef", fields);
		/* Setup Line2DRef methods */
		makeLine2DRef = lookup.findStatic(line2DRefClass, "makeRefGeneric", MethodType.methodType(line2DRefClass, Object.class, Object.class));
		getStRef = generateGenericGetter(line2DRefClass, "st");
		getEnRef = generateGenericGetter(line2DRefClass, "en");
		setStRef = generateGenericSetter(line2DRefClass, "st");
		setEnRef = generateGenericSetter(line2DRefClass, "en");

		/* Setup test variables */
		int pointAX = 0xDEFEFEF1;
		int pointAY = 0xEEFEFEF2;
		int pointBX = 0x1EFEFEF4;
		int pointBY = 0x0EFEFEFF;
		Object point2DA = makePoint2D.invoke(pointAX, pointAY);
		Object point2DB = makePoint2D.invoke(pointBX, pointBY);
		Object line2DRef = makeLine2DRef.invoke(point2DA, point2DB);
		Object point2DStOut;
		Object point2DEnOut;

		/* Verify that initialization and getters work */
		point2DStOut = getStRef.invoke(line2DRef);
		point2DEnOut = getEnRef.invoke(line2DRef);
		assertEquals(getX.invoke(point2DStOut), pointAX);
		assertEquals(getY.invoke(point2DStOut), pointAY);
		assertEquals(getX.invoke(point2DEnOut), pointBX);
		assertEquals(getY.invoke(point2DEnOut), pointBY);

		/* Verify that setters work */
		Object test = point2DB;
		setStRef.invoke(line2DRef, point2DB);
		setEnRef.invoke(line2DRef, point2DA);
		point2DStOut = getStRef.invoke(line2DRef);
		point2DEnOut = getEnRef.invoke(line2DRef);
		assertEquals(point2DStOut, point2DB);
		assertEquals(point2DEnOut, point2DA);

		//TODO need q signature support to do anything else with Line2D
	}

	/*
	 * Test with nested values
	 * 
	 * class InvalidField {
	 * 	Point2D st;
	 * 	Invalid x;
	 * }
	 * 
	 */
	@Test(priority=3)
	static public void testInvalidNestedField() throws Throwable {
		String fields[] = {"x:QPoint2D;:value", "st:QInvalid;:value"};

		try {
			Class<?> invalidField = ValueTypeGenerator.generateValueClass("InvalidField", fields);
			Assert.fail("should throw error. Nested class doesn't exist!");
		} catch (NoClassDefFoundError e) {}
	}
	
	/*
	 * Test with none value Qtype
	 * 
	 * class NoneValueQType {
	 * 	Point2D st;
	 * 	Object o;
	 * }
	 * 
	 */
	@Test(priority=3)
	static public void testNoneValueQTypeAsNestedField() throws Throwable {
		String fields[] = {"unsafe:Qjava/lang/Object;:value", "st:QPoint2D;:value"};
		try {
			Class<?> noneValueQType = ValueTypeGenerator.generateValueClass("NoneValueQType", fields);
			Assert.fail("should throw error. j.l.Object is not a qtype!");
		} catch (IncompatibleClassChangeError e) {}
	}
	
	/*
	 * Test setting QType to null
	 */
	/* 
	 * TODO Once bytecode support to prevent nullable Q Types is added,
	 * this should be uncommented.
	 * The catch block exception types and error messages should also be
	 * updated to match the correct exceptions
	 */
	/* 
	 * @Test(priority=3)
	 * static public void testSettingQTypeToNull() throws Throwable {
	 * 	Object point = makePoint2D.invoke(0xFEFEFEFE, 0x1E1E1E1E);
	 * 	try {
	 * 		Object line2DRef = makeLine2DRef.invoke(point, null);
	 * 		Assert.fail("Should throw error. Can't initialize QType to null!");
	 * 	} catch (Exception e) {} // TODO update Exception to be correct type, once bytecode support added
	 * 	try {
	 * 		Object line2DRef = makeLine2DRef.invoke(point, point);
	 * 		setStRef.invoke(line2DRef, null);
	 * 		Assert.fail("Should throw error. Can't set QType to null!");
	 * 	} catch (Exception e) {} // TODO update Exception to be correct type, once bytecode support added
	 * }
	 */

	static MethodHandle generateGetter(Class<?> clazz, String fieldName, Class<?> fieldType) {
		try {
			return lookup.findVirtual(clazz, "get"+fieldName, MethodType.methodType(fieldType));
		} catch (IllegalAccessException | SecurityException | NullPointerException | NoSuchMethodException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	static MethodHandle generateGenericGetter(Class<?> clazz, String fieldName) {
		try {
			return lookup.findVirtual(clazz, "getGeneric"+fieldName, MethodType.methodType(Object.class));
		} catch (IllegalAccessException | SecurityException | NullPointerException | NoSuchMethodException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	static MethodHandle generateSetter(Class clazz, String fieldName, Class fieldType) {
		try {
			return lookup.findVirtual(clazz, "set"+fieldName, MethodType.methodType(void.class, fieldType));
		} catch (IllegalAccessException | SecurityException | NullPointerException | NoSuchMethodException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	static MethodHandle generateGenericSetter(Class clazz, String fieldName) {
		try {
			return lookup.findVirtual(clazz, "setGeneric"+fieldName, MethodType.methodType(void.class, Object.class));
		} catch (IllegalAccessException | SecurityException | NullPointerException | NoSuchMethodException e) {
			e.printStackTrace();
		}
		return null;
	}


	// private static void checkValues(Object o) {
	// 	com.ibm.jvm.Dump.SystemDump();
	// }
	// private static void checkValues(Object o, Object o2) {
	// 	com.ibm.jvm.Dump.SystemDump();
	// }
	// private static void checkValues(Object o, Object o2, Object o3) {
	// 	com.ibm.jvm.Dump.SystemDump();
	// }
	// private static void checkValues(Object o, Object o2, Object o3, Object o4) {
	// 	com.ibm.jvm.Dump.SystemDump();
	// }
	// private static void checkValues(Object o, Object o2, Object o3, Object o4, Object o5) {
	// 	com.ibm.jvm.Dump.SystemDump();
	// }
	// private static void checkValues(Object o, Object o2, Object o3, Object o4, Object o5, Object o6) {
	// 	com.ibm.jvm.Dump.SystemDump();
	// }
	// private static void checkValues(Object o, Object o2, Object o3, Object o4, Object o5, Object o6, Object o7) {
	// 	com.ibm.jvm.Dump.SystemDump();
	// }
	// private static void checkValues(Object o, Object o2, Object o3, Object o4, Object o5, Object o6, Object o7, Object o8) {
	// 	com.ibm.jvm.Dump.SystemDump();
	// }
}
