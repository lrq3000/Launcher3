/* Copyright (C) 2026 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.util.PsiTreeUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtPsiFactory;

/** Parse edited Kotlin files using the installed compiler. This checks syntax only, not AOSP type resolution. */
public final class KotlinSyntaxCheck {
    public static void main(String[] args) throws Exception {
        Disposable disposable = Disposer.newDisposable();
        try {
            KotlinCoreEnvironment environment = KotlinCoreEnvironment.createForProduction(
                    disposable, new CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES);
            KtPsiFactory factory = new KtPsiFactory(environment.getProject(), false);
            for (String name : args) {
                KtFile file = factory.createFile(Path.of(name).getFileName().toString(), Files.readString(Path.of(name)));
                for (PsiErrorElement error : PsiTreeUtil.collectElementsOfType(file, PsiErrorElement.class)) {
                    throw new AssertionError(name + ": " + error.getErrorDescription());
                }
            }
            System.out.println("PASS Kotlin syntax only: " + args.length + " edited files (not full type checking)");
        } finally {
            Disposer.dispose(disposable);
        }
    }
}
