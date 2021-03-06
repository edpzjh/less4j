package com.github.sommeri.less4j.core.compiler.scopes;

public class InScopeSnapshotRunner {
  private final Scope scope;

  public InScopeSnapshotRunner(Scope scope) {
    this.scope = scope;
  }

  /**
   * Convenience method. See {@link #runInLocalDataSnapshot(ITask)}
   * 
   */
  public static void runInLocalDataSnapshot(IteratedScope scope, ITask task) {
    runInLocalDataSnapshot(scope.getScope(), task);
  }
  
  /**
   * Convenience method. See {@link #runInLocalDataSnapshot(ITask)}
   * 
   */
  public static void runInLocalDataSnapshot(Scope scope, ITask task) {
    InScopeSnapshotRunner runner = new InScopeSnapshotRunner(scope);
    runner.runInLocalDataSnapshot(task);
  }

  /**
   * Create local data snapshot on the scope and runs the task. Use this method to make sure
   * that each local data snapshot is closed on proper place and regardless of thrown exceptions.
   * 
   */
  public void runInLocalDataSnapshot(ITask task) {
    scope.createLocalDataSnapshot();
    try {
      task.run();
    } finally {
      scope.discardLastLocalDataSnapshot();
    }
  }

  public interface ITask {
    public void run();
  }
}
