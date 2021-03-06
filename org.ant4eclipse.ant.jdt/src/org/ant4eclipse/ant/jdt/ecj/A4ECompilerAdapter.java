/**********************************************************************
 * Copyright (c) 2005-2009 ant4eclipse project team.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nils Hartmann, Daniel Kasmeroglu, Gerd Wuetherich
 **********************************************************************/
package org.ant4eclipse.ant.jdt.ecj;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.ant4eclipse.ant.core.AntConfigurator;
import org.ant4eclipse.ant.jdt.EcjAdditionalCompilerArguments;
import org.ant4eclipse.lib.core.Assure;
import org.ant4eclipse.lib.core.exception.Ant4EclipseException;
import org.ant4eclipse.lib.core.logging.A4ELogging;
import org.ant4eclipse.lib.core.util.PerformanceLogging;
import org.ant4eclipse.lib.core.util.StringMap;
import org.ant4eclipse.lib.core.util.Utilities;
import org.ant4eclipse.lib.jdt.ecj.ClassFileLoader;
import org.ant4eclipse.lib.jdt.ecj.ClassFileLoaderFactory;
import org.ant4eclipse.lib.jdt.ecj.CompileJobDescription;
import org.ant4eclipse.lib.jdt.ecj.CompileJobResult;
import org.ant4eclipse.lib.jdt.ecj.DefaultCompileJobDescription;
import org.ant4eclipse.lib.jdt.ecj.EcjAdapter;
import org.ant4eclipse.lib.jdt.ecj.EcjExceptionCodes;
import org.ant4eclipse.lib.jdt.ecj.SourceFile;
import org.ant4eclipse.lib.jdt.ecj.SourceFileFactory;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.compilers.DefaultCompilerAdapter;
import org.apache.tools.ant.taskdefs.condition.Os;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.resources.FileResource;
import org.eclipse.jdt.core.compiler.CategorizedProblem;

/**
 * <p>
 * Basic implementation of a compiler adapter used within A4E.
 * </p>
 * 
 * @author Gerd W&uuml;therich (gerd@gerd-wuetherich.de)
 * @author Daniel Kasmeroglu (Daniel.Kasmeroglu@Kasisoft.net)
 */
public abstract class A4ECompilerAdapter extends DefaultCompilerAdapter {

  private static final String ANT4ECLIPSE_DEFAULT_FILE_ENCODING = "ant4eclipse.default.file.encoding";

  private static final String ANT4ECLIPSE_COMPILE_ERRORS_FILE   = "ant4eclipse.compile.errors.file";

  /** format of the compile problem message */
  private static final String COMPILE_PROBLEM_MESSAGE           = "----------\n%s. %s in %s (at line %s)\n%s\n%s\n%s\n";

  /** the compiler argument separator */
  private static final String COMPILER_ARGS_SEPARATOR           = "=";

  /** the refid key for the additional compiler arguments */
  private static final String COMPILER_ARGS_REFID_KEY           = "compiler.args.refid";

  /** the refid key for compiler.options.file */
  private static final String COMPILER_OPTIONS_FILE             = "compiler.options.file";

  /**
   * the refid key for default compiler options file.
   * 
   * <p>
   * If not set or set to an empty string no default compiler settings will be used
   * </p>
   */
  private static final String DEFAULT_COMPILER_OPTIONS_FILE     = "default.compiler.options.file";

  private boolean             _warnings                         = true;

  /**
   * Enables/disables the generation of warn messages.
   * 
   * @param newwarnings
   *          <code>true</code> <=> Enable warn messages.
   */
  public void setWarnings(boolean newwarnings) {
    this._warnings = newwarnings;
  }

