package org.sugarj.common.cleardep;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sugarj.common.cleardep.BuildSchedule.ScheduleMode;
import org.sugarj.common.cleardep.BuildSchedule.Task;
import org.sugarj.common.cleardep.mode.Mode;
import org.sugarj.common.path.RelativePath;

public class BuildScheduleBuilder {

  private Set<CompilationUnit> unitsToCompile;
  private Set<CompilationUnit> changedUnits;
  private ScheduleMode scheduleMode;

  public BuildScheduleBuilder(Set<CompilationUnit> unitsToCompile, ScheduleMode mode) {
    this.scheduleMode = mode;
    this.unitsToCompile = unitsToCompile;
    this.changedUnits = null;
  }

  private void findChangedUnits(Mode mode) {

    this.changedUnits = new HashSet<>();
    for (CompilationUnit unit : this.unitsToCompile) {
      this.changedUnits.addAll(CompilationUnitUtils.findUnitsWithChangedSourceFiles(unit, mode));
    }
  }

  public void updateDependencies(DependencyExtractor extractor, Mode mode) {
    // Find all dependencies which have changed
    this.findChangedUnits(mode);

    Set<CompilationUnit> visitedUnits = new HashSet<>();
    Set<CompilationUnit> units = new HashSet<>(changedUnits);
    units.addAll(this.unitsToCompile);

    // Track whether deps has been removed, then we need to repair the graph
    boolean depsRemoved = false;

    while (!units.isEmpty()) {
      CompilationUnit changedUnit = units.iterator().next();
      units.remove(changedUnit);
      Set<CompilationUnit> dependencies = extractor.extractDependencies(changedUnit);
      // Find new Compilation units and add them
      for (CompilationUnit dep : dependencies) {
        if (!changedUnit.getModuleDependencies().contains(dep) && !changedUnit.getCircularModuleDependencies().contains(dep)) {
          changedUnit.addModuleDependency(dep);
          if (!visitedUnits.contains(dep) && (!dep.isPersisted() || !dep.isConsistentShallow(null, mode))) {
            units.add(dep);
          }
        }
      }
      // Remove compilation units which are not needed anymore
      for (CompilationUnit unit : changedUnit.getCircularAndNonCircularModuleDependencies()) {
        if (!dependencies.contains(unit)) {
          depsRemoved = true;
          unit.removeModuleDependency(unit);
        }
      }
      visitedUnits.add(changedUnit);
    }
    // Removing compilation units may invalidate the circular dependencies
    // because circular dependencies may be not circular anymore
    // So we repair the graph, this may change all circular/not circular
    // dependencies
    if (depsRemoved) {
      GraphUtils.repairGraph(this.unitsToCompile);
    }
  }

