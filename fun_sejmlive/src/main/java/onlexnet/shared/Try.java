package onlexnet.shared;

/**
 * {@link Try} supports two types of lambda:
 * <ul>
 * <li>{@link Try.Supplier} which is an extension of {@link java.util.function.Supplier} allowing to throw any exception</li>
 * <li>{@link Try.Runnable} which is an extension of {@link java.lang.Runnable} allowing to throw any exception</li>
 * </ul>
 * @author slawomir@siudek.net
 * 
 * Examples of how to use Try:
 * <pre>
 * Example: handle result from a supplier in normal flow
 * var actual = switch(Try.of(() -> 1)) {
 *                case Try.Value<Integer>(var value) -> value;
 *                case Try.Failure(var ex) -> 0;
 *              };
 * result: actual == 1
 * 
 * Example: handle result from a supplier when some exception will happen
 *     var actual = switch(Try.of(() -> { throw new IllegalArgumentException(); })) {
 *                    case Try.Value(var value) -> value;
 *                    case Try.Failure(Exception ex) -> 2;
 *                  };
 * result: actual == 2
 * 
 * Example: handle result from a supplier for a particular exception type
 *     var actual = switch(Try.of(() -> { throw new IllegalArgumentException(); })) {
 *                    case Try.Value(var value) -> value;
 *                    case Try.Failure(IllegalArgumentException ex) -> 2;
 *                    case Try.Failure(Exception ex) -> 3;
 * result: actual == 2
 * 
 * Example: Guard: Handle exception early and exit method
 *   void myMethod() {
 *     switch(Try.of(() -> { doSomeValidationWhichCanThrowAnException(); })) {
 *       case Try.Success s -> { } // no-op handler as we can continue myMethod
 *       case Try.Failure(var ex) -> { return; } // imediatelly exist from myMethod because of exception 'ex'
 *     }
 * 
 *     // continuation of myMethod ...
 *   }
 *
 * </pre>
 */
sealed interface Try<T> permits Try.TryVoid, Try.TryValue {

  /** Try.run() takes a Try.Runnable, which has a void run() method to "perform side-effects". */
  static TryVoid run(Runnable runnable) {
    try {
      runnable.run();
      return Success.INSTANCE;
    } catch (Exception ex) {
      return new Failure(ex);
    }
  }

  /** Try.of() takes a Try.Supplier, which has a get() method to "get a result". */
  @SuppressWarnings("unchecked")
  static <T> TryValue<T> of(Supplier<T> supplier) {
    try {
      var value = supplier.get();
      return new Value<T>(value);
    } catch (Exception ex) {
      return new Failure(ex);
    }
  }

  @SuppressWarnings("rawtypes")
  sealed interface TryVoid extends Try permits Success, Failure {
  }

  sealed interface TryValue<T> extends Try<T> permits Value, Failure {
  }


  @SuppressWarnings("rawtypes")
  record Failure(Exception ex) implements TryVoid, TryValue {
  }

  record Value<T>(T value) implements TryValue<T> {
  }

  /**
   * {@link Success} can be constructed as a record, but we prefer to use singletone as there is no content,
   * so that Enum instance is used.
   */
  enum Success implements TryVoid {
    INSTANCE
  }

  /** Equivalent of {@link java.lang.Runnable} + ability to throw checked exceptions. */
  @FunctionalInterface
  public interface Runnable {
    void run() throws Exception;
  }

  /** Equivalent of {@link java.util.function.Supplier} + ability to throw checked exceptions. */
  @FunctionalInterface
  public interface Supplier<T> {
    T get() throws Exception;
  }

}