  /**
   * <p>
   * Checks the preconditions of the A4ECompilerAdapter
   * </p>
   * 
   * @throws BuildException
   */
  private void preconditions() throws BuildException {
    // source path is not supported!
    if (getJavac().getSourcepath() != null) {
      throw new Ant4EclipseException(EcjExceptionCodes.JAVAC_SOURCE_PATH_NOT_SUPPORTED_EXCEPTION);
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean execute() throws BuildException {

    // Step 1: check preconditions
    preconditions();

    // Step 2: Configure ant4eclipse: This is unnecessary if the adapter is launched within the
    // JdtCompilerTask as it's using the defining classpath. But it's still useful in case someone
    // wants to make use of the original Javac task. Anyway there's no harm in using this function
    // anymore.
    AntConfigurator.configureAnt4Eclipse(getProject());

    // Step 3: Fetch compiler arguments
    EcjAdditionalCompilerArguments ecjAdditionalCompilerArguments = fetchEcjAdditionalCompilerArguments();

    // Step 4: create CompileJobDescription
    DefaultCompileJobDescription compileJobDescription = new DefaultCompileJobDescription();
    SourceFile[] sourceFiles = getSourceFilesToCompile(ecjAdditionalCompilerArguments);
    compileJobDescription.setSourceFiles(sourceFiles);
    compileJobDescription.setClassFileLoader(createClassFileLoader(ecjAdditionalCompilerArguments));

    // Step 5: set the compiler options
    String compilerOptionsFileName = extractJavacCompilerArg(COMPILER_OPTIONS_FILE, null);
    String defaultCompilerOptionsFileName = extractJavacCompilerArg(DEFAULT_COMPILER_OPTIONS_FILE, null);
    StringMap compilerOptions = CompilerOptionsProvider.getCompilerOptions(getJavac(), compilerOptionsFileName,
        defaultCompilerOptionsFileName);
    compileJobDescription.setCompilerOptions(compilerOptions);

    if (A4ELogging.isTraceingEnabled()) {
      A4ELogging.trace("CompileJobDescription: %s", compileJobDescription);
    }

    // Step 6: Compile
    CompileJobResult compileJobResult = compile(compileJobDescription);

    // Step 7: dump result
    CategorizedProblem[] categorizedProblems = compileJobResult.getCategorizedProblems();

    // Buffer for messages
    StringBuilder builder = new StringBuilder();

    for (int i = 0; i < categorizedProblems.length; i++) {
      CategorizedProblem categorizedProblem = categorizedProblems[i];
      if (categorizedProblem.isError() || (categorizedProblem.isWarning() && !getJavac().getNowarn())) {
        String fileName = String.valueOf(categorizedProblem.getOriginatingFileName());
        for (SourceFile sourceFile : sourceFiles) {
          if (fileName.equals(sourceFile.getSourceFileName())) {
            if (!categorizedProblem.isError()) {
              if (!this._warnings) {
                continue;
              }
            }
            Object[] args = new Object[7];
            args[0] = Integer.valueOf(i + 1);
            args[1] = categorizedProblem.isError() ? "ERROR" : "WARNING";
            args[2] = sourceFile.getSourceFile().getAbsolutePath();
            args[3] = Integer.valueOf(categorizedProblem.getSourceLineNumber());
            String[] problematicLine = readProblematicLine(sourceFile, categorizedProblem);
            args[4] = problematicLine[0];
            args[5] = problematicLine[1];
            args[6] = categorizedProblem.getMessage();
            builder.append(String.format(COMPILE_PROBLEM_MESSAGE, args));
            if (i + 1 == categorizedProblems.length) {
              builder.append("----------\n");
            }
          }
        }
      }
    }

    // Dump error messages if any
    if (builder.length() > 0) {
      // Dump to logging system
      A4ELogging.error(builder.toString());

      // Optional: dump to specified file
      String compilerErrorFile = System.getProperty(ANT4ECLIPSE_COMPILE_ERRORS_FILE);
      if (compilerErrorFile != null) {
        Utilities.appendFile(new File(compilerErrorFile), builder.toString().getBytes());
      }
    }

    // if the destination directory has been specified for the javac task we might need
    // to copy the generated class files
    if (compileJobResult.succeeded() && (getJavac().getDestdir() != null)) {
      /**
       * @todo [12-Apr-2011:KASI] This needs to be supported for Javac, too. It would be possible to use the destdir
       *       alternatively but references like the EcjAdditionalCompilerArguments need to be adopted in this case.
       */
      File destdir = Utilities.getCanonicalFile(getJavac().getDestdir());

      PerformanceLogging.start(A4ECompilerAdapter.class, "cloneClasses");
      try {
        cloneClasses(destdir, compileJobResult.getCompiledClassFiles());
      } finally {
        PerformanceLogging.stop(A4ECompilerAdapter.class, "cloneClasses");
      }
    }

    // throw Exception if compilation was not successful
    if (!compileJobResult.succeeded()) {
      throw new Ant4EclipseException(EcjExceptionCodes.COMPILATION_WAS_NOT_SUCCESFUL);
    }

    // Step 8: Return
    return true;

  }

  /**
   * Clones all generated class files while copying them into the user specified directory.
   * 
   * @param destdir
   *          The destination director to save the classes to. Not <code>null</code>.
   * @param compiledclasses
   *          A map which provides all compiled classes. Not <code>null</code>.
   */
  private void cloneClasses(File destdir, Map<String, File> compiledclasses) {
    if (!destdir.isAbsolute()) {
      destdir = destdir.getAbsoluteFile();
    }
    for (Map.Entry<String, File> entry : compiledclasses.entrySet()) {
      File destfile = Utilities.getCanonicalFile(new File(destdir, entry.getKey()));
      Utilities.mkdirs(destfile.getParentFile());
      if (!destfile.equals(entry.getValue())) {
        Utilities.copy(entry.getValue(), destfile);
      }
    }
  }

  /**
   * Runs the compilation according to the supplied compilation description.
   * 
   * @param description
   *          The description which provides all necessary information for the compilation.
   * 
   * @return A descriptional instance which provides some information which came up during the compilation.
   */
  protected abstract CompileJobResult compile(CompileJobDescription description);

  /**
   * <p>
   * Returns an array with all the source files to compile.
   * </p>
   * 
   * @param compilerArguments
   *          can be null
   * @return the source files to compile
   */
  private SourceFile[] getSourceFilesToCompile(EcjAdditionalCompilerArguments compilerArguments) {

    // get default destination folder
    File defaultDestinationFolder = getJavac().getDestdir();

    // get the files to compile
    List<SourceFile> sourceFiles = new LinkedList<SourceFile>();

    File[] filelist = getJavac().getFileList();
    for (File file : filelist) {
      A4ELogging.debug("Sourcefile provided by the javac-task: %s", file);
    }

    // support for filtered filesets
    if ((compilerArguments != null) && compilerArguments.hasSourceFilteredFilesetPath()) {

      // create the result list
      List<File> files = new LinkedList<File>();

      Path path = compilerArguments.getSourceFilteredFilesetPath();
      Iterator<?> iterator = path.iterator();
      while (iterator.hasNext()) {
        Object object = iterator.next();
        if (object instanceof FileResource) {
          FileResource fileResource = (FileResource) object;
          File sourceFile = fileResource.getFile();
          // only handle java files
          if (sourceFile.getName().endsWith(".java")) {
            files.add(fileResource.getFile());
          }
        }
      }

      // set the file list
      filelist = files.toArray(new File[files.size()]);
      for (File file : filelist) {
        A4ELogging.debug("Sourcefile which passed the filters: %s", file);
      }

      // log
      A4ELogging.info("Compiling %s source %s (filtered %s source %s from source file list).", Integer
          .valueOf(filelist.length), filelist.length > 1 ? "files" : "file", Integer.valueOf(this.compileList.length
          - filelist.length), this.compileList.length - filelist.length > 1 ? "files" : "file");
    }

    // iterate over all the source files and create SourceFile
    for (File file : filelist) {

      if (!hasSourceFolder(file)) {
        // the user has restricted the source folders for the compilation.
        // f.e. the project has two source folders while the user only compiles one at
        // a time
        continue;
      }

      // get the source folder
      File sourceFolder = getSourceFolder(file);

      // get the relative source file name
      String sourceFileName = file.getAbsolutePath().substring(
          sourceFolder.getAbsolutePath().length() + File.separator.length());

      // get the destination folder
      File destinationFolder = compilerArguments != null ? compilerArguments.getOutputFolder(sourceFolder)
          : defaultDestinationFolder;

      // make sure a destination folder is set
      if (destinationFolder == null) {
        throw new Ant4EclipseException(EcjExceptionCodes.NO_DEST_PATH_SET);
      }

      // BUG-FIX for http://www.javakontor.org:8080/jira/browse/AE-203
      // compile package-info.java first
      if (sourceFileName.endsWith("package-info.java")) {
        // add the new source file
        sourceFiles.add(0,
            SourceFileFactory.createSourceFile(sourceFolder, sourceFileName, destinationFolder, getDefaultEncoding()));
      }
      // END BUG-FIX
      else {
        // add the new source file
        sourceFiles.add(SourceFileFactory.createSourceFile(sourceFolder, sourceFileName, destinationFolder,
            getDefaultEncoding()));
      }
    }

    // return the result
    return sourceFiles.toArray(new SourceFile[sourceFiles.size()]);
  }

  /**
   * <p>
   * Returns the source folder for the given source file.
   * </p>
   * 
   * @param sourceFile
   *          the source file.
   * @return the source folder
   */
  private File getSourceFolder(File sourceFile) {

    // get the absolute path
    String absolutePath = sourceFile.getAbsolutePath();

    // get the list of all source directories
    String[] srcDirs = getJavac().getSrcdir().list();

    // find the 'right' source directory
    for (String srcDir : srcDirs) {
      if (absolutePath.startsWith(srcDir) && absolutePath.charAt(srcDir.length()) == File.separatorChar) {
        return new File(srcDir);
      }
    }

    // source folder for source file does not exist...
    throw new Ant4EclipseException(EcjExceptionCodes.SOURCE_FOLDER_FOR_SOURCE_FILE_DOES_NOT_EXIST,
        sourceFile.getAbsolutePath());
  }

  /**
   * <p>
   * Returns <code>true</code> if there's a source folder for the given source file.
   * </p>
   * 
   * @param sourceFile
   *          the source file.
   * @return <code>true</code> the source folder exists for the given source file.
   */
  private boolean hasSourceFolder(File sourceFile) {

    // get the absolute path
    String absolutePath = sourceFile.getAbsolutePath();

    // get the list of all source directories
    String[] srcDirs = getJavac().getSrcdir().list();

    // find the 'right' source directory
    for (String srcDir : srcDirs) {
      if (absolutePath.startsWith(srcDir) && absolutePath.charAt(srcDir.length()) == File.separatorChar) {
        return true;
      }
    }

    // source folder for source file does not exist...
    return false;
  }

  /**
   * <p>
   * Creates class file loader.
   * </p>
   * 
   * @param compilerArguments
   *          the compiler arguments, can be <code>null</code>.
   * @return the class file loader.
   */
  @SuppressWarnings("unchecked")
  private ClassFileLoader createClassFileLoader(EcjAdditionalCompilerArguments compilerArguments) {

    // Step 1: create class file loader list
    List<ClassFileLoader> classFileLoaderList = new LinkedList<ClassFileLoader>();

    // Step 2: add boot class loader
    classFileLoaderList.add(createBootClassLoader(compilerArguments));

    // Step 3: add class loader for class path entries
    Iterator<FileResource> iterator = getJavac().getClasspath().iterator();
    while (iterator.hasNext()) {

      // get the file resource that contains the class files
      FileResource fileResource = iterator.next();
      File classesFile = fileResource.getFile();
      ClassFileLoader myclassFileLoader = null;

      // jar files
      if (classesFile.isFile()) {

        // if (ClassFileLoaderCache.getInstance().hasClassFileLoader(classesFile)) {
        // myclassFileLoader = ClassFileLoaderCache.getInstance().getClassFileLoader(classesFile);
        // } else {
        myclassFileLoader = ClassFileLoaderFactory.createClasspathClassFileLoader(classesFile, EcjAdapter.LIBRARY,
            new File[] { classesFile }, new File[] {});
        // ClassFileLoaderCache.getInstance().storeClassFileLoader(classesFile, myclassFileLoader);
        // }

      } else {

        // get source folders if available
        File[] sourceFolders = new File[] {};

        if ((compilerArguments != null) && compilerArguments.hasSourceFoldersForOutputFolder(classesFile)) {
          sourceFolders = compilerArguments.getSourceFoldersForOutputFolder(classesFile);
        }

        // create class file loader for file resource
        // TODO: LIBRARY AND PROJECT
        myclassFileLoader = ClassFileLoaderFactory.createClasspathClassFileLoader(classesFile, EcjAdapter.LIBRARY,
            new File[] { classesFile }, sourceFolders);
      }

      // create and add FilteringClassFileLoader is necessary
      if (compilerArguments != null && compilerArguments.hasAccessRestrictions(fileResource.getFile())) {
        classFileLoaderList.add(ClassFileLoaderFactory.createFilteringClassFileLoader(myclassFileLoader,
            compilerArguments.getAccessRestrictions(fileResource.getFile())));
      }
      // else add class file loader
      else {
        classFileLoaderList.add(myclassFileLoader);
      }
    }

    // Step 4: return the compound class file loader
    return ClassFileLoaderFactory.createCompoundClassFileLoader(classFileLoaderList.toArray(new ClassFileLoader[0]));
  }

  /**
   * <p>
   * Create a boot class loader.
   * </p>
   * 
   * @param compilerArguments
   *          the compiler arguments , can be <code>null</code>.
   * @return the boot class loader
   */
  @SuppressWarnings("unchecked")
  private ClassFileLoader createBootClassLoader(EcjAdditionalCompilerArguments compilerArguments) {

    // Step 1: get the boot class path as specified in the javac task
    Path bootclasspath = getJavac().getBootclasspath();

    // if (ClassFileLoaderCache.getInstance().hasClassFileLoader(bootclasspath.toString())) {
    // return ClassFileLoaderCache.getInstance().getClassFileLoader(bootclasspath.toString());
    // }

    // Step 2: create ClassFileLoaders for each entry in the boot class path
    List<ClassFileLoader> bootClassFileLoaders = new LinkedList<ClassFileLoader>();

    // Step 3: iterate over the boot class path entries as specified in the ant path
    for (Iterator<FileResource> iterator = bootclasspath.iterator(); iterator.hasNext();) {

      // get the file resource
      FileResource fileResource = iterator.next();

      // create class file loader
      if (fileResource.getFile().exists()) {
        ClassFileLoader classFileLoader = ClassFileLoaderFactory.createClasspathClassFileLoader(fileResource.getFile(),
            EcjAdapter.LIBRARY);
        bootClassFileLoaders.add(classFileLoader);
      }
    }

    // Step 4: create compound class file loader
    ClassFileLoader classFileLoader = ClassFileLoaderFactory.createCompoundClassFileLoader(bootClassFileLoaders
        .toArray(new ClassFileLoader[0]));

    // Step 5: create FilteringClassFileLoader is necessary
    if (compilerArguments != null && compilerArguments.hasBootClassPathAccessRestrictions()) {

      // Step 4: debug
      if (A4ELogging.isDebuggingEnabled()) {
        A4ELogging.debug("Boot class path access restrictions: '%s'",
            compilerArguments.getBootClassPathAccessRestrictions());
      }

      classFileLoader = ClassFileLoaderFactory.createFilteringClassFileLoader(classFileLoader,
          compilerArguments.getBootClassPathAccessRestrictions());
    }

    // ClassFileLoaderCache.getInstance().storeClassFileLoader(bootclasspath.toString(), classFileLoader);

    return classFileLoader;
  }

  /**
   * <p>
   * Helper method that reads the compiler argument with the specified name from the ant's javac task.
   * </p>
   * <p>
   * Compiler arguments can be specified using <code>&lt;compilerarg/&gt;</code> subelement:
   * 
   * <pre>
   * &lt;code&gt; &lt;javac destdir=&quot;${executeJdtProject.default.output.directory}&quot;
   *   debug=&quot;on&quot;
   *   source=&quot;1.5&quot;&gt;
   *   ...
   *   &lt;compilerarg value=&quot;compiler.args.refid=executeJdtProject.compiler.args&quot;
   *                compiler=&quot;org.ant4eclipse.ant.jdt.ecj.EcjCompilerAdapter&quot; /&gt;
   * &lt;/javac&gt;
   * &lt;/code&gt;
   * </pre>
   * 
   * </p>
   * 
   * @param argumentName
   * @param defaultValue
   * @return
   */
  private String extractJavacCompilerArg(String argumentName, String defaultValue) {
    Assure.notNull("argumentName", argumentName);

    // Step 1: Get all compilerArguments
    String[] currentCompilerArgs = getJavac().getCurrentCompilerArgs();

    // Step 2: Find the 'right' one
    for (String compilerArg : currentCompilerArgs) {

      // split the argument
      String[] args = compilerArg.split(COMPILER_ARGS_SEPARATOR);

      // requested one?
      if (args.length > 1 && argumentName.equalsIgnoreCase(args[0])) {

        // return the argument
        return args[1];
      }
    }

    // Step 3: Return defaultValue
    return defaultValue;
  }

  /**
   * <p>
   * Helper method that fetches the {@link EcjAdditionalCompilerArguments} from the underlying ant project. The
   * {@link EcjAdditionalCompilerArguments} are set when a JDT class path is resolved by ant4eclipse.
   * </p>
   * <p>
   * If no {@link EcjAdditionalCompilerArguments} are set, <code>null</code> will be returned.
   * </p>
   * 
   * @return the {@link EcjAdditionalCompilerArguments}
   */
  private EcjAdditionalCompilerArguments fetchEcjAdditionalCompilerArguments() {

    // Step 1: Fetch the CompilerArgument key
    String compilerArgsRefid = extractJavacCompilerArg(COMPILER_ARGS_REFID_KEY, null);

    // Step 2: Return null, if no EcjAdditionalCompilerArguments are set
    if (compilerArgsRefid == null) {
      return null;
    }

    // Step 3: Fetch the compiler arguments
    EcjAdditionalCompilerArguments compilerArguments = (EcjAdditionalCompilerArguments) getProject().getReference(
        compilerArgsRefid);

    // Step 4: Throw exception if null
    if (compilerArguments == null) {
      throw new Ant4EclipseException(EcjExceptionCodes.NO_ECJ_ADDITIONAL_COMPILER_ARGUMENTS_OBJECT, compilerArgsRefid);
    }

    A4ELogging.debug("Using compilerArguments '%s'", compilerArguments);

    // Step 5: Return the result
    return compilerArguments;
  }

  /**
   * <p>
   * </p>
   * 
   * @param sourceFile
   * @param lineNumber
   * @param sourceStart
   * @param sourceEnd
   * @return
   */
  private String[] readProblematicLine(SourceFile sourceFile, CategorizedProblem categorizedProblem) {
    Assure.notNull("sourceFile", sourceFile);
    Assure.notNull("categorizedProblem", categorizedProblem);

    int lineNumber = categorizedProblem.getSourceLineNumber();
    int sourceStart = categorizedProblem.getSourceStart();
    int sourceEnd = categorizedProblem.getSourceEnd();

    try {
      // Open the file that is the first
      // command line parameter
      FileInputStream fstream = new FileInputStream(sourceFile.getSourceFile());
      // Get the object of DataInputStream
      DataInputStream in = new DataInputStream(fstream);
      BufferedReader br = new BufferedReader(new InputStreamReader(in));
      int lineStart = 0;
      String strLine = "";
      // Read File Line By Line
      for (int i = 0; i < lineNumber; i++) {
        String newLine = br.readLine();

        lineStart = lineStart + strLine.length();
        if (i + 1 != lineNumber) {
          lineStart = lineStart + 1;
        }
        strLine = newLine;
      }
      Utilities.close(in);
      StringBuilder underscoreLine = new StringBuilder();
      for (int i = lineStart; i < sourceStart; i++) {
        if (strLine.charAt(i - lineStart) == '\t') {
          underscoreLine.append('\t');
        } else {
          underscoreLine.append(' ');
        }
      }
      for (int i = sourceStart; i <= sourceEnd; i++) {
        underscoreLine.append('^');
      }
      return new String[] { strLine, underscoreLine.toString() };
    } catch (Exception e) {// Catch exception if any
      return new String[] { "", "" };
    }
  }

  /**
   * <p>
   * Helper method. Returns the default encoding of the eclipse workspace.
   * </p>
   * 
   * @return the default encoding
   */
  private String getDefaultEncoding() {

    // Step 1: is the 'ANT4ECLIPSE_DEFAULT_FILE_ENCODING' property set?
    String property = getProject().getProperty(ANT4ECLIPSE_DEFAULT_FILE_ENCODING);
    if (property != null) {
      return property;
    }

    // Step 2: is the encoding set in the javac task?
    String encoding = getJavac().getEncoding();
    if (encoding != null) {
      return encoding;
    }

    // Step 3: try to resolve the os specific eclipse encoding
    if (Os.isFamily(Os.FAMILY_WINDOWS) && Charset.isSupported("Cp1252")) {
      return "Cp1252";
    } else if (Os.isFamily(Os.FAMILY_UNIX) && Charset.isSupported("UTF-8")) {
      return "UTF-8";
    } else if (Os.isFamily(Os.FAMILY_MAC) && Charset.isSupported("MacRoman")) {
      return "MacRoman";
    }

    // Step 4: last resort: return the default file encoding
    return System.getProperty("file.encoding");
  }

} /* ENDCLASS */