  /**
   * Creates a BuildSchedule for the units in unitsToCompile. That means that
   * the BuildSchedule is sufficient to build all dependencies of the given
   * units and the units itself.
   * 
   * The scheduleMode specifies which modules are included in the BuildSchedule.
   * For REBUILD_ALL, all dependencies are included in the schedule, whether
   * they are inconsistent or net. For REBUILD_INCONSISTENT, only dependencies
   * are included in the build schedule, if they are inconsistent. For
   * REBUILD_INCONSISTENT_INTERFACE, the same tasks are included in the schedule
   * but information for the interfaces of the modules before building is stored
   * and may be used to determine modules which does not have to be build later.
   * 
   * @param unitsToCompile
   *          a set of units which has to be compiled
   * @param editedSourceFiles
   * @param mode
   * @param scheduleMode
   *          the mode of the schedule as described
   * @return the created BuildSchedule
   */
  public BuildSchedule createBuildSchedule(Map<RelativePath, Integer> editedSourceFiles, Mode mode) {
    BuildSchedule schedule = new BuildSchedule();

    // Calculate strongly connected components: O(E+V)
    List<Set<CompilationUnit>> sccs = GraphUtils.calculateStronglyConnectedComponents(this.unitsToCompile);

    // Create tasks on fill map to find tasks for units: O(V)
    Map<CompilationUnit, Task> tasksForUnit = new HashMap<>();
    List<Task> buildTasks = new ArrayList<>(sccs.size());
    for (Set<CompilationUnit> scc : sccs) {
      System.out.println(scc);
      Task t = new Task(scc);
      buildTasks.add(t);
      for (CompilationUnit u : t.getUnitsToCompile()) {
        tasksForUnit.put(u, t);
      }
    }

    // Calculate dependencies between tasks (sccs): O(E+V)
    for (Task t : buildTasks) {
      for (CompilationUnit u : t.getUnitsToCompile()) {
        for (CompilationUnit dep : u.getModuleDependencies()) {
          Task depTask = tasksForUnit.get(dep);
          if (depTask != t) {
            t.addRequiredTask(depTask);
          }
        }
      }
    }

    // Prefilter tasks from which we know that they are consistent: O (V+E)
    // Why not filter consistent units before calculating the build
    // schedule:
    // This required calculating strongly connected components and a
    // topological order of them
    // which we also need for calculating the build schedule
    if (this.scheduleMode == ScheduleMode.REBUILD_INCONSISTENT) {
      this.findChangedUnits(mode);
      // All tasks which changed units are inconsistent
      Set<Task> inconsistentTasks = new HashSet<>();
      for (CompilationUnit u : this.changedUnits) {
        inconsistentTasks.add(tasksForUnit.get(u));
      }
      // All transitivly reachable to
      // Make use of the reverse topological order we have already
      Iterator<Task> buildTaskIter = buildTasks.iterator();
      while (buildTaskIter.hasNext()) {
        Task task = buildTaskIter.next();
        boolean taskConsistent = true;
        if (inconsistentTasks.contains(task)) {
          taskConsistent = false;
        } else {
          for (Task reqTask : task.requiredTasks) {
            if (inconsistentTasks.contains(reqTask)) {
              inconsistentTasks.add(task);
              taskConsistent = false;
              break;
            }
          }
        }
        if (taskConsistent) {
          // We may remove this task
          buildTaskIter.remove();
          task.remove();
        }
      }
    }

    // Find all leaf tasks in all tasks (tasks which does not require other
    // tasks): O(V)
    for (Task possibleRoot : buildTasks) {
      if (possibleRoot.hasNoRequiredTasks()) {
        schedule.addRootTask(possibleRoot);
      }
    }
    schedule.setOrderedTasks(buildTasks);

    // At the end, we validate the graph we build
    assert validateBuildSchedule(buildTasks);
    assert validateFlattenSchedule(buildTasks);

    return schedule;

  }

  private Set<CompilationUnit> calculateReachableUnits(Task task) {
    Set<CompilationUnit> reachableUnits = new HashSet<>();
    Deque<Task> taskStack = new LinkedList<>();
    Set<Task> seenTasks = new HashSet<>();
    taskStack.addAll(task.requiredTasks);
    reachableUnits.addAll(task.unitsToCompile);
    Map<Task, Task> preds = new HashMap<>();
    for (Task r : task.requiredTasks) {
      preds.put(r, task);
    }
    while (!taskStack.isEmpty()) {
      Task t = taskStack.pop();
      if (t == task) {
        Task tmp = preds.get(t);
        List<Task> path = new LinkedList<>();
        path.add(t);
        while (tmp != task) {
          path.add(tmp);
          tmp = preds.get(tmp);
        }
        path.add(task);
        throw new AssertionError("Graph contains a cycle with " + path);

      }
      seenTasks.add(t);
      reachableUnits.addAll(t.unitsToCompile);
      for (Task r : t.requiredTasks)
        if (!seenTasks.contains(r)) {
          taskStack.push(r);
          preds.put(r, t);
        }
    }
    return reachableUnits;
  }

