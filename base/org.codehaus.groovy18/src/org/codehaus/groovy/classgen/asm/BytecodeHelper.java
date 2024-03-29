/*
 * Copyright 2003-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.classgen.asm;

import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.reflection.ReflectionCache;
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * A helper class for bytecode generation with AsmClassGenerator.
 *
 * @author <a href="mailto:james@coredevelopers.net">James Strachan</a>
 * @author <a href="mailto:b55r@sina.com">Bing Ran</a>
 * @author <a href="mailto:blackdrag@gmx.org">Jochen Theodorou</a>
 * @version $Revision$
 */
public class BytecodeHelper implements Opcodes {

    private static String DTT_CLASSNAME = BytecodeHelper.getClassInternalName(DefaultTypeTransformation.class.getName());

    public static String getClassInternalName(ClassNode t) {
        if (t.isPrimaryClassNode()) {
            return getClassInternalName(t.getName());
        }
        // GRECLIPSE: start
        /*old{
        return getClassInternalName(t.getTypeClass());
        }*/
		// newcode
		// don't call getTypeClass() unless necessary
        // GRECLIPSE decide if this can ever get into trouble?  the second part of the if was added because of FindInSource.groovy which
        // refered to GroovyModel but that could not be found so we were left with an unresolved import and node in the code - crashed
        // whilst doing the code gen
        String name = t.getClassInternalName();
        if (name==null) {
            if (t.hasClass()) {
            	name = getClassInternalName(t.getTypeClass());
            } else {
            	name = getClassInternalName(t.getName());
            }
        }
        return name;
		// end
		
    }

    public static String getClassInternalName(Class t) {
        return org.objectweb.asm.Type.getInternalName(t);
    }

    /**
     * @return the ASM internal name of the type
     */
    public static String getClassInternalName(String name) {
        return name.replace('.', '/');
    }

    public static String getMethodDescriptor(ClassNode returnType, Parameter[] parameters) {
        StringBuffer buffer = new StringBuffer("(");
        for (int i = 0; i < parameters.length; i++) {
            buffer.append(getTypeDescription(parameters[i].getType()));
        }
        buffer.append(")");
        buffer.append(getTypeDescription(returnType));
        return buffer.toString();
    }

    /**
     * @return the ASM method type descriptor
     */
    public static String getMethodDescriptor(Class returnType, Class[] paramTypes) {
        // lets avoid class loading
        StringBuffer buffer = new StringBuffer("(");
        for (int i = 0; i < paramTypes.length; i++) {
            buffer.append(getTypeDescription(paramTypes[i]));
        }
        buffer.append(")");
        buffer.append(getTypeDescription(returnType));
        return buffer.toString();
    }

    public static String getTypeDescription(Class c) {
        return org.objectweb.asm.Type.getDescriptor(c);
    }

    /**
     * array types are special:
     * eg.: String[]: classname: [Ljava.lang.String;
     * Object:   classname: java.lang.Object
     * int[] :   classname: [I
     * unlike getTypeDescription '.' is not replaced by '/'.
     * it seems that makes problems for
     * the class loading if '.' is replaced by '/'
     *
     * @return the ASM type description for class loading
     */
    public static String getClassLoadingTypeDescription(ClassNode c) {
        StringBuffer buf = new StringBuffer();
        boolean array = false;
        while (true) {
            if (c.isArray()) {
                buf.append('[');
                c = c.getComponentType();
                array = true;
            } else {
                if (ClassHelper.isPrimitiveType(c)) {
                    buf.append(getTypeDescription(c));
                } else {
                    if (array) buf.append('L');
                    buf.append(c.getName());
                    if (array) buf.append(';');
                }
                return buf.toString();
            }
        }
    }

    /**
     * array types are special:
     * eg.: String[]: classname: [Ljava/lang/String;
     * int[]: [I
     *
     * @return the ASM type description
     */
    public static String getTypeDescription(ClassNode c) {
        return getTypeDescription(c, true);
    }

    /**
     * array types are special:
     * eg.: String[]: classname: [Ljava/lang/String;
     * int[]: [I
     *
     * @return the ASM type description
     */
    private static String getTypeDescription(ClassNode c, boolean end) {
        StringBuffer buf = new StringBuffer();
        ClassNode d = c;
        while (true) {
            if (ClassHelper.isPrimitiveType(d)) {
                char car;
                if (d == ClassHelper.int_TYPE) {
                    car = 'I';
                } else if (d == ClassHelper.VOID_TYPE) {
                    car = 'V';
                } else if (d == ClassHelper.boolean_TYPE) {
                    car = 'Z';
                } else if (d == ClassHelper.byte_TYPE) {
                    car = 'B';
                } else if (d == ClassHelper.char_TYPE) {
                    car = 'C';
                } else if (d == ClassHelper.short_TYPE) {
                    car = 'S';
                } else if (d == ClassHelper.double_TYPE) {
                    car = 'D';
                } else if (d == ClassHelper.float_TYPE) {
                    car = 'F';
                } else /* long */ {
                    car = 'J';
                }
                buf.append(car);
                return buf.toString();
            } else if (d.isArray()) {
                buf.append('[');
                d = d.getComponentType();
            } else {
                buf.append('L');
                String name = d.getName();
                int len = name.length();
                for (int i = 0; i < len; ++i) {
                    char car = name.charAt(i);
                    buf.append(car == '.' ? '/' : car);
                }
                if (end) buf.append(';');
                return buf.toString();
            }
        }
    }

