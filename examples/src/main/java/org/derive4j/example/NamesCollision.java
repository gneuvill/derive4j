package org.derive4j.example;

import fj.Equal;
import fj.Show;
import fj.data.List;
import org.derive4j.Derive;
import org.derive4j.Instances;

import java.util.function.Function;

public final class NamesCollision {
    private NamesCollision() {}

    @_data
    interface WorkflowDef {
      interface Cases<R> {
          R WorkflowDef(Workflow workflow, List<Step.Id> steps, List<Action.Id> actions);
      }
      <R> R match(Cases<R> cases);
    }

    @_data
    interface Workflow {
      <R> R match(Function<String, R> value);
    }

    @_data
    interface Step {
        <R> R match(Function<Id, R> id);

        @_data @Derive(inClass = "StepIdImpl")
        interface Id {
            <R> R match(Function<Integer, R> value);
        }
    }

    @_data
    interface Action {
        <R> R match(Function<Id, R> id);

        @_data @Derive(inClass = "ActionIdImpl")
        interface Id {
            <R> R match(Function<String, R> value);
        }
    }

    @data @Derive(value = @Instances({ Equal.class, Show.class }))
    private @interface _data {}
}