  private boolean validateDependenciesOfTask(Task task, Set<CompilationUnit> singleUnits) {
    Set<CompilationUnit> reachableUnits = this.calculateReachableUnits(task);
    for (CompilationUnit unit : singleUnits != null ? singleUnits : task.unitsToCompile) {
      if (!validateDeps("BuildSchedule", unit, reachableUnits)) {
        return false;
      }
    }

    return true;
  }

  private boolean validateBuildSchedule(Iterable<Task> allTasks) {
    for (Task task : allTasks) {
      if (!validateDependenciesOfTask(task, null)) {
        return false;
      }
    }
    return true;
  }

  boolean validateDeps(String prefix, CompilationUnit unit, Set<CompilationUnit> allDeps) {
    for (CompilationUnit dep : unit.getCircularAndNonCircularModuleDependencies()) {
      if (needsToBeBuild(dep) && !allDeps.contains(dep)) {
        if (prefix != null)
          System.err.println(prefix + ": Schedule violates dependency: " + unit + " on " + dep);
        return false;
      }
    }
    return true;
  }

  boolean needsToBeBuild(CompilationUnit unit) {
    // Calling isConsistent here is really really slow but safe and its a check
    boolean build = scheduleMode == BuildSchedule.ScheduleMode.REBUILD_ALL || !unit.isConsistent(null, null);
    return build;
  }

  public static boolean validateDepGraphCycleFree(Set<CompilationUnit> startUnits) {
    Set<CompilationUnit> unmarkedUnits = new HashSet<>();
    Set<CompilationUnit> tempMarkedUnits = new HashSet<>();
    for (CompilationUnit unit : startUnits) {
      unmarkedUnits.addAll(CompilationUnitUtils.findAllUnits(unit));
    }

    while (!unmarkedUnits.isEmpty()) {
      CompilationUnit unit = unmarkedUnits.iterator().next();
      if (!visit(unit, unmarkedUnits, tempMarkedUnits)) {
        return false;
      }
    }
    return true;

  }

  private static boolean visit(CompilationUnit u, Set<CompilationUnit> unmarkedUnits, Set<CompilationUnit> tempMarkedUnits) {
    if (tempMarkedUnits.contains(u)) {
      return false; // Found a cycle
    }
    if (unmarkedUnits.contains(u)) {
      tempMarkedUnits.add(u);
      for (CompilationUnit dep : u.getModuleDependencies()) {
        if (!visit(dep, unmarkedUnits, tempMarkedUnits)) {
          return false;
        }
      }
      unmarkedUnits.remove(u);
      tempMarkedUnits.remove(u);
    }
    return true;
  }

  public static boolean validateCircDepsAreCircDeps(Set<CompilationUnit> startUnits) {
    Set<CompilationUnit> allUnits = new HashSet<>();
    for (CompilationUnit unit : startUnits) {
      allUnits.addAll(CompilationUnitUtils.findAllUnits(unit));
    }

    for (CompilationUnit unit : allUnits) {
      for (CompilationUnit dep : unit.getCircularModuleDependencies()) {
        if (!dep.dependsOnTransitivelyNoncircularly(unit)) {
          return false; // Unit would not be a circle
        }
      }
    }

    return true;
  }

  private boolean validateFlattenSchedule(List<Task> flatSchedule) {
    Set<CompilationUnit> collectedUnits = new HashSet<>();
    for (int i = 0; i < flatSchedule.size(); i++) {
      Task currentTask = flatSchedule.get(i);
      // Find duplicates
      for (CompilationUnit unit : currentTask.unitsToCompile) {
        if (collectedUnits.contains(unit)) {
          throw new AssertionError("Task contained twice: " + unit);
        }
      }
      collectedUnits.addAll(currentTask.unitsToCompile);

      for (CompilationUnit unit : currentTask.unitsToCompile) {
        validateDeps("Flattened Schedule", unit, collectedUnits);

      }
    }
    return true;
  }

}
