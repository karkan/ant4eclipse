package org.ant4eclipse.jdt.ant.compiler;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.ant4eclipse.core.Ant4EclipseConfigurator;
import org.ant4eclipse.jdt.internal.ant.compiler.AntCompileJobDescription;
import org.ant4eclipse.jdt.internal.ant.compiler.CompilerArguments;
import org.ant4eclipse.jdt.internal.tools.ejc.loader.FilteringClassFileLoader;
import org.ant4eclipse.jdt.tools.ejc.CompileJobResult;
import org.ant4eclipse.jdt.tools.ejc.EjcAdapter;
import org.ant4eclipse.jdt.tools.ejc.CompileJobDescription.SourceFile;
import org.ant4eclipse.jdt.tools.ejc.loader.ClassFileLoader;
import org.ant4eclipse.jdt.tools.ejc.loader.ClassFileLoaderFactory;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.compilers.DefaultCompilerAdapter;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.resources.FileResource;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;

public class JDTCompilerAdapter extends DefaultCompilerAdapter {

  private static final String COMPILER_ARGS_REFID_KEY = "compiler.args.refid";

  public boolean execute() {

    // CompilerArguments
    String compilerArgsRefid = null;
    final String[] currentCompilerArgs = getJavac().getCurrentCompilerArgs();
    for (final String compilerArg : currentCompilerArgs) {
      if (compilerArg.startsWith(COMPILER_ARGS_REFID_KEY)) {
        final String[] args = compilerArg.split("=");
        compilerArgsRefid = args[1];
      }
    }

    // configure ant4eclipse
    Ant4EclipseConfigurator.configureAnt4Eclipse(getProject());

    final CompilerArguments compilerArguments = (CompilerArguments) getProject().getReference(compilerArgsRefid);

    if (compilerArguments == null) {
      // TODO
      throw new RuntimeException();
    }

    // source path is not supported!
    if (getJavac().getSourcepath() != null) {
      // TODO
      throw new BuildException("getJavac().getSourcepath() != null");
    }

    // Files to Compile
    final List<SourceFile> sourceFiles = new LinkedList<SourceFile>();
    for (final File file : getJavac().getFileList()) {

      final File sourceFolder = getSourceFolder(file);
      final String sourceFileName = file.getAbsolutePath().substring(
          sourceFolder.getAbsolutePath().length() + File.separator.length());

      // TODO
      final SourceFile sourceFile = new SourceFile(sourceFolder, sourceFileName, compilerArguments
          .getOutputFolder(sourceFolder));
      sourceFiles.add(sourceFile);
    }

    final EjcAdapter ejcAdapter = EjcAdapter.Factory.create();

    final AntCompileJobDescription compileJobDescription = new AntCompileJobDescription();
    compileJobDescription.setSourceFiles(sourceFiles.toArray(new SourceFile[0]));
    // TODO
    final CompilerOptions compilerOptions = new CompilerOptions();
    compilerOptions.complianceLevel = ClassFileConstants.JDK1_5;
    compilerOptions.sourceLevel = ClassFileConstants.JDK1_5;
    compilerOptions.targetJDK = ClassFileConstants.JDK1_5;
    compileJobDescription.setCompilerOptions(compilerOptions.getMap());
    compileJobDescription.setClassFileLoader(createClassFileLoader(compilerArguments));

    final CompileJobResult compileJobResult = ejcAdapter.compile(compileJobDescription);

    compileJobResult.dumpProblems();

    if (!compileJobResult.succeeded()) {
      throw new BuildException();
    }

    return true;
  }

  private File getSourceFolder(final File sourceFile) {
    final String absolutePath = sourceFile.getAbsolutePath();

    final String[] srcDirs = getJavac().getSrcdir().list();
    for (final String srcDir : srcDirs) {
      if (absolutePath.startsWith(srcDir) && absolutePath.charAt(srcDir.length()) == File.separatorChar) {
        return new File(srcDir);
      }
    }
    // TODO
    throw new RuntimeException();
  }

  private ClassFileLoader createClassFileLoader(final CompilerArguments compilerArguments) {

    // create class file loader list
    final List<ClassFileLoader> classFileLoaderList = new LinkedList<ClassFileLoader>();

    // add boot class loader
    classFileLoaderList.add(createBootClassLoader(compilerArguments));

    // add class loader for class path entries
    final Path classpath = getJavac().getClasspath();
    for (final Iterator iterator = classpath.iterator(); iterator.hasNext();) {
      final FileResource fileResource = (FileResource) iterator.next();
      if (fileResource.getFile().exists()) {
        // TODO: LIBRARY AND PROJECT
        final ClassFileLoader myclassFileLoader = ClassFileLoaderFactory.createClasspathClassFileLoader(fileResource
            .getFile(), EjcAdapter.LIBRARY);
        classFileLoaderList.add(myclassFileLoader);
      }
    }

    // return the compound class file loader
    return ClassFileLoaderFactory.createCompoundClassFileLoader(classFileLoaderList.toArray(new ClassFileLoader[0]));
  }

  private ClassFileLoader createBootClassLoader(final CompilerArguments compilerArguments) {
    final Path bootclasspath = getJavac().getBootclasspath();

    final List<ClassFileLoader> bootClassFileLoaders = new LinkedList<ClassFileLoader>();

    for (final Iterator<FileResource> iterator = bootclasspath.iterator(); iterator.hasNext();) {
      final FileResource fileResource = iterator.next();
      if (fileResource.getFile().exists()) {
        final ClassFileLoader classFileLoader = ClassFileLoaderFactory.createClasspathClassFileLoader(fileResource
            .getFile(), EjcAdapter.LIBRARY);
        bootClassFileLoaders.add(classFileLoader);
      }
    }

    final ClassFileLoader classFileLoader = ClassFileLoaderFactory.createCompoundClassFileLoader(bootClassFileLoaders
        .toArray(new ClassFileLoader[0]));
    if (compilerArguments.hasBootClassPathAccessRestrictions()) {
      return new FilteringClassFileLoader(classFileLoader, compilerArguments.getBootClassPathAccessRestrictions());
    } else {
      return classFileLoader;
    }
  }
}
