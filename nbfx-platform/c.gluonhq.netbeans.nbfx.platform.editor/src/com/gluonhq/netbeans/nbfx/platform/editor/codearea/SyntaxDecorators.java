package com.gluonhq.netbeans.nbfx.platform.editor.codearea;

import org.openide.filesystems.FileObject;

import java.util.Objects;

/**
 * Factory of the language-specific {@link BaseSyntaxDecorator}s, selected by the
 * file extension: CSS files get the lightweight CSS highlighter, XML/FXML files
 * the XML lexer highlighter, everything else the combined lex + Java analysis
 * decorator.
 */
public final class SyntaxDecorators {

    private SyntaxDecorators() {
    }

    /**
     * Creates the syntax decorator for the given file.
     *
     * @param fo the file being edited, never {@code null}
     * @return a new decorator matching the file's language
     */
    public static BaseSyntaxDecorator forFile(FileObject fo) {
        String ext = Objects.requireNonNull(fo).getExt().toLowerCase();
        String className = switch (ext) {
            case "css" -> "com.gluonhq.netbeans.nbfx.ide.editor.css.codearea.CssSyntaxDecorator";
            case "xml", "fxml" -> "com.gluonhq.netbeans.nbfx.ide.editor.xml.codearea.XmlSyntaxDecorator";
            default -> "com.gluonhq.netbeans.nbfx.java.editor.codearea.JavaSyntaxDecorator";
        };
        String moduleCodeName = switch (ext) {
            case "css" -> "com.gluonhq.netbeans.nbfx.ide.editor.css";
            case "xml", "fxml" -> "com.gluonhq.netbeans.nbfx.ide.editor.xml";
            default -> "com.gluonhq.netbeans.nbfx.java.editor";
        };
        try {
            org.openide.modules.ModuleInfo module =
                    org.openide.modules.Modules.getDefault().findCodeNameBase(moduleCodeName);
            if (module == null || !module.isEnabled()) {
                throw new IllegalStateException("Language module not enabled: " + moduleCodeName);
            }
            Class<?> type = module.getClassLoader().loadClass(className);
            return (BaseSyntaxDecorator) type.getConstructor(FileObject.class).newInstance(fo);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Missing language decorator: " + className, ex);
        }
    }
}
