package org.derive4j.example;

import fj.F;
import fj.F0;
import fj.Unit;
import fj.control.Trampoline;
import fj.data.Either;
import org.derive4j.*;
import org.derive4j.hkt.__;

@Data(flavour = Flavour.FJ, value = @Derive(inClass = "_Task"
    , withVisibility = Visibility.Smart
    , make = { Make.constructors, Make.casesMatching, Make.caseOfMatching }))
public abstract class Task<A> implements __<Task.µ, A> {
    public enum µ {}

    interface Cases<R, A> {
        R Now(Either<Exception, A> a);

        R Async(F<F<Either<Exception, A>, Trampoline<Unit>>, Unit> onFinish);

        R Suspend(F0<Task<A>> thunk);

        R BindSuspend(BS<?, A> bs);

        R BindAsync(BA<?, A> ba);
    }
    abstract <R> R match(Cases<R, A> cases);

    protected record BS<A, B>(F0<Task<A>> thunk, F<Either<Exception, A>, Task<B>> cont) {}

    protected record BA<A, B>(F<F<Either<Exception, A>, Trampoline<Unit>>, Unit> onFinish, F<Either<Exception, A>, Task<B>> cont) {}
}
