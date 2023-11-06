
package com.zero.helper.beanInvoker;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;

import com.zero.helper.GU;
import com.zero.helper.beanInvoker.AnnotationDescMaker.AnnotationWrapper;

import lombok.extern.slf4j.Slf4j;

/**
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Oct 12, 2016
 * @Desc this guy is too lazy, nothing left.
 */
@Slf4j
public class InvokerCreateor implements Opcodes {

	@SuppressWarnings("unchecked")
	public static <T> Invoker<T> createInvoker(Method method) {
		Class<?> hostClass = method.getDeclaringClass();
		String className = getInvokerClassName(hostClass, method);
		byte[] generatorClass = generatorClass(className, method.getDeclaringClass(), method);
		Class<? extends Invoker<T>> invokerClass = (Class<? extends Invoker<T>>) ByteSourceClassLoader
				.loadClass(InvokerCreateor.class.getClassLoader(), className, generatorClass);
		try {
			return invokerClass.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static <T> InstanceInvoker<T> createInstanceInvoker(Class<?> hostClass) {
		String className = hostClass.getName() + "$Instance";
		byte[] generatorClass = generatorInstanceClass(className, hostClass);
		Class<? extends InstanceInvoker<T>> invokerClass = (Class<? extends InstanceInvoker<T>>) ByteSourceClassLoader
				.loadClass(InvokerCreateor.class.getClassLoader(), className, generatorClass);
		try {
			return invokerClass.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * @param hostClass
	 * @param method
	 * @return
	 */
	private static String getInvokerClassName(Class<?> hostClass, Method method) {
		return hostClass.getName() + "$" + method.getName() + "$"
				+ Math.abs(Type.getMethodDescriptor(method).hashCode());
	}

	public static byte[] generatorClass(Class<?> hostClass, Method method) {
		return generatorClass(getInvokerClassName(hostClass, method), hostClass, method);
	}

	@SuppressWarnings("deprecation")
	public static byte[] generatorInstanceClass(String invokerClassName, Class<?> hostClass) {
		String hostClassDesc = Type.getDescriptor(hostClass);
		String hostClassInnerName = Type.getInternalName(hostClass);
		String className = (GU.isNullOrEmpty(invokerClassName) ? hostClass.getName() + "$Instance"
				: invokerClassName);
		className = className.replace(".", "/");

		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		CheckClassAdapter cca = new CheckClassAdapter(cw);
		TraceClassVisitor cv = new TraceClassVisitor(cca, initTempOutputWriter());

		cv.visit(V1_7, ACC_PUBLIC + ACC_SUPER, className,
				AnnotationDescMaker.makeAnnotatinDesc(
						new AnnotationWrapper(Type.getDescriptor(InstanceInvoker.class), hostClassDesc)),
				Type.getInternalName(Object.class), new String[] { Type.getInternalName(InstanceInvoker.class) });

		MethodVisitor mv;

		{
			mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
			mv.visitCode();
			Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
			mv.visitInsn(RETURN);
			Label l1 = new Label();
			mv.visitLabel(l1);
			mv.visitLocalVariable("this", "L" + className + ";", null, l0, l1, 0);
			mv.visitMaxs(1, 1);
			mv.visitEnd();
		}

		{
			mv = cw.visitMethod(ACC_PUBLIC, "instance", "()" + hostClassDesc, null, null);
			mv.visitCode();
			Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitTypeInsn(NEW, hostClassInnerName);
			mv.visitInsn(DUP);
			mv.visitMethodInsn(INVOKESPECIAL, hostClassInnerName, "<init>", "()V");
			mv.visitInsn(ARETURN);
			Label l1 = new Label();
			mv.visitLabel(l1);
			mv.visitLocalVariable("this", "L" + className + ";", null, l0, l1, 0);
			mv.visitMaxs(2, 1);
			mv.visitEnd();
		}

		{
			mv = cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC, "instance", "()Ljava/lang/Object;", null,
					null);
			mv.visitCode();
			Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitLineNumber(1, l0);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKEVIRTUAL, className, "instance", "()" + hostClassDesc);
			mv.visitInsn(ARETURN);
			mv.visitMaxs(1, 1);
			mv.visitEnd();
		}
		cv.visitEnd();
		return cw.toByteArray();
	}

	@SuppressWarnings("deprecation")
	public static byte[] generatorClass(String invokerClassName, Class<?> hostClass, Method method) {
		String hostClassDesc = Type.getDescriptor(hostClass);
		String hostClassInnerName = Type.getInternalName(hostClass);
		String className = (GU.isNullOrEmpty(invokerClassName)
				? hostClass.getName() + "$" + method.getName() + "$"
						+ Math.abs(Type.getMethodDescriptor(method).hashCode())
				: invokerClassName);
		className = className.replace(".", "/");

		Class<?> returnType = method.getReturnType();
		log.debug("retType : {} " + returnType.getName());
		boolean primitive = returnType.isPrimitive();
		boolean hasRet = !(returnType == void.class);
		log.debug("has ret : " + hasRet);

		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		CheckClassAdapter cca = new CheckClassAdapter(cw);
		TraceClassVisitor cv = new TraceClassVisitor(cca, initTempOutputWriter());
		MethodVisitor mv;

		cv.visit(V1_7, ACC_PUBLIC + ACC_SUPER, className,
				AnnotationDescMaker
						.makeAnnotatinDesc(new AnnotationWrapper(Type.getDescriptor(Invoker.class), hostClassDesc)),
				Type.getInternalName(Object.class), new String[] { Type.getInternalName(Invoker.class) });

		{
			mv = cv.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
			mv.visitCode();
			Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
			mv.visitInsn(RETURN);
			Label l1 = new Label();
			mv.visitLabel(l1);
			mv.visitLocalVariable("this", "L" + className + ";", null, l0, l1, 0);
			mv.visitMaxs(1, 1);
			mv.visitEnd();
		}

		{
			mv = cv.visitMethod(ACC_PUBLIC + ACC_VARARGS, "invoke",
					"(" + hostClassDesc + "[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
			mv.visitCode();
			if (hasRet) {
				log.debug("has ret ");
				visitWithHasRet(mv, method.getParameterTypes(), method, className, hostClassDesc, hostClassInnerName,
						returnType, primitive);
			} else {
				log.debug("has no ret");
				visitWithNotRet(mv, method.getParameterTypes(), method, className, hostClassDesc, hostClassInnerName,
						returnType, primitive);
			}
		}

		{
			mv = cv.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_VARARGS + ACC_SYNTHETIC, "invoke",
					"(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
			mv.visitCode();
			Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitTypeInsn(CHECKCAST, hostClassInnerName);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitMethodInsn(INVOKEVIRTUAL, className, "invoke",
					"(" + hostClassDesc + "[Ljava/lang/Object;)Ljava/lang/Object;");
			mv.visitInsn(ARETURN);
			mv.visitMaxs(3, 3);
			mv.visitEnd();
		}
		cv.visitEnd();
		return cw.toByteArray();
	}

	private static PrintWriter initTempOutputWriter() {
		PrintWriter filePw = null;
		try {
			filePw = new PrintWriter(new File(FileUtils.getTempDirectory(), UUID.randomUUID().toString() + ".out"));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return filePw;
	}

	/**
	 * @param mv
	 * @param method
	 * @param arguSize
	 * @param className
	 * @param hostClassDesc
	 * @param hostClassInnerName
	 * @param returnType
	 * @param primitive
	 */
	@SuppressWarnings("deprecation")
	private static void visitWithNotRet(MethodVisitor mv, Class<?>[] argumentTypes, Method method, String className,
			String hostClassDesc, String hostClassInnerName, Class<?> returnType, boolean primitive) {
		boolean hasArguments = argumentTypes.length > 0;
		int oldBaseIndex = 2;
		int baseIndex = 2;
		if (hasArguments) {
			int i = 0;
			for (Class<?> arguType : argumentTypes) {
				log.debug("arguType:" + arguType.getName() + "i : " + i);
				Label label = new Label();
				String innnerName = Type.getInternalName(getWrapperType(arguType));
				mv.visitLabel(label);
				mv.visitVarInsn(ALOAD, 2);
				log.debug("i:" + i);
				if (i < 6) {
					mv.visitInsn(getICONST(i));
				} else {
					mv.visitIntInsn(BIPUSH, i);
				}
				mv.visitInsn(AALOAD);
				mv.visitTypeInsn(CHECKCAST, innnerName);
				mv.visitVarInsn(ASTORE, ++baseIndex);
				i++;
			}
		}
		log.debug("baseIndex : " + baseIndex);

		Label l0 = new Label();
		mv.visitLabel(l0);
		mv.visitVarInsn(ALOAD, 1);
		int i = oldBaseIndex + 1;
		if (hasArguments) {
			for (; i <= baseIndex; i++) {
				mv.visitVarInsn(ALOAD, i);
				if (argumentTypes[i - oldBaseIndex - 1].isPrimitive()) {
					wrapperChange(mv, getWrapperType(argumentTypes[i - oldBaseIndex - 1]));
				}
			}
		}
		log.debug("==========");
		mv.visitMethodInsn(INVOKEVIRTUAL, hostClassInnerName, method.getName(), Type.getMethodDescriptor(method));
		Label l1 = new Label();
		mv.visitLabel(l1);
		mv.visitInsn(ACONST_NULL);
		mv.visitInsn(ARETURN);
		Label l2 = new Label();
		mv.visitLabel(l2);
		if (hasArguments) {
			log.debug("maxStack: " + (1 + argumentTypes.length) + " , maxLocal" + (3 + argumentTypes.length));
			mv.visitMaxs(1 + argumentTypes.length, 3 + argumentTypes.length);
		} else {
			mv.visitMaxs(1, 3);
		}
		mv.visitEnd();

	}

	/**
	 * @param mv
	 * @param className
	 * @param hostClassDesc
	 * @param hostClassInnerName
	 * @param returnType
	 * @param primitive
	 */

	@SuppressWarnings("deprecation")
	private static void visitWithHasRet(MethodVisitor mv, Class<?>[] argumentTypes, Method method, String className,
			String hostClassDesc, String hostClassInnerName, Class<?> returnType, boolean primitive) {
		boolean hasArguments = argumentTypes.length > 0;
		int oldBaseIndex = 2;
		int baseIndex = 2;
		log.debug("arguType are : " + argumentTypes.length);
		if (hasArguments) {
			int i = 0;
			for (Class<?> arguType : argumentTypes) {
				log.debug("arguType:" + arguType.getName() + "i : " + i);
				Label label = new Label();
				String innnerName = Type.getInternalName(getWrapperType(arguType));
				mv.visitLabel(label);
				mv.visitVarInsn(ALOAD, 2);
				if (i < 6) {
					mv.visitInsn(getICONST(i));
				} else {
					mv.visitIntInsn(BIPUSH, i);
				}
				mv.visitInsn(AALOAD);
				mv.visitTypeInsn(CHECKCAST, innnerName);
				mv.visitVarInsn(ASTORE, ++baseIndex);
				i++;
			}
		}
		log.debug("baseIndex is : " + baseIndex);
		Label l0 = new Label();
		mv.visitLabel(l0);
		mv.visitVarInsn(ALOAD, 1);
		int i = oldBaseIndex + 1;
		if (hasArguments) {
			for (; i <= baseIndex; i++) {
				mv.visitVarInsn(ALOAD, i);
				if (argumentTypes[i - oldBaseIndex - 1].isPrimitive()) {
					wrapperChange(mv, getWrapperType(argumentTypes[i - oldBaseIndex - 1]));
				}
			}
		}
		mv.visitMethodInsn(INVOKEVIRTUAL, hostClassInnerName, method.getName(), Type.getMethodDescriptor(method));
		if (primitive) {
			primitiveChange(mv, returnType);
		}
		mv.visitVarInsn(ASTORE, i);
		Label l1 = new Label();
		mv.visitLabel(l1);
		mv.visitVarInsn(ALOAD, i);
		mv.visitInsn(ARETURN);
		Label l2 = new Label();
		mv.visitLabel(l2);
		if (hasArguments) {
			mv.visitMaxs(1 + argumentTypes.length, 4 + argumentTypes.length);
		} else {
			mv.visitMaxs(1, 4);
		}
		mv.visitEnd();
	}

	/**
	 * @param i
	 * @return
	 */
	private static int getICONST(int i) {
		switch (i) {
		case 0:
			return ICONST_0;
		case 1:
			return ICONST_1;
		case 2:
			return ICONST_2;
		case 3:
			return ICONST_3;
		case 4:
			return ICONST_4;
		case 5:
			return ICONST_5;
		default:
			break;
		}
		return 0;
	}

	@SuppressWarnings("deprecation")
	public static void wrapperChange(MethodVisitor mv, Class<?> wrapperType) {
		Method primitiveMethod = getPrimitiveMethod(getWrapperType(wrapperType));
		mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(wrapperType), primitiveMethod.getName(),
				Type.getMethodDescriptor(primitiveMethod));
	}

	/**
	 * @param wrapperType
	 * @return
	 */
	private static Method getPrimitiveMethod(Class<?> wrapperType) {
		String methodName = "";
		Class<?>[] emptyParamterTypes = new Class<?>[] {};
		if (wrapperType == Byte.class) {
			methodName = "byteValue";
		} else if (wrapperType == Boolean.class) {
			methodName = "booleanValue";
		} else if (wrapperType == Character.class) {
			methodName = "charValue";
		} else if (wrapperType == Short.class) {
			methodName = "shortValue";
		} else if (wrapperType == Integer.class) {
			methodName = "intValue";
		} else if (wrapperType == Float.class) {
			methodName = "floatValue";
		} else if (wrapperType == Long.class) {
			methodName = "longValue";
		} else if (wrapperType == Double.class) {
			methodName = "doubleValue";
		}
		log.debug("wrapperType : " + wrapperType + " and methodName is : " + methodName);
		try {
			return wrapperType.getMethod(methodName, emptyParamterTypes);
		} catch (NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
		}
		return null;
	}

	@SuppressWarnings("deprecation")
	public static void primitiveChange(MethodVisitor mv, Class<?> primitiveType) {
		Method wrapperTypeMethod = getWrapperMethod(primitiveType);
		Class<?> wrapperClass = wrapperTypeMethod.getDeclaringClass();
		log.debug(Type.getInternalName(wrapperClass) + "----" + Type.getMethodDescriptor(wrapperTypeMethod));
		mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(wrapperClass), "valueOf",
				Type.getMethodDescriptor(wrapperTypeMethod));
	}

	/**
	 * @param primitiveType
	 * @return
	 */
	private static Method getWrapperMethod(Class<?> primitiveType) {
		Class<?> wrapperType = getWrapperType(primitiveType);
		try {
			return wrapperType.getMethod("valueOf", primitiveType);
		} catch (NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
			return null;
		}
	}

	private static Class<?> getWrapperType(Class<?> primitiveType) {
		if (primitiveType == int.class) {
			return Integer.class;
		} else if (primitiveType == boolean.class) {
			return Boolean.class;
		} else if (primitiveType == byte.class) {
			return Byte.class;
		} else if (primitiveType == float.class) {
			return Float.class;
		} else if (primitiveType == short.class) {
			return Short.class;
		} else if (primitiveType == long.class) {
			return Long.class;
		} else if (primitiveType == double.class) {
			return Double.class;
		} else if (primitiveType == char.class) {
			return Character.class;
		}
		return primitiveType;
	}

	public static int getLoadOpcode(Class<?> primitiveType) {
		if (primitiveType == int.class || primitiveType == boolean.class || primitiveType == short.class
				|| primitiveType == char.class || primitiveType == byte.class) {
			return Opcodes.ILOAD;
		} else if (primitiveType == long.class) {
			return Opcodes.LLOAD;
		} else if (primitiveType == float.class) {
			return Opcodes.FLOAD;
		} else if (primitiveType == double.class) {
			return Opcodes.DLOAD;
		} else {
			return Opcodes.ALOAD;
		}
	}

}
