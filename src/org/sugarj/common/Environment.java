package org.sugarj.common;


import java.io.File;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.spoofax.interpreter.terms.IStrategoTerm;
import org.sugarj.common.path.AbsolutePath;
import org.sugarj.common.path.Path;
import org.sugarj.common.path.RelativePath;
import org.sugarj.common.path.SourceLocation;
import org.sugarj.stdlib.StdLib;


/**
 * Shared execution environment.
 * 
 * @author Sebastian Erdweg <seba at informatik uni-marburg de>
 */
public class Environment implements Serializable {
  
  private static final long serialVersionUID = -8403625415393122607L;

  public static Map<Path, IStrategoTerm> terms = new WeakHashMap<Path, IStrategoTerm>();
  
  public static String sep = "/";
  public static String classpathsep = File.pathSeparator;

  /**
   * @author Sebastian Erdweg <seba at informatik uni-marburg de>
   */
  private class RelativePathBin extends RelativePath {
    private static final long serialVersionUID = -4418944917032203709L;

    public RelativePathBin(String relativePath) {
      super(relativePath);
    }
    
    @Override
    public Path getBasePath() {
      return bin;
    }
  }
  
  /**
   * @author Sebastian Erdweg <seba at informatik uni-marburg de>
   */
  private class RelativePathCache extends RelativePath {
    private static final long serialVersionUID = -6347244639940662095L;

    public RelativePathCache(String relativePath) {
      super(relativePath);
    }
    
    @Override
    public Path getBasePath() {
      return cacheDir;
    }
  }

  
  private Path cacheDir = null;
  
  private Path root = new AbsolutePath(".");
  
  private Path bin = new AbsolutePath(".");
  
  
  /* 
   * parse all imports simultaneously, i.e., not one after the other
   */
  private boolean atomicImportParsing = false;
  
  /*
   * don't check resulting sdf and stratego files after splitting
   */
  private boolean noChecking = false;

  private boolean generateJavaFile = false;
  
  
  private Path tmpDir = new AbsolutePath(System.getProperty("java.io.tmpdir"));
  
  private Set<SourceLocation> sourcePath = new HashSet<SourceLocation>();
  private Set<Path> includePath = new HashSet<Path>();
  
  public Environment() {
    includePath.add(bin);
    includePath.add(new AbsolutePath(StdLib.stdLibDir.getAbsolutePath()));
  }
  
  public Path getRoot() {
    return root;
  }

  public void setRoot(Path root) {
    this.root = root;
  }

  public Set<SourceLocation> getSourcePath() {
    return sourcePath;
  }

  public void setSourcePath(Set<SourceLocation> sourcePath) {
    this.sourcePath = sourcePath;
  }

  public Path getBin() {
    return bin;
  }

  public void setBin(Path bin) {
    if (this.bin!=null)
      includePath.remove(this.bin);
    this.bin = bin;
    includePath.add(bin);
  }

  public Path getCacheDir() {
    return cacheDir;
  }

  public void setCacheDir(Path cacheDir) {
    this.cacheDir = cacheDir;
  }

  public boolean isAtomicImportParsing() {
    return atomicImportParsing;
  }

  public void setAtomicImportParsing(boolean atomicImportParsing) {
    this.atomicImportParsing = atomicImportParsing;
  }

  public boolean isNoChecking() {
    return noChecking;
  }

  public void setNoChecking(boolean noChecking) {
    this.noChecking = noChecking;
  }

  public boolean isGenerateJavaFile() {
    return generateJavaFile;
  }

  public void setGenerateJavaFile(boolean generateJavaFile) {
    this.generateJavaFile = generateJavaFile;
  }

  public Path getTmpDir() {
    return tmpDir;
  }

  public void setTmpDir(Path tmpDir) {
    this.tmpDir = tmpDir;
  }

  public Set<Path> getIncludePath() {
    return includePath;
  }

  public void setIncludePath(Set<Path> includePath) {
    this.includePath = includePath;
  }

  public RelativePath createCachePath(String relativePath) {
    return new RelativePathCache(relativePath);
  }

  public RelativePath createBinPath(String relativePath) {
    return new RelativePathBin(relativePath);
  }
}
