/**
 * 
 */
package org.sugarj.common;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.sugarj.common.path.RelativePath;

/**
 * @author Sebastian Erdweg <seba at informatik uni-marburg de>
 */
public class StringCommands {

  public static String printListSeparated(Collection<? extends Object> l, String sep) {
    StringBuilder b = new StringBuilder();
  
    for (Iterator<? extends Object> it = l.iterator(); it.hasNext();) {
      b.append(it.next());
      if (it.hasNext())
        b.append(sep);
    }
  
    return b.toString();
  }
  
  public static String printListSeparated(Object[] l, String sep) {
    StringBuilder b = new StringBuilder();
  
    for (int i = 0; i < l.length; i++) {
      b.append(l[i]);
      if (i+1 < l.length)
        b.append(sep);
    }
  
    return b.toString();
  }

  public static String makeTransformationPathString(RelativePath path) {
    return FileCommands.dropExtension(path.getRelativePath()).replace('/', '_');
  }
  
  public static String makeTransformationPathString(List<RelativePath> paths) {
    List<String> transformationPathStrings = new LinkedList<String>();
    for (RelativePath p : paths)
      transformationPathStrings.add(makeTransformationPathString(p));
    return StringCommands.printListSeparated(transformationPathStrings, "$");
  }
}
