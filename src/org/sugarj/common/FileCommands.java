package org.sugarj.common;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.apache.commons.io.IOUtils;
import org.sugarj.common.path.AbsolutePath;
import org.sugarj.common.path.Path;
import org.sugarj.common.path.RelativePath;

/**
 * Provides methods for doing stuff with files.
 * 
 * @author Sebastian Erdweg <seba at informatik uni-marburg de>
 */
public class FileCommands {
  
  public static void main(String[] args) {
    String targetDir = "/Users/andiderp/repos/pluto-ecosystem/build-monto/targetsbj";
    String p = "/Users/andiderp/repos/pluto-ecosystem/build-monto/targetsbj/monto/service/ast/AST.class";
    java.nio.file.Path rel = getRelativePath(new File(targetDir), new File(p));
    System.out.println(replaceExtension(rel, "java"));
  }

  public final static boolean DO_DELETE = true;
  private final static String FORWARD_SLASH = "/";
  public final static String TMP_DIR;
  static {
    try {
      TMP_DIR = File.createTempFile("tmp", "").getParent();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 
   * @param suffix
   *          without dot "."
   * @return
   * @throws IOException
   */
  public static Path newTempFile(String suffix) throws IOException {
    File f = File.createTempFile("sugarj", suffix == null || suffix.isEmpty() ? suffix : "." + suffix);
    final Path p = new AbsolutePath(f.getAbsolutePath());

    return p;
  }

  public static void deleteTempFiles(Path file) throws IOException {
    if (file == null)
      return;

    String parent = file.getFile().getParent();

    if (parent == null)
      return;
    else if (parent.equals(TMP_DIR))
      delete(file);
    else
      deleteTempFiles(new AbsolutePath(parent));
  }

  public static void delete(Path file) throws IOException {
    if (file == null)
      return;

    if (file.getFile().listFiles() != null)
      for (File f : file.getFile().listFiles())
        FileCommands.delete(new AbsolutePath(f.getPath()));

    file.getFile().delete();
  }

  public static void delete(File file) throws IOException {
    delete(file.toPath());
  }

  public static void delete(java.nio.file.Path file) throws IOException {
    if (file == null)
      return;

    Files.walkFileTree(file, new FileVisitor<java.nio.file.Path>() {

      @Override
      public FileVisitResult preVisitDirectory(java.nio.file.Path dir, BasicFileAttributes attrs) throws IOException {
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(java.nio.file.Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFileFailed(java.nio.file.Path file, IOException exc) throws IOException {
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(java.nio.file.Path dir, IOException exc) throws IOException {
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  public static void copyFile(Path from, Path to, CopyOption... options) throws IOException {
    Set<CopyOption> optSet = new HashSet<>();
    for (CopyOption o : options)
      optSet.add(o);
    optSet.add(StandardCopyOption.REPLACE_EXISTING);

    Files.copy(from.getFile().toPath(), to.getFile().toPath(), optSet.toArray(new CopyOption[optSet.size()]));
  }

  public static void copyFile(File from, File to, CopyOption... options) throws IOException {
    Set<CopyOption> optSet = new HashSet<>();
    for (CopyOption o : options)
      optSet.add(o);
    optSet.add(StandardCopyOption.REPLACE_EXISTING);
    Files.copy(from.toPath(), to.toPath(), optSet.toArray(new CopyOption[optSet.size()]));
  }

  public static void copyFile(InputStream in, OutputStream out) throws IOException {
    int len;
    byte[] b = new byte[1024];

    while ((len = in.read(b)) > 0)
      out.write(b, 0, len);
  }

  public static boolean acceptableAsAbsolute(String path) {

    return new File(path).isAbsolute() || path.startsWith("./") || path.startsWith("." + File.separator) || path.equals(".");

  }

  /**
   * Beware: one must not rename SDF files since the filename and the module
   * name needs to coincide. Instead generate a new file which imports the other
   * SDF file.
   * 
   * @param file
   * @param content
   * @throws IOException
   */
  public static void writeToFile(File file, String content) throws IOException {
    FileCommands.createFile(file);
    FileOutputStream fos = new FileOutputStream(file);
    fos.write(content.getBytes());
    fos.close();
  }

  public static void writeToFile(java.nio.file.Path file, String content) throws IOException {
    writeToFile(file.toFile(), content);
  }

  public static void writeLinesFile(File file, List<String> lines) throws IOException {
    FileCommands.createFile(file);
    BufferedWriter writer = new BufferedWriter(new FileWriter(file));
    Iterator<String> iter = lines.iterator();
    while (iter.hasNext()) {
      writer.write(iter.next());
      if (iter.hasNext()) {
        writer.write("\n");
      }
    }
    writer.flush();
    writer.close();
  }

  public static void appendToFile(Path file, String content) throws IOException {
    createFile(file);
    FileOutputStream fos = new FileOutputStream(file.getFile(), true);
    fos.write(content.getBytes());
    fos.close();
  }

  public static byte[] readFileAsByteArray(Path file) throws IOException {
    return readFileAsByteArray(file.getFile());
  }

  public static byte[] readFileAsByteArray(File file) throws IOException {
    return Files.readAllBytes(file.toPath());
  }

  public static String readFileAsString(File file) throws IOException {
    return readFileAsString(new AbsolutePath(file.getAbsolutePath()));
  }

  // from http://snippets.dzone.com/posts/show/1335
  // Author: http://snippets.dzone.com/user/daph2001
  public static String readFileAsString(Path filePath) throws IOException {
    StringBuilder fileData = new StringBuilder(1000);
    BufferedReader reader = new BufferedReader(new FileReader(filePath.getFile()));
    char[] buf = new char[1024];
    int numRead = 0;
    while ((numRead = reader.read(buf)) != -1)
      fileData.append(buf, 0, numRead);

    reader.close();
    return fileData.toString();
  }

  public static List<String> readFileLines(File file) throws IOException {
    BufferedReader reader = new BufferedReader(new FileReader(file));
    List<String> lines = new ArrayList<>();
    String temp;
    while ((temp = reader.readLine()) != null) {
      lines.add(temp);
    }
    reader.close();
    return lines;
  }

  public static String readStreamAsString(InputStream in) throws IOException {
    StringBuilder fileData = new StringBuilder(1000);
    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
    char[] buf = new char[1024];
    int numRead = 0;
    while ((numRead = reader.read(buf)) != -1)
      fileData.append(buf, 0, numRead);

    reader.close();
    return fileData.toString();
  }

  public static String fileName(URL url) {
    return fileName(new AbsolutePath(url.getPath()));
  }

  public static String fileName(File url) {
    return fileName(toCygwinPath(url.getPath()));
  }

  public static String fileName(URI uri) {
    return fileName(new AbsolutePath(uri.getPath()));
  }

  public static String fileName(Path file_doof) {
    return fileName(toCygwinPath(file_doof.getAbsolutePath()));
  }

  public static String fileName(String file) {
    int index = file.lastIndexOf(File.separator);

    if (index >= 0)
      file = file.substring(index + 1);

    index = file.lastIndexOf(".");

    if (index > 0)
      file = file.substring(0, index);

    return file;
  }

  public static RelativePath[] listFiles(Path p) {
    return listFiles(p, null);
  }

  public static RelativePath[] listFiles(Path p, FileFilter filter) {
    File[] files = p.getFile().listFiles(filter);
    RelativePath[] paths = new RelativePath[files.length];

    for (int i = 0; i < files.length; i++)
      paths[i] = new RelativePath(p, files[i].getName());

    return paths;
  }

  public static List<java.nio.file.Path> listFilesRecursive(java.nio.file.Path p) {
    return listFilesRecursive(p, null);
  }

  // Guarentees that list is mutable
  public static List<java.nio.file.Path> listFilesRecursive(java.nio.file.Path p, final FileFilter filter) {
    try {
      final List<java.nio.file.Path> files = new ArrayList<>();
      
      Files.walkFileTree(p, new SimpleFileVisitor<java.nio.file.Path>() {
        @Override
        public FileVisitResult visitFile(java.nio.file.Path file, BasicFileAttributes attrs) throws IOException {
          if (file.toFile().isDirectory())
            return FileVisitResult.CONTINUE;
          
          if (filter == null || filter.accept(file.toFile()))
            files.add(file);
          
          return FileVisitResult.CONTINUE;
        }
      });
      
      return Collections.unmodifiableList(files);
    } catch (IOException e) {
      return Collections.emptyList();
    }
  }

  /**
   * Finds the given file in the given list of paths.
   * 
   * @param filename
   *          relative filename.
   * @param paths
   *          list of possible paths to filename
   * @return full file path to filename or null
   */
  @Deprecated
  public static String findFile(String filename, List<String> paths) {
    return findFile(filename, paths.toArray(new String[] {}));
  }

  /**
   * Finds the given file in the given list of paths.
   * 
   * @param filename
   *          relative filename.
   * @param paths
   *          list of possible paths to filename
   * @return full file path to filename or null
   */
  @Deprecated
  public static String findFile(String filename, String... paths) {
    for (String path : paths) {
      File f = new File(path + File.separator + filename);
      if (f.exists())
        return f.getAbsolutePath();
    }

    return null;
  }

  public static File newTempDir() throws IOException {
    final File f = File.createTempFile("SugarJ", "");
    // need to delete the file, but want to reuse the filename
    f.delete();
    f.mkdir();
    return f;
  }

  public static File tryNewTempDir() {
    try {
      return newTempDir();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void prependToFile(Path file, String head) throws IOException {
    Path tmp = newTempFile("");
    file.getFile().renameTo(tmp.getFile());

    FileInputStream in = new FileInputStream(tmp.getFile());
    FileOutputStream out = new FileOutputStream(file.getFile());

    out.write(head.getBytes());

    int len;
    byte[] b = new byte[1024];

    while ((len = in.read(b)) > 0)
      out.write(b, 0, len);

    in.close();
    out.close();
    delete(tmp);
  }

  public static void createFile(Path file) throws IOException {
    File f = file.getFile();
    if (f.getParentFile().mkdirs())
      f.createNewFile();
  }

  public static void createFile(java.nio.file.Path file) throws IOException {
    try {
      Files.createDirectories(file.getParent());
      Files.createFile(file);
    } catch (FileAlreadyExistsException e) {
      // Is ok, then the file is there
    }
  }

  public static void createFile(File file) throws IOException {
    createFile(file.toPath());
  }

  /**
   * Create file with name deduced from hash in dir.
   * 
   * @param dir
   * @param hash
   * @return
   * @throws IOException
   */
  public static Path createFile(Path dir, int hash) throws IOException {
    Path p = new RelativePath(dir, hashFileName("sugarj", hash));
    createFile(p);
    return p;
  }

  public static void createDir(File dir) throws IOException {
    boolean isMade = dir.mkdirs();
    boolean exists = dir.exists();
    if (!isMade && !exists)
      throw new IOException("Failed to create the directories\n" + dir);
  }

  public static void createDir(java.nio.file.Path dir) throws IOException {
    Files.createDirectories(dir);
    boolean exists = Files.exists(dir);
    if (!exists)
      throw new IOException("Failed to create the directories\n" + dir);
  }

  /**
   * Create directory with name deduced from hash in dir.
   * 
   * @param dir
   * @param hash
   * @return
   * @throws IOException
   */
  public static File createDir(File dir, int hash) throws IOException {
    File p = new File(dir, hashFileName("SugarJ", hash));
    createDir(p);
    return p;
  }

  /**
   * Ensures that a path is suitable for a cygwin command line.
   */
  public static String toCygwinPath(String filepath) {
    // XXX hacky

    if (System.getProperty("os.name").toLowerCase().contains("win")) {
      filepath = filepath.replace("\\", "/");
      filepath = filepath.replace("/C:/", "/cygdrive/C/");
      filepath = filepath.replace("C:/", "/cygdrive/C/");
    }

    return filepath;
  }

  /**
   * Ensure that a path is suitable for a windows command line
   */
  public static String toWindowsPath(String filepath) {
    // XXX hacky

    if (System.getProperty("os.name").toLowerCase().contains("win")) {
      filepath = filepath.replace("/cygdrive/C", "C:");
      filepath = filepath.replace("/C:", "C:");
      filepath = filepath.replace("/", "\\");
    }

    return filepath;
  }

  /**
   * checks whether f1 was modified after f2.
   * 
   * @return true iff f1 was modified after f2.
   */
  public static boolean isModifiedLater(Path f1, Path f2) {
    return f1.getFile().lastModified() > f2.getFile().lastModified();
  }

  public static boolean fileExists(Path file) {
    return file != null && file.getFile().exists() && file.getFile().isFile();
  }

  public static boolean fileExists(File file) {
    return file != null && file.exists() && file.isFile();
  }

  public static boolean exists(Path file) {
    return file != null && file.getFile().exists();
  }

  public static boolean exists(File file) {
    return file != null && file.exists();
  }

  public static boolean exists(java.nio.file.Path file) {
    return file != null && Files.exists(file);
  }

  public static boolean exists(URI file) {
    return new File(file).exists();
  }

  public static String hashFileName(String prefix, int hash) {
    return prefix + (hash < 0 ? "1" + Math.abs(hash) : "0" + hash);
  }

  public static String hashFileName(String prefix, Object o) {
    return hashFileName(prefix, o.hashCode());
  }

  public static String getExtension(Path infile) {
    return getExtension(infile.getFile());
  }

  public static String getExtension(File infile) {
    return getExtension(infile.getName());
  }

  public static String getExtension(String infile) {
    int i = infile.lastIndexOf('.');

    if (i > 0)
      return infile.substring(i + 1, infile.length());

    return null;
  }

  public static String dropExtension(String file) {
    int i = file.lastIndexOf('.');

    if (i > 0)
      return file.substring(0, i);

    return file;
  }

  public static java.nio.file.Path dropExtension(java.nio.file.Path p) {
    java.nio.file.Path file = p.getFileName();
    java.nio.file.Path parent = p.getParent();

    String fileName = file.toString();

    int i = fileName.lastIndexOf('.');

    if (i > 0) {
      String newName = fileName.substring(0, i);
      if (parent == null) {
        return java.nio.file.Paths.get(newName);
      } else {
        return parent.resolve(newName);
      }
    }

    return p;
  }

  public static String dropDirectory(Path p) {
    String ext = getExtension(p);
    if (ext == null)
      return fileName(p);
    else
      return fileName(p) + "." + getExtension(p);
  }

  public static AbsolutePath replaceExtension(AbsolutePath p, String newExtension) {
    return p.replaceExtension(newExtension);
  }

  public static RelativePath replaceExtension(RelativePath p, String newExtension) {
    return p.replaceExtension(newExtension);
  }

  public static java.nio.file.Path replaceExtension(java.nio.file.Path p, String newExtension) {
    java.nio.file.Path withoutExt = dropExtension(p);
    return addExtension(withoutExt, newExtension);
  }

  public static File replaceExtension(File p, String newExtension) {
    return replaceExtension(p.toPath(), newExtension).toFile();
  }

  public static Path addExtension(Path p, String newExtension) {
    if (p instanceof RelativePath)
      return new RelativePath(((RelativePath) p).getBasePath(), ((RelativePath) p).getRelativePath() + "." + newExtension);
    return new AbsolutePath(p.getAbsolutePath() + "." + newExtension);
  }

  public static AbsolutePath addExtension(AbsolutePath p, String newExtension) {
    return new AbsolutePath(p.getAbsolutePath() + "." + newExtension);
  }

  public static java.nio.file.Path addExtension(java.nio.file.Path p, String newExtension) {
    String newName = p.getFileName() + "." + newExtension;
    if (p.getParent() == null) {
      return java.nio.file.Paths.get(newName);
    } else {
      return p.getParent().resolve(newName);
    }
  }

  public static File addExtension(File p, String newExtension) {
    return addExtension(p.toPath(), newExtension).toFile();
  }

  public static RelativePath dropFilename(RelativePath file) {
    return new RelativePath(file.getBasePath(), dropFilename(file.getRelativePath()));
  }

  public static AbsolutePath dropFilename(Path file) {
    return new AbsolutePath(dropFilename(file.getAbsolutePath()));
  }

  public static String dropFilename(String file) {
    int i = file.lastIndexOf(File.separator);
    if (i > 0)
      return file.substring(0, i);

    return "";
  }

  public static byte[] fileHash(Path file) throws IOException {
    try (FileInputStream inputStream = new FileInputStream(file.getFile())) {
      return streamHash(inputStream);
    }
  }

  public static byte[] fileHash(java.nio.file.Path file) throws IOException {
    try (InputStream inputStream = Files.newInputStream(file)) {
      return streamHash(inputStream);
    }
  }

  public static byte[] streamHash(InputStream inputStream) throws IOException {
    // http://www.codejava.net/coding/how-to-calculate-md5-and-sha-hash-values-in-java
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");

      byte[] bytesBuffer = new byte[1024];
      int bytesRead = -1;

      while ((bytesRead = inputStream.read(bytesBuffer)) != -1) {
        digest.update(bytesBuffer, 0, bytesRead);
      }

      byte[] hashedBytes = digest.digest();

      return hashedBytes;
    } catch (NoSuchAlgorithmException e) {
      return null;
    }

  }

  public static byte[] tryFileHash(Path file) {
    try {
      return FileCommands.fileHash(file);
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  public static boolean isEmptyFile(Path prog) throws IOException {
    FileInputStream in = null;

    try {
      in = new FileInputStream(prog.getFile());
      if (in.read() == -1)
        return true;
      return false;
    } finally {
      if (in != null)
        in.close();
    }
  }

  // cai 27.09.12
  // convert path-separator to that of the OS
  // so that strategoXT doesn't prepend ./ to C:/foo/bar/baz.
  public static String nativePath(String path) {
    return path.replace('/', File.separatorChar);
  }

  public static RelativePath getRelativePath(Path base, Path fullPath) {
    if (fullPath instanceof RelativePath && ((RelativePath) fullPath).getBasePath().equals(base))
      return (RelativePath) fullPath;

    String baseS = base.getAbsolutePath();
    String fullS = fullPath.getAbsolutePath();

    if (fullS.startsWith(baseS))
      return new RelativePath(base, fullS.substring(baseS.length()));

    return null;
  }

  public static java.nio.file.Path getRelativePath(java.nio.file.Path base, java.nio.file.Path fullPath) {
    try {
      return base.toAbsolutePath().relativize(fullPath.toAbsolutePath());
    } catch (IllegalArgumentException e) {
      // Cannot be relativized
      return null;
    }
  }

  public static java.nio.file.Path getRelativePath(File base, File fullPath) {
    return getRelativePath(base.toPath(), fullPath.toPath());
  }

  public static Path copyFile(Path from, Path to, Path file, CopyOption... options) {
    RelativePath p = getRelativePath(from, file);
    if (p == null)
      return null;

    RelativePath target = new RelativePath(to, p.getRelativePath());
    if (!FileCommands.exists(p))
      return target;
    try {
      copyFile(p, target, options);
      return target;
    } catch (IOException e) {
      e.printStackTrace();
      return target;
    }
  }

  public static File copyFile(File from, File to, File file, CopyOption... options) {
    java.nio.file.Path p = getRelativePath(from, file);
    if (p == null)
      return null;

    File target = new File(to, p.toString());
    try {
      if (!FileCommands.exists(target))
        target.getParentFile().mkdirs();
      copyFile(file, target, options);
      return target;
    } catch (IOException e) {
      e.printStackTrace();
      return target;
    }
  }

  public static String tryGetRelativePath(Path p) {
    if (p instanceof RelativePath)
      return ((RelativePath) p).getRelativePath();
    return p.getAbsolutePath();
  }

  public static java.nio.file.Path getRessourcePath(Class<?> clazz) {
    String className = clazz.getName();
    URL url = clazz.getResource(className.substring(className.lastIndexOf(".") + 1) + ".class");
    return getRessourcePath(url);
  }
  
  public static java.nio.file.Path getRessourcePath(URL url) {
    String path = url == null ? null : url.getPath();
    if (path == null)
      return null;

    // remove URL leftovers
    if (path.startsWith("file:")) {
      path = path.substring("file:".length());
    }

    // is the class file inside a jar?
    if (path.contains(".jar!")) {
      path = path.substring(0, path.indexOf(".jar!") + ".jar".length());
    }

    // have we found the class file?
    try {
      return Paths.get(trimFront(path));
    } catch (InvalidPathException e) {
      return null;
    }

  }

  public static boolean acceptable(String path) {
    return new File(path).isAbsolute() || path.startsWith("./") || path.startsWith("." + File.separator) || path.equals(".");
  }

  public static File unpackJarfile(File jar) throws IOException {
    File dir = newTempDir();
    unpackJarfile(dir, jar);
    return dir;
  }

  public static void unpackJarfile(File outdir, File jar) throws IOException {
    try (JarFile jarFile = new JarFile(jar)) {
      Enumeration<? extends ZipEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        File entryDestination = new File(outdir.getAbsolutePath(), entry.getName());
        entryDestination.getParentFile().mkdirs();
        if (entry.isDirectory())
          entryDestination.mkdirs();
        else {
          InputStream in = jarFile.getInputStream(entry);
          OutputStream out = new FileOutputStream(entryDestination);
          IOUtils.copy(in, out);
          IOUtils.closeQuietly(in);
          IOUtils.closeQuietly(out);
        }
      }
    }
  }
  
  public static String trimFront(String path) {
    while (path.startsWith(FORWARD_SLASH))
      path = path.substring(1, path.length());
    return path;
  }
}
