import org.objectweb.asm.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Odpowiednik MockableJarGenerator z AGP (returnDefaultValues=true): każda metoda android.*
 *  (także native; abstrakcyjne bez zmian) zwraca wartość domyślną, a <clinit> jest pusty;
 *  modyfikator final jest usuwany. Użycie: MockableJar <android-all.jar> <wyjście.jar> */
public class MockableJar {
    public static void main(String[] a) throws Exception {
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(a[0]));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(a[1]))) {
            Set<String> seen = new HashSet<>();
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                String n = e.getName();
                if (!n.endsWith(".class") || !seen.add(n)) continue;
                if (!(n.startsWith("android/") || n.startsWith("com/android/") || n.startsWith("dalvik/")
                        || n.startsWith("org/xmlpull/") || n.startsWith("org/json/"))) continue;
                byte[] b = in.readAllBytes();
                ClassReader r = new ClassReader(b);
                ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                r.accept(new ClassVisitor(Opcodes.ASM9, w) {
                    @Override public void visit(int v, int acc, String name, String sig, String sup, String[] itf) {
                        superName[0] = sup == null ? "java/lang/Object" : sup;
                        super.visit(v, acc & ~Opcodes.ACC_FINAL, name, sig, sup, itf);
                    }
                    @Override public void visitInnerClass(String name, String outer, String inner, int acc) {
                        super.visitInnerClass(name, outer, inner, acc & ~Opcodes.ACC_FINAL);
                    }
                    @Override public MethodVisitor visitMethod(int acc, String name, String desc, String sig, String[] ex) {
                        if ((acc & Opcodes.ACC_ABSTRACT) != 0) return super.visitMethod(acc, name, desc, sig, ex);
                        int nacc = acc & ~(Opcodes.ACC_NATIVE | Opcodes.ACC_FINAL);
                        MethodVisitor mv = super.visitMethod(nacc, name, desc, sig, ex);
                        if (name.equals("<clinit>")) { mv.visitCode(); mv.visitInsn(Opcodes.RETURN); mv.visitMaxs(0, 0); mv.visitEnd(); return null; }
                        mv.visitCode();
                        Type ret = Type.getReturnType(desc);
                        if (name.equals("<init>")) {
                            // Konstruktor: tylko bezargumentowy super() (jak pusty obiekt z AGP).
                            mv.visitVarInsn(Opcodes.ALOAD, 0);
                            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superName[0], "<init>", "()V", false);
                            mv.visitInsn(Opcodes.RETURN);
                        } else switch (ret.getSort()) {
                            case Type.VOID: mv.visitInsn(Opcodes.RETURN); break;
                            case Type.BOOLEAN: case Type.CHAR: case Type.BYTE: case Type.SHORT: case Type.INT:
                                mv.visitInsn(Opcodes.ICONST_0); mv.visitInsn(Opcodes.IRETURN); break;
                            case Type.LONG: mv.visitInsn(Opcodes.LCONST_0); mv.visitInsn(Opcodes.LRETURN); break;
                            case Type.FLOAT: mv.visitInsn(Opcodes.FCONST_0); mv.visitInsn(Opcodes.FRETURN); break;
                            case Type.DOUBLE: mv.visitInsn(Opcodes.DCONST_0); mv.visitInsn(Opcodes.DRETURN); break;
                            default: mv.visitInsn(Opcodes.ACONST_NULL); mv.visitInsn(Opcodes.ARETURN);
                        }
                        mv.visitMaxs(0, 0);
                        mv.visitEnd();
                        return null;
                    }
                }, ClassReader.SKIP_CODE);
                out.putNextEntry(new ZipEntry(n));
                out.write(w.toByteArray());
                out.closeEntry();
            }
        }
    }
    static final String[] superName = new String[1];
}