    /**
     * @return an array of ASM internal names of the type
     */
    public static String[] getClassInternalNames(ClassNode[] names) {
        int size = names.length;
        String[] answer = new String[size];
        for (int i = 0; i < size; i++) {
            answer[i] = getClassInternalName(names[i]);
        }
        return answer;
    }

    public static void pushConstant(MethodVisitor mv, int value) {
        switch (value) {
            case 0:
                mv.visitInsn(ICONST_0);
                break;
            case 1:
                mv.visitInsn(ICONST_1);
                break;
            case 2:
                mv.visitInsn(ICONST_2);
                break;
            case 3:
                mv.visitInsn(ICONST_3);
                break;
            case 4:
                mv.visitInsn(ICONST_4);
                break;
            case 5:
                mv.visitInsn(ICONST_5);
                break;
            default:
                if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
                    mv.visitIntInsn(BIPUSH, value);
                } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
                    mv.visitIntInsn(SIPUSH, value);
                } else {
                    mv.visitLdcInsn(Integer.valueOf(value));
                }
        }
    }

    /**
     * negate a boolean on stack. true->false, false->true
     */
    public static void negateBoolean(MethodVisitor mv) {
        // code to negate the primitive boolean
        Label endLabel = new Label();
        Label falseLabel = new Label();
        mv.visitJumpInsn(IFNE, falseLabel);
        mv.visitInsn(ICONST_1);
        mv.visitJumpInsn(GOTO, endLabel);
        mv.visitLabel(falseLabel);
        mv.visitInsn(ICONST_0);
        mv.visitLabel(endLabel);
    }

    /**
     * load a message on the stack and remove it right away. Good for put a mark in the generated bytecode for debugging purpose.
     *
     * @param msg
     */
    /*public void mark(String msg) {
        mv.visitLdcInsn(msg);
        mv.visitInsn(POP);
    }*/

    /**
     * returns a name that Class.forName() can take. Notably for arrays:
     * [I, [Ljava.lang.String; etc
     * Regular object type:  java.lang.String
     *
     * @param name
     */
    public static String formatNameForClassLoading(String name) {
        if (name.equals("int")
                || name.equals("long")
                || name.equals("short")
                || name.equals("float")
                || name.equals("double")
                || name.equals("byte")
                || name.equals("char")
                || name.equals("boolean")
                || name.equals("void")
                ) {
            return name;
        }

        if (name == null) {
            return "java.lang.Object;";
        }

        if (name.startsWith("[")) {
            return name.replace('/', '.');
        }

        if (name.startsWith("L")) {
            name = name.substring(1);
            if (name.endsWith(";")) {
                name = name.substring(0, name.length() - 1);
            }
            return name.replace('/', '.');
        }

        String prefix = "";
        if (name.endsWith("[]")) { // todo need process multi
            prefix = "[";
            name = name.substring(0, name.length() - 2);
            if (name.equals("int")) {
                return prefix + "I";
            } else if (name.equals("long")) {
                return prefix + "J";
            } else if (name.equals("short")) {
                return prefix + "S";
            } else if (name.equals("float")) {
                return prefix + "F";
            } else if (name.equals("double")) {
                return prefix + "D";
            } else if (name.equals("byte")) {
                return prefix + "B";
            } else if (name.equals("char")) {
                return prefix + "C";
            } else if (name.equals("boolean")) {
                return prefix + "Z";
            } else {
                return prefix + "L" + name.replace('/', '.') + ";";
            }
        }
        return name.replace('/', '.');

    }

    /*public void dup() {
        mv.visitInsn(DUP);
    }*/

    public static void doReturn(MethodVisitor mv, ClassNode returnType) {
        if (returnType == ClassHelper.double_TYPE) {
            mv.visitInsn(DRETURN);
        } else if (returnType == ClassHelper.float_TYPE) {
            mv.visitInsn(FRETURN);
        } else if (returnType == ClassHelper.long_TYPE) {
            mv.visitInsn(LRETURN);
        } else if (
                returnType == ClassHelper.boolean_TYPE
                        || returnType == ClassHelper.char_TYPE
                        || returnType == ClassHelper.byte_TYPE
                        || returnType == ClassHelper.int_TYPE
                        || returnType == ClassHelper.short_TYPE) {
            //byte,short,boolean,int are all IRETURN
            mv.visitInsn(IRETURN);
        } else if (returnType == ClassHelper.VOID_TYPE) {
            mv.visitInsn(RETURN);
        } else {
            mv.visitInsn(ARETURN);
        }

    }

    private static boolean hasGenerics(Parameter[] param) {
        if (param.length == 0) return false;
        for (int i = 0; i < param.length; i++) {
            ClassNode type = param[i].getType();
            if (hasGenerics(type)) return true;
        }
        return false;
    }

    private static boolean hasGenerics(ClassNode type) {
        return type.isArray() ? hasGenerics(type.getComponentType()) : type.getGenericsTypes() != null;
    }

    public static String getGenericsMethodSignature(MethodNode node) {
        GenericsType[] generics = node.getGenericsTypes();
        Parameter[] param = node.getParameters();
        ClassNode returnType = node.getReturnType();

        if (generics == null && !hasGenerics(param) && !hasGenerics(returnType)) return null;

        StringBuffer ret = new StringBuffer(100);
        getGenericsTypeSpec(ret, generics);

        GenericsType[] paramTypes = new GenericsType[param.length];
        for (int i = 0; i < param.length; i++) {
            ClassNode pType = param[i].getType();
            if (pType.getGenericsTypes() == null || !pType.isGenericsPlaceHolder()) {
                paramTypes[i] = new GenericsType(pType);
            } else {
                paramTypes[i] = pType.getGenericsTypes()[0];
            }
        }
        addSubTypes(ret, paramTypes, "(", ")");
        addSubTypes(ret, new GenericsType[]{new GenericsType(returnType)}, "", "");
        return ret.toString();
    }

    private static boolean usesGenericsInClassSignature(ClassNode node) {
        if (!node.isUsingGenerics()) return false;
        if (hasGenerics(node)) return true;
        ClassNode sclass = node.getUnresolvedSuperClass(false);
        if (sclass.isUsingGenerics()) return true;
        ClassNode[] interfaces = node.getInterfaces();
        if (interfaces != null) {
            for (int i = 0; i < interfaces.length; i++) {
                if (interfaces[i].isUsingGenerics()) return true;
            }
        }

        return false;
    }

    public static String getGenericsSignature(ClassNode node) {
        if (!usesGenericsInClassSignature(node)) return null;
        GenericsType[] genericsTypes = node.getGenericsTypes();
        StringBuffer ret = new StringBuffer(100);
        getGenericsTypeSpec(ret, genericsTypes);
        GenericsType extendsPart = new GenericsType(node.getUnresolvedSuperClass(false));
        writeGenericsBounds(ret, extendsPart, true);
        ClassNode[] interfaces = node.getInterfaces();
        for (int i = 0; i < interfaces.length; i++) {
            GenericsType interfacePart = new GenericsType(interfaces[i]);
            writeGenericsBounds(ret, interfacePart, false);
        }
        return ret.toString();
    }

    private static void getGenericsTypeSpec(StringBuffer ret, GenericsType[] genericsTypes) {
        if (genericsTypes == null) return;
        ret.append('<');
        for (int i = 0; i < genericsTypes.length; i++) {
            String name = genericsTypes[i].getName();
            ret.append(name);
            ret.append(':');
            writeGenericsBounds(ret, genericsTypes[i], true);
        }
        ret.append('>');
    }

    public static String getGenericsBounds(ClassNode type) {
        GenericsType[] genericsTypes = type.getGenericsTypes();
        if (genericsTypes == null) return null;
        StringBuffer ret = new StringBuffer(100);
        if (type.isGenericsPlaceHolder()) {
            addSubTypes(ret, type.getGenericsTypes(), "", "");
        } else {
            GenericsType gt = new GenericsType(type);
            writeGenericsBounds(ret, gt, false);
        }

        return ret.toString();
    }

    private static void writeGenericsBoundType(StringBuffer ret, ClassNode printType, boolean writeInterfaceMarker) {
        if (writeInterfaceMarker && printType.isInterface()) ret.append(":");
        if (printType.equals(ClassHelper.OBJECT_TYPE) && printType.getGenericsTypes() != null) {
            ret.append("T");
            ret.append(printType.getGenericsTypes()[0].getName());
            ret.append(";");
        }
        else {
            ret.append(getTypeDescription(printType, false));
            addSubTypes(ret, printType.getGenericsTypes(), "<", ">");
        	if (!ClassHelper.isPrimitiveType(printType)) ret.append(";");
    	  }
    }

    private static void writeGenericsBounds(StringBuffer ret, GenericsType type, boolean writeInterfaceMarker) {
        if (type.getUpperBounds() != null) {
            ClassNode[] bounds = type.getUpperBounds();
            for (int i = 0; i < bounds.length; i++) {
                writeGenericsBoundType(ret, bounds[i], writeInterfaceMarker);
            }
        } else if (type.getLowerBound() != null) {
            writeGenericsBoundType(ret, type.getLowerBound(), writeInterfaceMarker);
        } else {
            writeGenericsBoundType(ret, type.getType(), writeInterfaceMarker);
        }
    }

    private static void addSubTypes(StringBuffer ret, GenericsType[] types, String start, String end) {
        if (types == null) return;
        ret.append(start);
        for (int i = 0; i < types.length; i++) {
            if (types[i].getType().isArray()) {
                ret.append("[");
                addSubTypes(ret, new GenericsType[]{new GenericsType(types[i].getType().getComponentType())}, "", "");
            }
            else {
            if (types[i].isPlaceholder()) {
                ret.append('T');
                    String name = types[i].getName();
                ret.append(name);
                ret.append(';');
            } else if (types[i].isWildcard()) {
                if (types[i].getUpperBounds() != null) {
                    ret.append('+');
                    writeGenericsBounds(ret, types[i], false);
                } else if (types[i].getLowerBound() != null) {
                    ret.append('-');
                    writeGenericsBounds(ret, types[i], false);
                } else {
                    ret.append('*');
                }
            } else {
                writeGenericsBounds(ret, types[i], false);
            }
        }
        }
        ret.append(end);
    }

    public static void load(MethodVisitor mv, ClassNode type, int idx) {
        if (type == ClassHelper.double_TYPE) {
            mv.visitVarInsn(DLOAD, idx);
        } else if (type == ClassHelper.float_TYPE) {
            mv.visitVarInsn(FLOAD, idx);
        } else if (type == ClassHelper.long_TYPE) {
            mv.visitVarInsn(LLOAD, idx);
        } else if (
                type == ClassHelper.boolean_TYPE
                        || type == ClassHelper.char_TYPE
                        || type == ClassHelper.byte_TYPE
                        || type == ClassHelper.int_TYPE
                        || type == ClassHelper.short_TYPE) {
            mv.visitVarInsn(ILOAD, idx);
        } else {
            mv.visitVarInsn(ALOAD, idx);
        }
    }
    

    public static void doCast(MethodVisitor mv, ClassNode type) {
        if (type == ClassHelper.OBJECT_TYPE) return;
        if (ClassHelper.isPrimitiveType(type) && type != ClassHelper.VOID_TYPE) {
            unbox(mv, type);
        } else {
            mv.visitTypeInsn(
                    CHECKCAST,
                    type.isArray() ? 
                            BytecodeHelper.getTypeDescription(type) : 
                            BytecodeHelper.getClassInternalName(type.getName()));
        }
    }
    
    public static void doCast(MethodVisitor mv, Class type) {
        if (type == Object.class) return;
        if (type.isPrimitive() && type != Void.TYPE) {
            unbox(mv, type);
        } else {
            mv.visitTypeInsn(
                    CHECKCAST,
                    type.isArray() ? 
                            BytecodeHelper.getTypeDescription(type) : 
                                BytecodeHelper.getClassInternalName(type.getName()));
        }
    }

    /**
     * Generates the bytecode to unbox the current value on the stack
     */
    public static void unbox(MethodVisitor mv, Class type) {
        if (type.isPrimitive() && type != Void.TYPE) {
            String returnString = "(Ljava/lang/Object;)" + BytecodeHelper.getTypeDescription(type);
            mv.visitMethodInsn(
                    INVOKESTATIC,
                    DTT_CLASSNAME,
                    type.getName() + "Unbox",
                    returnString);
        }
    }

    public static void unbox(MethodVisitor mv, ClassNode type) {
        if (type.isPrimaryClassNode()) return;
        // GRECLIPSE: start
        if (type.isPrimitive())
        // end
        unbox(mv, type.getTypeClass());
    }

    /**
     * box top level operand
     */
    public static boolean box(MethodVisitor mv, ClassNode type) {
        if (type.isPrimaryClassNode()) return false;
        // GRECLIPSE: start
        if (!type.isPrimitive()) return false;
        // end
        return box(mv, type.getTypeClass());
    }

    
    /**
     * Generates the bytecode to autobox the current value on the stack
     */
    public static boolean box(MethodVisitor mv, Class type) {
        if (ReflectionCache.getCachedClass(type).isPrimitive && type != void.class) {
            String returnString = "(" + BytecodeHelper.getTypeDescription(type) + ")Ljava/lang/Object;";
            mv.visitMethodInsn(INVOKESTATIC, DTT_CLASSNAME, "box", returnString);
            return true;
        }
        return false;
    }
}
